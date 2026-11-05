package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintRadialShapeUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds one paint layer's brush for a box of [size], in that box's own coordinates.
 *
 * The website renders a paint as a CSS background clipped to the text, so the brush only has to describe the
 * fill over the name box - the glyphs are the mask. The caller draws both in the same translated coordinate
 * space; saveLayer alone does not rebase a shader.
 */
internal fun SevenTVPaintShape.toBrush(
    size: Size,
    userColor: Color,
    texture: ImageBitmap? = null,
): Brush? = when (this) {
    is SevenTVPaintShape.Linear -> {
        val (lineStart, lineEnd) = gradientLine(size, angle)
        when (val span = stops.repeatSpan(repeating)) {
            // A collapsed repeating period paints the box with the last stop color
            is RepeatSpan.Degenerate -> {
                // The period repeats nothing, so the last authored stop covers the box
                val last = stops.last().color
                Brush.linearGradient(listOf(Color(last), Color(last)))
            }

            is RepeatSpan.Normalized ->
                Brush.linearGradient(
                    colorStops = stops.toColorStops(userColor, span),
                    start = lineStart.lerp(lineEnd, span.first),
                    end = lineStart.lerp(lineEnd, span.last),
                    tileMode = TileMode.Repeated,
                )

            null ->
                Brush.linearGradient(
                    colorStops = stops.toColorStops(userColor),
                    start = lineStart,
                    end = lineEnd,
                    tileMode = if (repeating) TileMode.Repeated else TileMode.Clamp,
                )
        }
    }

    is SevenTVPaintShape.Radial -> {
        val span = stops.repeatSpan(repeating)
        val normalized = span as? RepeatSpan.Normalized
        val radius = if (shape == SevenTVPaintRadialShapeUi.CIRCLE) {
            hypot(size.width, size.height) / 2f
        } else {
            // Compose's radial brush is circular; scale it into the CSS ellipse below.
            size.width / SQRT_TWO
        }
        val ellipse = shape == SevenTVPaintRadialShapeUi.ELLIPSE
        val brush = Brush.radialGradient(
            colorStops = stops.toColorStops(userColor, normalized),
            center = Offset(size.width / 2f, if (ellipse) size.width / 2f else size.height / 2f),
            radius = (radius * (normalized?.last ?: 1f)).let { if (it > 0f) it else 1f },
            tileMode = if (repeating) TileMode.Repeated else TileMode.Clamp,
        )
        if (ellipse) {
            object : ShaderBrush() {
                override fun createShader(size: Size): Shader = (brush as ShaderBrush).createShader(size).apply {
                    setLocalMatrix(android.graphics.Matrix().apply { setScale(1f, size.height / size.width) })
                }
            }
        } else {
            brush
        }
    }

    is SevenTVPaintShape.Solid -> Brush.linearGradient(listOf(Color(color), Color(color)))

    // One copy stretched over the whole box, matching `background-size: 100% 100%`
    is SevenTVPaintShape.Image -> texture?.let { bitmap -> textureBrush(bitmap) }
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

private fun Offset.lerp(
    end: Offset,
    fraction: Float,
): Offset = Offset(x + (end.x - x) * fraction, y + (end.y - y) * fraction)

/** The repeating span of a paint, derived from its CSS fix-up positions. */
private sealed interface RepeatSpan {
    /** A normal span from the first to the last effective stop position. */
    data class Normalized(
        val first: Float,
        val last: Float,
    ) : RepeatSpan

    /** A degenerate period repeats nothing, so the last stop color covers the box. */
    data object Degenerate : RepeatSpan
}

/**
 * Repeating paints span their outer effective stops; everything else keeps its authored CSS positions.
 *
 * CSS never sorts stops: each authored position is clamped to the largest preceding one. A period that collapses
 * to zero length repeats nothing, which resolves to the last stop color covering the box.
 */
private fun List<SevenTVPaintStopUi>.repeatSpan(repeating: Boolean): RepeatSpan? {
    if (!repeating || isEmpty()) return null
    var previous = Float.NEGATIVE_INFINITY
    val effective = map { stop -> maxOf(stop.at, previous).also { previous = it } }
    val first = effective.first()
    val last = effective.last()
    return when {
        last > first -> RepeatSpan.Normalized(first, last)
        else -> RepeatSpan.Degenerate
    }
}

/**
 * Repeating paints normalize over their outer stops; other stops keep their authored CSS positions.
 *
 * Paints in the wild do contain stops at the same position, where two colours meet with a hard edge. Compose does
 * not define what a shader does with equal positions and renders such paints unpredictably, so a repeated
 * position is nudged forward by a hair - the guard Chatterino applies while parsing stops.
 */
private fun List<SevenTVPaintStopUi>.toColorStops(
    userColor: Color,
    repeatSpan: RepeatSpan.Normalized? = null,
): Array<Pair<Float, Color>> {
    if (isEmpty()) return arrayOf(0f to userColor, 1f to userColor)

    var previous = -1f
    return sortedBy { it.at }
        .map { stop ->
            val authored =
                repeatSpan?.let { span -> (stop.at - span.first) / (span.last - span.first) } ?: stop.at
            val position = if (authored > previous) authored else previous + STOP_EPSILON
            previous = position
            position.coerceIn(0f, 1f) to Color(stop.color)
        }.toTypedArray()
}

/**
 * A texture maps one bitmap pixel per pixel, so without a scale it would only cover the top-left corner of the
 * name. The local matrix stretches it over the whole box, which is what `background-size: 100% 100%` does.
 */
private fun textureBrush(bitmap: ImageBitmap): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val source = bitmap.asAndroidBitmap()
        val shader =
            android.graphics.BitmapShader(
                source,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP,
            )
        val scaleX = if (source.width > 0) size.width / source.width else 1f
        val scaleY = if (source.height > 0) size.height / source.height else 1f
        shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(scaleX, scaleY) })
        return shader
    }
}

private const val STOP_EPSILON = 0.0001f
private val SQRT_TWO = sqrt(2f)
