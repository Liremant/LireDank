package com.flxrs.dankchat.ui.chat.messages

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintRadialShapeUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintStopUi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaintBrushTest {
    @Test
    fun `three stop horizontal gradient keeps its stops away from the origin`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0f to Color.Red, 0.5f to Color.Green, 1f to Color.Blue),
                angle = 90f,
            ).toBrush(Size(100f, 20f), Color.White)!!

        val translated = render(brush, Size(100f, 20f), Offset(40f, 30f))

        assertClose(Color.Red, translated.colorAt(41, 40), tolerance = 12)
        assertClose(Color.Green, translated.colorAt(90, 40), tolerance = 12)
        assertClose(Color.Blue, translated.colorAt(139, 40), tolerance = 12)
    }

    @Test
    fun `vertical gradient runs upwards from the translated box`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0f to Color.Red, 1f to Color.Blue),
                angle = 0f,
            ).toBrush(Size(40f, 100f), Color.White)!!

        val bitmap = render(brush, Size(40f, 100f), Offset(10f, 20f))

        assertClose(Color.Red, bitmap.colorAt(20, 119), tolerance = 12)
        assertClose(Color.Blue, bitmap.colorAt(20, 21), tolerance = 12)
    }

    @Test
    fun `out of order stops are sorted by their css positions`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0.8f to Color.Red, 0.2f to Color.Blue),
                angle = 90f,
            ).toBrush(Size(100f, 20f), Color.White)!!

        val bitmap = render(brush, Size(100f, 20f))

        assertClose(Color.Blue, bitmap.colorAt(10, 10), tolerance = 12)
        assertClose(Color.Red, bitmap.colorAt(90, 10), tolerance = 12)
    }

    @Test
    fun `equal position stops keep their authored order for a hard edge`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0f to Color.Red, 0.5f to Color.Blue, 0.5f to Color.Green, 1f to Color.Red),
                angle = 90f,
            ).toBrush(Size(100f, 20f), Color.White)!!

        val bitmap = render(brush, Size(100f, 20f))

        assertClose(Color.Blue, bitmap.colorAt(49, 10), tolerance = 12)
        assertClose(Color.Green, bitmap.colorAt(51, 10), tolerance = 12)
    }

    @Test
    fun `repeating linear gradient spans only the outer stop range`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0.25f to Color.Red, 0.75f to Color.Green),
                angle = 90f,
                repeating = true,
            ).toBrush(Size(100f, 20f), Color.White)!!

        val bitmap = render(brush, Size(100f, 20f))

        // The shader span covers x in [25, 75]; TileMode.Repeated wraps outside it like CSS repeating-linear-gradient.
        // x=0 sits half a period before the span start, so it lands mid-transition just like x=50.
        assertClose(Color.Red, bitmap.colorAt(26, 10), tolerance = 12)
        assertClose(Color.Green, bitmap.colorAt(74, 10), tolerance = 12)
        assertClose(bitmap.colorAt(50, 10), bitmap.colorAt(0, 10), tolerance = 2)
        assertClose(Color.Red, bitmap.colorAt(76, 10), tolerance = 12)
    }

    @Test
    fun `repeating linear gradient with a collapsed span paints the last stop color`() {
        // CSS never sorts stops: [0.8, 0.2] collapses to a degenerate zero-length period at 0.8,
        // and repeating nothing resolves to the last stop color covering the box.
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0.8f to Color.Red, 0.2f to Color.Blue),
                angle = 90f,
                repeating = true,
            ).toBrush(Size(100f, 20f), Color.White)!!

        val bitmap = render(brush, Size(100f, 20f))

        assertClose(Color.Blue, bitmap.colorAt(5, 10), tolerance = 12)
        assertClose(Color.Blue, bitmap.colorAt(50, 10), tolerance = 12)
        assertClose(Color.Blue, bitmap.colorAt(95, 10), tolerance = 12)
    }

    @Test
    fun `circle and ellipse use css farthest corner geometry`() {
        val stops = listOf(0f to Color.Red, 0.5f to Color.Green, 1f to Color.Blue)
        val circle = SevenTVPaintShape
            .Radial(
                stops = stops,
                shape = SevenTVPaintRadialShapeUi.CIRCLE,
            ).toBrush(Size(100f, 40f), Color.White)!!
        val ellipse = SevenTVPaintShape
            .Radial(
                stops = stops,
                shape = SevenTVPaintRadialShapeUi.ELLIPSE,
            ).toBrush(Size(100f, 40f), Color.White)!!

        // The same point has different normalized radial distances in the two shapes.
        // The local matrix only applies to the shader's own bounds, not the full destination bitmap.
        val ellipseBitmap = render(ellipse, Size(100f, 40f), bitmapSize = Size(100f, 40f))
        assertClose(Color.Red, ellipseBitmap.colorAt(50, 20), tolerance = 12)
        val top = ellipseBitmap.colorAt(50, 0)
        assertTrue(top.green > 0.45f && top.blue > 0.3f && top.blue < 0.55f, "Ellipse top should sit at t=1/sqrt(2): $top")
        val corner = ellipseBitmap.colorAt(0, 39)
        assertTrue(corner.blue > 0.85f, "Farthest corner should reach the last stop: $corner")

        // The same point has different normalized radial distances in the two shapes.
        val ellipseColor = ellipseBitmap.colorAt(85, 20)
        assertTrue(ellipseColor.red < 0.1f && ellipseColor.green > 0.3f && ellipseColor.blue < 0.7f, "Wrong ellipse geometry: $ellipseColor")
        val circleColor = render(circle, Size(100f, 40f)).colorAt(85, 20)
        assertTrue(circleColor.blue > ellipseColor.blue, "Circle and ellipse should sample different radial distances")
    }

    @Test
    fun `translucent stops preserve alpha instead of compositing over the user color`() {
        val brush = SevenTVPaintShape
            .Linear(
                stops = listOf(0f to Color.Red.copy(alpha = 0.5f), 1f to Color.Red.copy(alpha = 0.5f)),
                angle = 90f,
            ).toBrush(Size(100f, 20f), Color.Blue)!!

        val bitmap = render(brush, Size(100f, 20f))
        val color = bitmap.colorAt(50, 10)

        assertTrue(abs(color.alpha - 0.5f) < 0.02f, "Expected translucent CSS stop, got $color")
        assertTrue(color.red > 0.45f && color.blue < 0.05f, "Stop was composited over the user color: $color")
    }

    @Test
    fun `texture is stretched across a translated box`() {
        val source = Bitmap
            .createBitmap(2, 1, Bitmap.Config.ARGB_8888)
            .apply {
                eraseColor(android.graphics.Color.RED)
                setPixel(1, 0, android.graphics.Color.BLUE)
            }.asImageBitmap()
        val brush = SevenTVPaintShape.Image("paint").toBrush(Size(100f, 20f), Color.White, source)!!

        val bitmap = render(brush, Size(100f, 20f), Offset(10f, 20f))

        assertClose(Color.Red, bitmap.colorAt(15, 30))
        assertClose(Color.Blue, bitmap.colorAt(105, 30))
    }

    private fun render(
        brush: Brush,
        size: Size,
        offset: Offset = Offset.Zero,
        bitmapSize: Size = Size(size.width + offset.x + 1, size.height + offset.y + 1),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(bitmapSize.width.toInt(), bitmapSize.height.toInt(), Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics
                .Canvas(android.graphics.Canvas(bitmap)),
            size = Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        ) {
            translate(offset.x, offset.y) {
                drawRect(brush = brush, size = size)
            }
        }
        return bitmap
    }

    private fun Bitmap.colorAt(
        x: Int,
        y: Int,
    ): Color = Color(getPixel(x, y))
}

private infix fun Float.to(color: Color) = SevenTVPaintStopUi(
    this,
    android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    ),
)

private fun assertClose(
    expected: Color,
    actual: Color,
    tolerance: Int = 1,
) {
    val expectedInt = expected.toAndroidColor()
    val actualInt = actual.toAndroidColor()
    assertTrue(abs(android.graphics.Color.red(expectedInt) - android.graphics.Color.red(actualInt)) <= tolerance, "red: expected $expected, got $actual")
    assertTrue(abs(android.graphics.Color.green(expectedInt) - android.graphics.Color.green(actualInt)) <= tolerance, "green: expected $expected, got $actual")
    assertTrue(abs(android.graphics.Color.blue(expectedInt) - android.graphics.Color.blue(actualInt)) <= tolerance, "blue: expected $expected, got $actual")
}

private fun Color.toAndroidColor() = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
