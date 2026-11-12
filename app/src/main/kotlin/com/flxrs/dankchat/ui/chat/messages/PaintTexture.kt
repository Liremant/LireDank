package com.flxrs.dankchat.ui.chat.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.flxrs.dankchat.ui.chat.emote.isAnimated
import com.flxrs.dankchat.ui.chat.emote.setAnimationsRunning

/**
 * Loads the textures of one painted nickname, keeping one bitmap of each current frame.
 *
 * Animated paints ship a still and an animated file side by side on the CDN. An [Animatable], possibly nested in
 * a LayerDrawable, needs a running frame loop to advance. Frames keep their native size and the paint shader
 * stretches them, so measuring text cannot restart the animation.
 *
 * All urls are resolved in one place on purpose: a composable inside the caller's per-layer loop would be called a
 * varying number of times and lose its state.
 */
@Composable
internal fun rememberPaintTextures(urls: List<String>): PaintTextures {
    val context = LocalPlatformContext.current
    // One stable holder per row: a fresh instance every call would invalidate the painted-name
    // bitmap cache on every recomposition
    val holder = remember(urls) { PaintTextures() }
    val animated = remember(urls) { mutableMapOf<String, Drawable>() }
    val drawables = remember(urls) { mutableMapOf<String, Drawable>() }

    LaunchedEffect(urls) {
        holder.frames.keys.retainAll(urls.toSet())
        try {
            urls.forEach { url ->
                val drawable =
                    runCatching {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(url)
                                // Frames are drawn into a software canvas, a hardware bitmap cannot be read back
                                .allowHardware(false)
                                .build()
                        (context.imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(context.resources)
                    }.getOrNull() ?: return@forEach

                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_TEXTURE_PX
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_TEXTURE_PX
                drawables[url] = drawable
                drawable.toFrame(holder, url, width, height)?.let { holder.frames[url] = it }
                if (drawable.isAnimated) {
                    animated[url] = drawable
                    drawable.setAnimationsRunning(true)
                }
            }

            var lastDrawnAt = 0L
            while (animated.isNotEmpty()) {
                withFrameMillis { frameTimeMillis ->
                    if (frameTimeMillis - lastDrawnAt >= FRAME_INTERVAL_MS) {
                        lastDrawnAt = frameTimeMillis
                        animated.forEach { (url, drawable) ->
                            drawable.toFrame(holder, url, drawable.intrinsicWidth, drawable.intrinsicHeight)?.let { holder.frames[url] = it }
                        }
                    }
                }
            }
        } finally {
            // Runs on every effect exit: key change, leave-composition cancellation, or load failure
            drawables.values.forEach { it.setAnimationsRunning(false) }
            animated.clear()
            drawables.clear()
        }
    }

    return holder
}

/**
 * Stable per-row holder of one painted nickname's texture frames. The same instance is returned
 * across recompositions so caches keyed on it survive; its [frames] state map invalidates caches when a
 * frame bitmap is (re)drawn.
 */
internal class PaintTextures {
    val frames = mutableStateMapOf<String, ImageBitmap>()

    /** Bitmaps reused across frames of one url, so animated paints stop allocating per tick. */
    private val reusableBitmaps = mutableMapOf<String, Bitmap>()

    fun reusableBitmap(url: String): Bitmap? = reusableBitmaps[url]

    fun storeReusableBitmap(url: String, bitmap: Bitmap) {
        reusableBitmaps[url] = bitmap
    }

    operator fun get(url: String): ImageBitmap? = frames[url]
}

/** Renders the drawable's current frame, reusing [holder]'s bitmap for [url] when it fits. */
private fun Drawable.toFrame(
    holder: PaintTextures,
    url: String,
    widthPx: Int,
    heightPx: Int,
): ImageBitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    return runCatching {
        val reuse = holder.reusableBitmap(url)
        val bitmap =
            if (reuse != null && reuse.width == widthPx && reuse.height == heightPx && reuse.isMutable) {
                reuse.eraseColor(android.graphics.Color.TRANSPARENT)
                reuse
            } else {
                Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                    holder.storeReusableBitmap(url, it)
                }
            }
        setBounds(0, 0, widthPx, heightPx)
        draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()
}

private const val FRAME_INTERVAL_MS = 33L
private const val DEFAULT_TEXTURE_PX = 32
