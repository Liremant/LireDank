package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVCosmeticsResponse(
    val data: SevenTVCosmeticsData,
)

@Keep
@Serializable
data class SevenTVCosmeticsData(
    val users: SevenTVCosmeticsUsers?,
)

@Keep
@Serializable
data class SevenTVCosmeticsUsers(
    val userByConnection: SevenTVCosmeticsUser?,
)

@Keep
@Serializable
data class SevenTVCosmeticsUser(
    val style: SevenTVUserStyle?,
)

@Keep
@Serializable
data class SevenTVUserStyle(
    val activePaint: SevenTVPaint?,
    val activeBadge: SevenTVBadge?,
)

@Keep
@Serializable
data class SevenTVPaint(
    val id: String,
    val name: String?,
    val data: SevenTVPaintData?,
)

@Keep
@Serializable
data class SevenTVPaintData(
    val layers: List<SevenTVPaintLayer> = emptyList(),
    val shadows: List<SevenTVPaintShadow> = emptyList(),
)

/** Only linear gradients carry usable colour stops; other layer types are ignored. */
@Keep
@Serializable
data class SevenTVPaintLayer(
    val ty: SevenTVPaintLayerType?,
)

@Keep
@Serializable
data class SevenTVPaintLayerType(
    val angle: Float? = null,
    val stops: List<SevenTVPaintStop> = emptyList(),
)

@Keep
@Serializable
data class SevenTVPaintStop(
    val at: Float,
    val color: SevenTVColor,
)

@Keep
@Serializable
data class SevenTVPaintShadow(
    val color: SevenTVColor,
    val blur: Float = 0f,
)

@Keep
@Serializable
data class SevenTVColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int = 255,
)

@Keep
@Serializable
data class SevenTVBadge(
    val id: String,
    val name: String?,
    val images: List<SevenTVBadgeImage> = emptyList(),
)

@Keep
@Serializable
data class SevenTVBadgeImage(
    val url: String,
)
