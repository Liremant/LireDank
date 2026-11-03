package com.flxrs.dankchat.data.repo.emote

import androidx.collection.LruCache
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.seventv.SevenTVApiClient
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVColor
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVCosmeticsResponse
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaint
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintLayerType
import com.flxrs.dankchat.di.DispatchersProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

private val logger = KotlinLogging.logger("SevenTVCosmeticsRepository")

/**
 * Resolves 7TV paints and badges per Twitch user id.
 *
 * Cosmetics are only exposed over GraphQL, one request per user. A busy channel has hundreds of distinct
 * senders, so results (including misses) are memoised and concurrent lookups for the same user are coalesced.
 * Rendering never waits on the network: callers read [getCached] and [prefetch] fills the cache in the
 * background, falling back to the plain IRC colour until the paint arrives.
 */
@Single
class SevenTVCosmeticsRepository(
    private val sevenTVApiClient: SevenTVApiClient,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val cache = LruCache<UserId, CacheEntry>(CACHE_SIZE)

    // Bumped for every resolved user so a chat can re-map once paints are known
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private val inFlight = mutableMapOf<UserId, CompletableDeferred<SevenTVCosmetics?>>()

    fun getCached(userId: UserId): SevenTVCosmetics? = synchronized(cache) { cache[userId]?.cosmetics }

    /** Starts a lookup unless this user is already resolved or being fetched. Returns immediately. */
    fun prefetch(
        scope: CoroutineScope,
        userId: UserId,
    ) {
        val job =
            synchronized(cache) {
                if (cache[userId] != null || inFlight.containsKey(userId)) return
                CompletableDeferred<SevenTVCosmetics?>().also { inFlight[userId] = it }
            }

        scope.launch {
            val result =
                try {
                    withContext(dispatchersProvider.io) {
                        sevenTVApiClient.getSevenTVCosmetics(userId.value).getOrNull()?.toCosmetics()
                    }
                } catch (e: Exception) {
                    logger.debug(e) { "7TV cosmetics lookup failed for $userId" }
                    null
                }

            logger.info {
                "7TV cosmetics $userId -> paint=${result?.paint?.let { describe(it) } ?: "none"} badge=${result?.badgeUrl != null}"
            }
            synchronized(cache) {
                cache.put(userId, CacheEntry(result))
                inFlight.remove(userId)
                _version.value++
            }
            job.complete(result)
        }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
        _version.value++
    }

    private fun SevenTVCosmeticsResponse.toCosmetics(): SevenTVCosmetics? {
        val style = data?.users?.userByConnection?.style ?: return null
        val paint = style.activePaint?.toPaintUi()
        val badgeUrl = style.activeBadge
            ?.images
            ?.lastOrNull()
            ?.url
        return if (paint == null && badgeUrl == null) {
            null
        } else {
            SevenTVCosmetics(paint = paint, badgeUrl = badgeUrl, badgeTitle = style.activeBadge?.name)
        }
    }

    private fun SevenTVPaint.toPaintUi(): SevenTVPaintUi? {
        val layer = data?.layers?.firstNotNullOfOrNull { it.ty } ?: return null
        val shadows = data.shadows.mapNotNull { shadow ->
            shadow.color.toColorInt()?.let { SevenTVPaintShadowUi(it, shadow.offsetX, shadow.offsetY, shadow.blur) }
        }

        // Mirrors Chatterino: the shape is chosen by the layer kind, colours are authored by 7TV
        return when {
            layer.kind == null || layer.kind.contains("LinearGradient") -> {
                val stops = layer.toStops()
                if (stops.isEmpty()) null else SevenTVPaintUi.Linear(stops, layer.angle ?: 0f, layer.repeating, shadows)
            }

            layer.kind.contains("RadialGradient") -> {
                val stops = layer.toStops()
                if (stops.isEmpty()) null else SevenTVPaintUi.Radial(stops, layer.repeating, shadows)
            }

            layer.kind.contains("SingleColor") -> layer.color?.toColorInt()?.let { SevenTVPaintUi.Solid(it, shadows) }

            layer.kind.contains("Image") -> layer.imageUrl?.let { SevenTVPaintUi.Image(it, shadows) }

            else -> null
        }
    }

    private fun describe(paint: SevenTVPaintUi): String = when (paint) {
        is SevenTVPaintUi.Linear -> "linear angle=${paint.angle} repeat=${paint.repeating} stops=${paint.stops.map { "${it.at}:${Integer.toHexString(it.color)}" }}"
        is SevenTVPaintUi.Radial -> "radial repeat=${paint.repeating} stops=${paint.stops.map { "${it.at}:${Integer.toHexString(it.color)}" }}"
        is SevenTVPaintUi.Solid -> "solid ${Integer.toHexString(paint.color)}"
        is SevenTVPaintUi.Image -> "image ${paint.url}"
    }

    private fun SevenTVPaintLayerType.toStops(): List<SevenTVPaintStopUi> = stops.mapNotNull { stop -> stop.color.toColorInt()?.let { SevenTVPaintStopUi(stop.at, it) } }.sortedBy { it.at }

    private fun SevenTVColor.toColorInt(): Int? {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255 || a !in 0..255) return null
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        const val CACHE_SIZE = 500
    }

    /** LruCache cannot hold null values, and a miss must be remembered too. */
    private class CacheEntry(
        val cosmetics: SevenTVCosmetics?,
    )
}

/** Resolved 7TV cosmetics for one user; both parts are optional. */
data class SevenTVCosmetics(
    val paint: SevenTVPaintUi?,
    val badgeUrl: String?,
    val badgeTitle: String?,
)
