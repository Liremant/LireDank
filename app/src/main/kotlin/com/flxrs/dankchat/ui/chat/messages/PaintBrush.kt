package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Builds a real shader for the nickname box, the way Chatterino turns a 7TV paint into a QBrush and paints the
 * name tag with it. The gradient covers [size], so a vertical paint shades each glyph top to bottom and a radial
 * paint is a circle centred on the name. Returns null for textures, which cannot be a colour shader.
 */
internal fun SevenTVPaintUi.toBrush(
    size: Size,
    userColor: Color,
): Brush? = when (this) {
    is SevenTVPaintUi.Linear -> {
        val (start, end) = gradientLine(size, angle)
        Brush.linearGradient(
            colorStops = stops.toColorStops(userColor),
            start = start,
            end = end,
            tileMode = tileMode(repeating),
        )
    }

    is SevenTVPaintUi.Radial -> {
        // Chatterino centres the circle on the name and uses the larger side as the diameter
        val radius = max(size.width, size.height) / 2f
        Brush.radialGradient(
            colorStops = stops.toColorStops(userColor),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = if (radius > 0f) radius else 1f,
            tileMode = tileMode(repeating),
        )
    }

    is SevenTVPaintUi.Solid -> Brush.linearGradient(listOf(Color(color), Color(color)))

    is SevenTVPaintUi.Image -> null
}

private fun tileMode(repeating: Boolean): TileMode =
    if (repeating) {
        TileMode.Repeated
    } else {
        TileMode.Clamp
    }

/**
 * Endpoints of the gradient line across [size] at [angle] degrees.
 *
 * 7TV paints are authored in CSS units, where 0 degrees points up and 90 degrees points right, so the axis
 * follows that convention: 0 shades the glyphs bottom to top, 90 sweeps left to right across the name.
 * The line is as long as the box projected onto that direction (the CSS gradient line).
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
 * Stops are used at their authored positions: 7TV already lays them out in 0..1 across the gradient line.
 * Rescaling them would shove the colour transition away from where the paint author put it.
 */
private fun List<SevenTVPaintStopUi>.toColorStops(userColor: Color): Array<Pair<Float, Color>> {
    val sorted = sortedBy { it.at }
    if (sorted.isEmpty()) return arrayOf(0f to userColor, 1f to userColor)

    return sorted
        .map { stop -> stop.at.coerceIn(0f, 1f) to overlayOver(userColor, Color(stop.color)) }
        .sortedBy { it.first }
        .toTypedArray()
}

private fun overlayOver(
    background: Color,
    foreground: Color,
): Color {
    val alpha = foreground.alpha
    return Color(
        red = (1f - alpha) * background.red + alpha * foreground.red,
        green = (1f - alpha) * background.green + alpha * foreground.green,
        blue = (1f - alpha) * background.blue + alpha * foreground.blue,
        alpha = 1f,
    )
}
