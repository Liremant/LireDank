package com.flxrs.dankchat.ui.chat.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Loads a 7TV paint texture and exposes one bitmap of its current frame.
 *
 * Animated paints ship both a still and an animated file on the CDN, and the animated one decodes into an
 * [Animatable] drawable. Android only advances such a drawable while something keeps drawing it, so a frame loop
 * redraws it into a single reused bitmap and bumps a version counter. The caller recomposes and rebuilds its
 * shader from that same bitmap, so no bitmap is allocated per frame.
 */
@Composable
internal fun rememberPaintTexture(
    url: String,
    widthPx: Int,
    heightPx: Int,
): ImageBitmap? {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val holder = remember(url) { TextureHolder() }
    var version by remember(url) { mutableIntStateOf(0) }
    // Match the name box, so the texture is not distorted before the shader stretches it
    val fallback = with(density) { DEFAULT_TEXTURE_PX.dp.toPx().toInt() }
    val width = if (widthPx > 0) widthPx else fallback
    val height = if (heightPx > 0) heightPx else fallback

    LaunchedEffect(url, width, height) {
        val drawable =
            runCatching {
                val request =
                    ImageRequest.Builder(context)
                        .data(url)
                        // Frames are drawn into a software canvas, a hardware bitmap cannot be read back
                        .allowHardware(false)
                        .build()
                (context.imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(context.resources)
            }.getOrNull() ?: return@LaunchedEffect

        holder.attach(drawable, width, height)
        holder.render()
        version++

        val animatable = drawable as? Animatable ?: return@LaunchedEffect
        animatable.start()
        var lastDrawnAt = 0L
        while (true) {
            withFrameMillis { frameTimeMillis ->
                if (frameTimeMillis - lastDrawnAt >= FRAME_INTERVAL_MS) {
                    lastDrawnAt = frameTimeMillis
                    holder.render()
                    version++
                    if (version % 30 == 0) android.util.Log.i("PaintTexture", "render v$version $url")
                }
            }
        }
    }

    // Reading the version subscribes this composable to frame updates
    return if (version >= 0) holder.image else null
}

/** One bitmap per visible painted name, redrawn in place from the texture drawable. */
private class TextureHolder {
    var image: ImageBitmap? = null
        private set

    private var drawable: Drawable? = null
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    fun attach(
        source: Drawable,
        widthPx: Int,
        heightPx: Int,
    ) {
        drawable = source
        val current = bitmap
        if (current != null && current.width == widthPx && current.height == heightPx) return
        val created = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap = created
        canvas = Canvas(created)
        image = created.asImageBitmap()
    }

    fun render() {
        val drawable = drawable ?: return
        val bitmap = bitmap ?: return
        val canvas = canvas ?: return
        bitmap.eraseColor(0)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(canvas)
    }
}

private const val FRAME_INTERVAL_MS = 33L
private const val DEFAULT_TEXTURE_PX = 32
