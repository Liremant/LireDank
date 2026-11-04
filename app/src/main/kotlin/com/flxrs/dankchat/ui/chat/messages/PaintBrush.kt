package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.TileMode
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Turns one paint layer into a real shader over the nickname box, mirroring the 7TV website.
 *
 * The site renders each layer as a CSS background clipped to the text: `linear-gradient(<angle>deg, stops)`,
 * `radial-gradient(<shape>, stops)` and `url(<image>)` with `background-size: 100% 100%`. This is the same idea
 * as a Compose brush sized to the name, so the paint belongs to the name and cannot reach the timestamp or text.
 */
internal fun SevenTVPaintShape.toBrush(
    rect: Rect,
    userColor: Color,
    texture: ImageBitmap? = null,
): Brush? = when (this) {
    is SevenTVPaintShape.Linear -> {
        val (start, end) = gradientLine(rect.size, angle)
        android.util.Log.i("Paint", "linear rect=$rect start=$start end=$end stops=${stops.size}")
        Brush.linearGradient(
            colorStops = stops.toColorStops(userColor),
            start = start + rect.topLeft,
            end = end + rect.topLeft,
            tileMode = if (repeating) TileMode.Repeated else TileMode.Clamp,
        )
    }

    is SevenTVPaintShape.Radial -> {
        // The site passes only a shape, so CSS defaults apply: centred, sized to the farthest corner
        val radius = hypot(rect.width, rect.height) / 2f
        Brush.radialGradient(
            colorStops = stops.toColorStops(userColor),
            center = rect.center,
            radius = if (radius > 0f) radius else 1f,
            tileMode = if (repeating) TileMode.Repeated else TileMode.Clamp,
        )
    }

    is SevenTVPaintShape.Solid -> Brush.linearGradient(listOf(Color(color), Color(color)))

    // One copy stretched over the whole name, matching `background-size: 100% 100%`.
    // A ShaderBrush caches the shader it built for a size, so the brush must be a new instance per texture frame
    is SevenTVPaintShape.Image -> texture?.let { bitmap -> textureBrush(bitmap, rect) }
}

/**
 * Endpoints of the gradient line across [size] at [angle] degrees.
 *
 * 7TV authors CSS angles: 0 points up and 90 points right, so a 90 degree paint sweeps across the name while a
 * 0 degree paint shades the glyphs bottom to top. The line is the box projected onto that direction.
 */
internal fun gradientLine(
    size: Size,
    angle: Float,
): Pair<Offset, Offset> {
    val radians = Math.toRadians(angle.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val half = length / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Offset(center.x - dx * half, center.y - dy * half) to Offset(center.x + dx * half, center.y + dy * half)
}

/**
 * Stops keep their authored positions, which is what puts the colour transition where the author put it.
 *
 * Paints in the wild do contain stops at the same position, where two colours meet with a hard edge. Compose
 * does not define what a shader does with equal positions and renders such paints unpredictably, so a repeated
 * position is nudged forward by a hair - the same guard Chatterino applies while parsing stops.
 */
private fun List<SevenTVPaintStopUi>.toColorStops(userColor: Color): Array<Pair<Float, Color>> {
    val sorted = sortedBy { it.at }
    if (sorted.isEmpty()) return arrayOf(0f to userColor, 1f to userColor)

    var previous = -1f
    return sorted
        .map { stop ->
            val position = if (stop.at > previous) stop.at else previous + STOP_EPSILON
            previous = position
            position.coerceIn(0f, 1f) to overlayOver(userColor, Color(stop.color))
        }.toTypedArray()
}

/** Semi-transparent stops are laid over the user's own colour, so a paint never makes a name unreadable. */
private fun overlayOver(
    background: Color,
    foreground: Color,
): Color {
    val alpha = foreground.alpha
    if (alpha >= 1f) return foreground
    return Color(
        red = (1f - alpha) * background.red + alpha * foreground.red,
        green = (1f - alpha) * background.green + alpha * foreground.green,
        blue = (1f - alpha) * background.blue + alpha * foreground.blue,
        alpha = 1f,
    )
}

private fun textureBrush(
    bitmap: ImageBitmap,
    rect: Rect,
): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            // A shader maps its bitmap one pixel per pixel, so without a scale the texture would only cover the
            // top-left corner of the name. The local matrix stretches it over the name box, which is what
            // `background-size: 100% 100%` does on the website.
            val androidBitmap = bitmap.asAndroidBitmap()
            val shader =
                android.graphics.BitmapShader(
                    androidBitmap,
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP,
                )
            val scaleX = if (androidBitmap.width > 0) rect.width / androidBitmap.width else 1f
            val scaleY = if (androidBitmap.height > 0) rect.height / androidBitmap.height else 1f
            shader.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(scaleX, scaleY)
                    postTranslate(rect.left, rect.top)
                },
            )
            return shader
        }
    }

private const val STOP_EPSILON = 0.0001f
