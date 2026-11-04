package com.flxrs.dankchat.ui.chat.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware

/**
 * Loads the textures of one painted nickname, keeping one bitmap of each current frame.
 *
 * Animated paints ship a still and an animated file side by side on the CDN, and the animated one decodes into an
 * [Animatable] drawable. Android only advances such a drawable while something draws it, so a single frame loop
 * redraws every animated texture into a reused bitmap at ~30fps.
 *
 * All urls are resolved in one place on purpose: a composable inside the caller's per-layer loop would be called a
 * varying number of times and lose its state.
 */
@Composable
internal fun rememberPaintTextures(
    urls: List<String>,
    widthPx: Int,
    heightPx: Int,
): Map<String, ImageBitmap> {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val frames = remember { mutableStateMapOf<String, ImageBitmap>() }
    val animated = remember { mutableMapOf<String, Drawable>() }

    val fallback = with(density) { DEFAULT_TEXTURE_PX.dp.toPx().toInt() }
    val width = if (widthPx > 0) widthPx else fallback
    val height = if (heightPx > 0) heightPx else fallback

    LaunchedEffect(urls, width, height) {
        if (urls.isEmpty()) return@LaunchedEffect

        urls.forEach { url ->
            val drawable =
                runCatching {
                    val request =
                        ImageRequest.Builder(context)
                            .data(url)
                            // Frames are drawn into a software canvas, a hardware bitmap cannot be read back
                            .allowHardware(false)
                            .build()
                    (context.imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(context.resources)
                }.getOrNull() ?: return@forEach

            drawable.toFrame(width, height)?.let { frames[url] = it }
            (drawable as? Animatable)?.let { animatable ->
                animated[url] = drawable
                animatable.start()
            }
        }

        if (animated.isEmpty()) return@LaunchedEffect

        var lastDrawnAt = 0L
        while (true) {
            withFrameMillis { frameTimeMillis ->
                if (frameTimeMillis - lastDrawnAt >= FRAME_INTERVAL_MS) {
                    lastDrawnAt = frameTimeMillis
                    animated.forEach { (url, drawable) ->
                        drawable.toFrame(width, height)?.let { frames[url] = it }
                    }
                }
            }
        }
    }

    return frames
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
