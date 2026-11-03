package com.flxrs.dankchat.data.repo.emote

/**
 * A 7TV paint, modelled the way Chatterino models it: a shape plus optional drop shadows.
 *
 * 7TV keeps adding shapes, so this is a closed set of the four shapes the API currently describes rather than
 * a per-paint table. Anything unknown resolves to [Solid] with no colour and therefore no paint.
 */
sealed interface SevenTVPaintUi {
    val shadows: List<SevenTVPaintShadowUi>

    data class Linear(
        val stops: List<SevenTVPaintStopUi>,
        /** Degrees. 0 sweeps across the name, 90 runs down the text line. */
        val angle: Float,
        val repeating: Boolean = false,
        override val shadows: List<SevenTVPaintShadowUi> = emptyList(),
    ) : SevenTVPaintUi

    data class Radial(
        val stops: List<SevenTVPaintStopUi>,
        val repeating: Boolean = false,
        override val shadows: List<SevenTVPaintShadowUi> = emptyList(),
    ) : SevenTVPaintUi

    data class Solid(
        val color: Int,
        override val shadows: List<SevenTVPaintShadowUi> = emptyList(),
    ) : SevenTVPaintUi

    /** Texture paints are drawn as an image brush; they carry no colours to sample. */
    data class Image(
        val url: String,
        override val shadows: List<SevenTVPaintShadowUi> = emptyList(),
    ) : SevenTVPaintUi
}

data class SevenTVPaintStopUi(
    val at: Float,
    val color: Int,
)

data class SevenTVPaintShadowUi(
    val color: Int,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blur: Float = 0f,
) {
    val isVisible: Boolean get() = blur > 0f
}
