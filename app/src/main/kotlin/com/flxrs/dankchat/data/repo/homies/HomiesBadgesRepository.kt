package com.flxrs.dankchat.data.repo.homies

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.di.DispatchersProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

private val logger = KotlinLogging.logger("HomiesBadgesRepository")

/**
 * Community "homies" badges, the same three remote lists Chatterino's fork uses.
 *
 * They are community run and change without notice, so the payload is parsed tolerantly: one entry per user
 * (chatterinohomies) or one entry per badge listing its owners (itzalex), wrapped in a `badges` object or served
 * as a bare array. A list that fails or changes shape is skipped - losing a decorative badge must never affect
 * chat.
 */
@Single
class HomiesBadgesRepository(
    private val httpClient: HttpClient,
    private val json: Json,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val _badges = MutableStateFlow<Map<UserId, List<HomiesBadge>>>(emptyMap())
    val badges: StateFlow<Map<UserId, List<HomiesBadge>>> = _badges.asStateFlow()

    private var loaded = false

    /** Fetches all lists once per app run; later calls are a no-op unless [force] is set. */
    suspend fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true

        val byUser = HashMap<UserId, MutableList<HomiesBadge>>()
        LISTS.forEach { url ->
            val entries = fetch(url) ?: return@forEach
            entries.forEach { (userId, badge) ->
                byUser.getOrPut(userId) { mutableListOf() }.add(badge)
            }
        }

        _badges.value = byUser.mapValues { (_, list) -> list.take(MAX_BADGES_PER_USER) }
    }

    fun clear() {
        loaded = false
        _badges.value = emptyMap()
    }

    private suspend fun fetch(url: String): List<Pair<UserId, HomiesBadge>>? = withContext(dispatchersProvider.io) {
        val body =
            runCatching { httpClient.get(url).bodyAsText() }
                .onFailure { error -> logger.debug(error) { "homies list $url failed" } }
                .getOrNull()
                ?: return@withContext null

        runCatching { parse(body) }
            .onFailure { error -> logger.debug(error) { "homies list $url did not parse" } }
            .getOrNull()
    }

    private fun parse(body: String): List<Pair<UserId, HomiesBadge>> {
        val root = json.parseToJsonElement(body)
        val array =
            when (root) {
                is JsonArray -> root
                is JsonObject -> root["badges"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }

        return array.flatMap { element ->
            val entry = element.jsonObject
            val tooltip = entry.stringOrNull("tooltip").orEmpty()
            val image = entry.stringOrNull("image1") ?: return@flatMap emptyList()
            val owners =
                when {
                    // chatterinohomies: one entry per user
                    entry.stringOrNull("userId") != null -> listOf(entry.stringOrNull("userId")!!)
                    // itzalex: one entry per badge listing its owners; empty ids are placeholders
                    else -> entry["users"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
                }

            owners
                .filter { it.isNotBlank() }
                .map { userId ->
                    UserId(userId) to HomiesBadge(userId = userId, tooltip = tooltip, imageUrl = image)
                }
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val MAX_BADGES_PER_USER = 3

        val LISTS =
            listOf(
                "https://chatterinohomies.com/api/badges/list",
                "https://itzalex.github.io/badges",
                "https://itzalex.github.io/badges2",
            )
    }
}
