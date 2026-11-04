package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVCosmeticsResponse(
    val data: SevenTVCosmeticsData? = null,
    /**
     * GraphQL reports a rejected document here and still answers 200 with `data: null`. Leaving this
     * unchecked is how a wrong field name silently disabled every paint.
     */
    val errors: List<SevenTVCosmeticsError> = emptyList(),
)

@Keep
@Serializable
data class SevenTVCosmeticsError(
    val message: String,
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

/**
 * One layer of a paint. 7TV exposes four layer kinds and adds new ones over time, so this mirrors the
 * shape of the API rather than enumerating known paints.
 */
@Keep
@Serializable
data class SevenTVPaintLayer(
    val ty: SevenTVPaintLayerType?,
    val opacity: Float = 1f,
)

@Keep
@Serializable
data class SevenTVPaintLayerType(
    @SerialName("__typename") val kind: String? = null,
    val angle: Float? = null,
    val repeating: Boolean = false,
    val stops: List<SevenTVPaintStop> = emptyList(),
    val color: SevenTVColor? = null,
    val images: List<SevenTVBadgeImage> = emptyList(),
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
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
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
    /** Frames in the file; more than one means the paint animates. */
    val frameCount: Int = 1,
)
