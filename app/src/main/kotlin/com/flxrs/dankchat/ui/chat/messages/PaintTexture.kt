package com.flxrs.dankchat.ui.chat.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
    val frames = remember { mutableStateMapOf<String, ImageBitmap>() }
    val animated = remember { mutableMapOf<String, Drawable>() }
    val drawables = remember { mutableMapOf<String, Drawable>() }
    val animatedUrls = remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(urls) {
        frames.keys.retainAll(urls.toSet())
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
                drawable.toFrame(width, height)?.let { frames[url] = it }
                if (drawable.isAnimated) {
                    animated[url] = drawable
                    drawable.setAnimationsRunning(true)
                }
            }
            animatedUrls.value = animated.keys.toSet()

            var lastDrawnAt = 0L
            while (animated.isNotEmpty()) {
                withFrameMillis { frameTimeMillis ->
                    if (frameTimeMillis - lastDrawnAt >= FRAME_INTERVAL_MS) {
                        lastDrawnAt = frameTimeMillis
                        animated.forEach { (url, drawable) ->
                            drawable.toFrame(drawable.intrinsicWidth, drawable.intrinsicHeight)?.let { frames[url] = it }
                        }
                    }
                }
            }
        } finally {
            drawables.values.forEach { it.setAnimationsRunning(false) }
            animated.clear()
            drawables.clear()
            animatedUrls.value = emptySet()
        }
    }

    return PaintTextures(frames, animatedUrls.value)
}

/** Current frame bitmaps of one painted nickname's textures, plus which of them animate. */
internal class PaintTextures(
    val frames: Map<String, ImageBitmap>,
    val animatedUrls: Set<String>,
) {
    operator fun get(url: String): ImageBitmap? = frames[url]
}

/** Renders the drawable's current frame at the requested size. */
private fun Drawable.toFrame(
    widthPx: Int,
    heightPx: Int,
): ImageBitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    return runCatching {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, widthPx, heightPx)
        draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()
}

private const val FRAME_INTERVAL_MS = 33L
private const val DEFAULT_TEXTURE_PX = 32
