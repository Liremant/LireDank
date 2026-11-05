package com.flxrs.dankchat.ui.chat.messages.common

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintLayerUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShadowUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextWithMeasuredInlineContentPaintTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `three stop nickname paint covers glyphs after timestamp offset`() {
        val paint = SevenTVPaintUi(
            layers = listOf(
                SevenTVPaintLayerUi(
                    SevenTVPaintShape.Linear(
                        stops = listOf(
                            SevenTVPaintStopUi(0f, android.graphics.Color.RED),
                            SevenTVPaintStopUi(0.5f, android.graphics.Color.GREEN),
                            SevenTVPaintStopUi(1f, android.graphics.Color.BLUE),
                        ),
                        angle = 90f,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            TextWithMeasuredInlineContent(
                text = AnnotatedString("12:34 NICKNAME"),
                namePaint = paint,
                nameRange = TextRange(6, 14),
                nameColor = Color.White,
                modifier = Modifier.testTag("painted"),
            )
        }

        val image = composeRule.onNodeWithTag("painted").captureToImage().asAndroidBitmap()
        val pixels = image.nicknamePixels()

        assertTrue(pixels.size > 30, "Expected painted glyphs at a nonzero origin")
        assertTrue(pixels.take(pixels.size / 3).any { android.graphics.Color.red(it) > 150 && android.graphics.Color.green(it) < 100 }, "Missing red stop")
        assertTrue(pixels.drop(pixels.size / 3).take(pixels.size / 3).any { android.graphics.Color.green(it) > 100 && android.graphics.Color.red(it) < 150 }, "Missing green stop")
        assertTrue(pixels.takeLast(pixels.size / 3).any { android.graphics.Color.blue(it) > 150 && android.graphics.Color.red(it) < 100 }, "Missing blue stop")
    }

    @Test
    fun `every paint shadow renders, chained beneath the painted name`() {
        val paint = SevenTVPaintUi(
            layers = listOf(SevenTVPaintLayerUi(SevenTVPaintShape.Solid(android.graphics.Color.WHITE))),
            shadows = listOf(
                SevenTVPaintShadowUi(android.graphics.Color.RED, offsetX = 6f, offsetY = 0f, blur = 1f),
                SevenTVPaintShadowUi(android.graphics.Color.BLUE, offsetX = 0f, offsetY = 6f, blur = 1f),
            ),
        )

        composeRule.setContent {
            TextWithMeasuredInlineContent(
                text = AnnotatedString("NICK"),
                namePaint = paint,
                nameRange = TextRange(0, 4),
                nameColor = Color.White,
                modifier = Modifier.testTag("painted"),
            )
        }

        val image = composeRule.onNodeWithTag("painted").captureToImage().asAndroidBitmap()
        var red = false
        var blue = false
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 150 && android.graphics.Color.green(pixel) < 80 && android.graphics.Color.blue(pixel) < 80) red = true
                if (android.graphics.Color.blue(pixel) > 150 && android.graphics.Color.red(pixel) < 80 && android.graphics.Color.green(pixel) < 80) blue = true
            }
        }

        assertTrue(red, "First shadow missing")
        assertTrue(blue, "Second shadow missing - only the first one rendered")
    }

    private fun Bitmap.nicknamePixels(): List<Int> = buildList {
        for (x in width / 3 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val saturation = maxOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)) -
                    minOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
                if (saturation > 80) add(pixel)
            }
        }
    }
}
