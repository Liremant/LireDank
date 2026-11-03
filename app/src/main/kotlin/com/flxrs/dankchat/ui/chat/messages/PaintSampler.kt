package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Samples a 7TV paint into one colour per nickname character.
 *
 * Compose cannot put a Brush into a SpanStyle without breaking text measurement, so the shape is evaluated at
 * the position of each glyph instead of being handed to the renderer. The geometry follows Chatterino: a linear
 * paint runs along an axis through the name box at [SevenTVPaintUi.Linear.angle], a radial paint is centred with
 * the radius of the box, and every stopped colour is composited over the user's own name colour by its alpha.
 *
 * Glyphs of one line share a vertical centre, so a vertical axis (90 degrees) legitimately yields one colour.
 */
internal fun SevenTVPaintUi.colorsFor(
    length: Int,
    userColor: Color,
    aspectRatio: Float = DEFAULT_NAME_ASPECT,
): List<Color> {
    if (length <= 0) return emptyList()
    return List(length) { index ->
        // Glyph centres spread across the name box; a single glyph sits in the middle
        val x = if (length == 1) 0.5f else (index + 0.5f) / length
        colorAt(xFraction = x, yFraction = 0.5f, userColor = userColor, aspectRatio = aspectRatio)
    }
}

internal fun SevenTVPaintUi.colorAt(
    xFraction: Float,
    yFraction: Float,
    userColor: Color,
    aspectRatio: Float = DEFAULT_NAME_ASPECT,
): Color = when (this) {
    is SevenTVPaintUi.Linear -> sampleLinear(xFraction, yFraction, userColor, aspectRatio)

    is SevenTVPaintUi.Radial -> sampleRadial(xFraction, yFraction, userColor, aspectRatio)

    is SevenTVPaintUi.Solid -> Color(color)

    // A texture cannot be reduced to a flat colour; the name keeps its own colour instead of guessing
    is SevenTVPaintUi.Image -> userColor
}

private fun SevenTVPaintUi.Linear.sampleLinear(
    xFraction: Float,
    yFraction: Float,
    userColor: Color,
    aspectRatio: Float,
): Color {
    val radians = Math.toRadians(angle.toDouble())
    // Box is in relative units, so the x axis is scaled by the width/height ratio to keep the angle honest
    val dx = (xFraction - 0.5f) * aspectRatio
    val dy = yFraction - 0.5f
    val projected = dx * cos(radians).toFloat() + dy * sin(radians).toFloat()
    val halfExtent = (hypot(cos(radians).toDouble() * aspectRatio, sin(radians).toDouble()) / 2).toFloat()
    val t = if (halfExtent <= 0f) 0.5f else 0.5f + projected / (2f * halfExtent)
    return sampleStops(stops, t, repeating, userColor)
}

private fun SevenTVPaintUi.Radial.sampleRadial(
    xFraction: Float,
    yFraction: Float,
    userColor: Color,
    aspectRatio: Float,
): Color {
    // Chatterino uses the larger box side as the diameter
    val radius = maxOf(aspectRatio, 1f) / 2f
    val distance = hypot(((xFraction - 0.5f) * aspectRatio).toDouble(), (yFraction - 0.5f).toDouble()).toFloat()
    val t = if (radius <= 0f) 0f else distance / radius
    return sampleStops(stops, t, repeating, userColor)
}

private fun sampleStops(
    stops: List<SevenTVPaintStopUi>,
    t: Float,
    repeating: Boolean,
    userColor: Color,
): Color {
    if (stops.isEmpty()) return userColor
    val sorted = stops.sortedBy { it.at }
    val normalized = if (repeating) normalizeRepeating(sorted, t) else t
    val clamped = normalized.coerceIn(sorted.first().at, sorted.last().at)
    val upperIndex = sorted.indexOfFirst { it.at >= clamped }.takeIf { it >= 0 } ?: sorted.lastIndex
    val upper = sorted[upperIndex]
    if (upperIndex == 0) return overlay(userColor, Color(upper.color))
    val lower = sorted[upperIndex - 1]
    val span = upper.at - lower.at
    val local = if (span <= 0f) 1f else ((clamped - lower.at) / span).coerceIn(0f, 1f)
    return overlay(userColor, lerp(Color(lower.color), Color(upper.color), local))
}

/** Chatterino rescales the stops into 0..1 across their own range before repeating. */
private fun normalizeRepeating(
    stops: List<SevenTVPaintStopUi>,
    t: Float,
): Float {
    val start = stops.first().at
    val end = stops.last().at
    val length = end - start
    if (length <= 0f) return start
    val wrapped = ((t - start) % length + length) % length
    return start + wrapped
}

private fun overlay(
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

private fun lerp(
    from: Color,
    to: Color,
    fraction: Float,
): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction,
)

// A nickname is roughly this many times wider than the text line is tall
private const val DEFAULT_NAME_ASPECT = 6f
