package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Expectations are taken from real 7TV paints: "Haunting" (angle 0) and "Ghoulish" (angle 90).
 */
internal class PaintSamplerTest {
    // Haunting: dark grey, dark brown, pure red, orange
    private val haunting =
        SevenTVPaintUi.Linear(
            stops =
                listOf(
                    SevenTVPaintStopUi(0.0f, GREY),
                    SevenTVPaintStopUi(0.35f, BROWN),
                    SevenTVPaintStopUi(0.48f, RED),
                    SevenTVPaintStopUi(0.74f, ORANGE),
                ),
            angle = 0f,
        )

    // Ghoulish: purple, purple, green - a vertical axis
    private val ghoulish =
        SevenTVPaintUi.Linear(
            stops =
                listOf(
                    SevenTVPaintStopUi(0.15f, PURPLE_LIGHT),
                    SevenTVPaintStopUi(0.45f, PURPLE_DARK),
                    SevenTVPaintStopUi(0.65f, GREEN),
                ),
            angle = 90f,
        )

    @Test
    fun `horizontal paint starts dark and pads with the last stop`() {
        val colors = haunting.colorsFor(length = 10, userColor = Color.White)

        // The first glyph already blends towards the next stop, so it is dark, not the exact first stop
        assertTrue(colors.first().red < 0.45f && colors.first().green < 0.45f, "expected dark grey, was ${colors.first()}")
        // The last stop sits at 0.74, beyond that the gradient pads with its own colour
        assertEquals(Color(ORANGE), colors.last())
    }

    @Test
    fun `horizontal paint is grey for the first half and red for the second`() {
        val colors = haunting.colorsFor(length = 10, userColor = Color.White)

        assertTrue(colors[1].red < 0.6f, "first half must stay grey, was ${colors[1]}")
        assertTrue(colors[7].red > 0.9f && colors[7].green < 0.4f, "second half must be red, was ${colors[7]}")
    }

    @Test
    fun `vertical paint yields a single colour across the line`() {
        val colors = ghoulish.colorsFor(length = 12, userColor = Color.White)

        assertEquals(1, colors.toSet().size, "a 90 degree axis must not sweep across the name: $colors")
    }

    @Test
    fun `vertical paint samples the middle of the gradient`() {
        val color = ghoulish.colorsFor(length = 7, userColor = Color.White).first()

        // Halfway between the dark purple stop (0.45) and the green stop (0.65) is mostly purple
        assertTrue(color.blue > color.green, "expected a purple name, was $color")
    }

    @Test
    fun `repeat wraps instead of clamping`() {
        val repeating = haunting.copy(stops = haunting.stops, repeating = true)

        val colors = repeating.colorsFor(length = 20, userColor = Color.White)

        assertTrue(colors.distinct().size > 3, "a repeating paint must cycle, got ${colors.distinct()}")
    }

    @Test
    fun `semi transparent stops are composited over the user colour`() {
        val paint =
            SevenTVPaintUi.Linear(
                stops = listOf(SevenTVPaintStopUi(0f, 0x80FF0000.toInt()), SevenTVPaintStopUi(1f, 0x80FF0000.toInt())),
                angle = 0f,
            )

        val color = paint.colorsFor(length = 4, userColor = Color.Black).first()

        // 50% red over black is half as bright as the authored red
        assertTrue(color.red in 0.4f..0.6f, "expected a half-red overlay, was $color")
    }

    @Test
    fun `image paints keep the user colour because a texture cannot be flattened`() {
        val paint = SevenTVPaintUi.Image(url = "https://cdn.7tv.app/paint/x.webp")

        assertEquals(Color.White, paint.colorsFor(length = 5, userColor = Color.White).first())
    }

    private companion object {
        val GREY = 0xFF4F4F4F.toInt()
        val BROWN = 0xFF4B3F3F.toInt()
        val RED = 0xFFFF0000.toInt()
        val ORANGE = 0xFFFF4000.toInt()
        val PURPLE_LIGHT = 0xFF893CC4.toInt()
        val PURPLE_DARK = 0xFF5922BF.toInt()
        val GREEN = 0xFF00FF04.toInt()
    }
}
