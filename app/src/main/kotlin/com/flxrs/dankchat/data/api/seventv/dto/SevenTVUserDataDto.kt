package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVUserDataDto(
    val id: String,
    val connections: List<SevenTVUserConnection>,
    @SerialName("emote_sets") val emoteSets: List<SevenTVEmoteSetRefDto> = emptyList(),
)
