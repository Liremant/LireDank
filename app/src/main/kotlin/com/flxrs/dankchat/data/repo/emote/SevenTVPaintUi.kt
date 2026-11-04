package com.flxrs.dankchat.data.repo.emote

/**
 * A 7TV paint: an ordered list of layers plus drop shadows, matching what the 7TV website renders.
 *
 * The site draws every layer as its own CSS background clipped to the text and overlays them, so a paint is a
 * list rather than one shape. A paint with no usable layer is dropped and the name keeps its plain colour.
 */
data class SevenTVPaintUi(
    val layers: List<SevenTVPaintLayerUi>,
    val shadows: List<SevenTVPaintShadowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = layers.isEmpty()
}

/** One drawable layer, in draw order. */
data class SevenTVPaintLayerUi(
    val shape: SevenTVPaintShape,
    val opacity: Float = 1f,
)

/** The shapes 7TV describes today. Unknown layer types are dropped rather than guessed at. */
sealed interface SevenTVPaintShape {
    data class Linear(
        val stops: List<SevenTVPaintStopUi>,
        /** CSS degrees: 0 points up, 90 points right. */
        val angle: Float,
        val repeating: Boolean = false,
    ) : SevenTVPaintShape

    data class Radial(
        val stops: List<SevenTVPaintStopUi>,
        val repeating: Boolean = false,
    ) : SevenTVPaintShape

    data class Solid(
        val color: Int,
    ) : SevenTVPaintShape

    /** A texture stretched over the whole name, like `background-size: 100% 100%`. */
    data class Image(
        val url: String,
    ) : SevenTVPaintShape
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
