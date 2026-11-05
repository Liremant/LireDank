package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.api.seventv.dto.SevenTVBadgeImage
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVColor
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaint
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintData
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintLayer
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintLayerType
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintRadialShape
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVPaintStop
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SevenTVCosmeticsRepositoryPaintTest {
    private val repository = SevenTVCosmeticsRepository(mockk(), mockk())

    @Test
    fun `radial shape and authored stop order survive mapping`() {
        val paint = SevenTVPaint(
            id = "paint",
            name = "paint",
            data = SevenTVPaintData(
                layers = listOf(
                    SevenTVPaintLayer(
                        ty = SevenTVPaintLayerType(
                            kind = "PaintLayerTypeRadialGradient",
                            shape = SevenTVPaintRadialShape.CIRCLE,
                            stops = listOf(stop(0.8f), stop(0.2f)),
                        ),
                    ),
                ),
            ),
        )

        val radial = assertIs<SevenTVPaintShape.Radial>(with(repository) { paint.toPaintUi() }!!.layers.single().shape)
        assertEquals(SevenTVPaintRadialShapeUi.CIRCLE, radial.shape)
        assertEquals(listOf(0.8f, 0.2f), radial.stops.map { it.at })
    }

    @Test
    fun `image mapping selects one x animated image when a variant animates`() {
        val paint = paintWithImages(
            SevenTVBadgeImage(url = "still", frameCount = 1, scale = 1),
            SevenTVBadgeImage(url = "animated-2x", frameCount = 10, scale = 2),
            SevenTVBadgeImage(url = "animated-1x", frameCount = 10, scale = 1),
        )

        val image = assertIs<SevenTVPaintShape.Image>(with(repository) { paint.toPaintUi() }!!.layers.single().shape)
        assertEquals("animated-1x", image.url)
    }

    @Test
    fun `image mapping does not substitute a still when only an animated variant exists`() {
        val paint = paintWithImages(SevenTVBadgeImage(url = "animated", frameCount = 10, scale = 2))

        assertEquals(null, with(repository) { paint.toPaintUi() })
    }

    private fun paintWithImages(vararg images: SevenTVBadgeImage) = SevenTVPaint(
        id = "paint",
        name = "paint",
        data = SevenTVPaintData(
            layers = listOf(
                SevenTVPaintLayer(
                    ty = SevenTVPaintLayerType(kind = "PaintLayerTypeImage", images = images.toList()),
                ),
            ),
        ),
    )

    private fun stop(at: Float) = SevenTVPaintStop(at = at, color = SevenTVColor(r = 255, g = 0, b = 0))
}
