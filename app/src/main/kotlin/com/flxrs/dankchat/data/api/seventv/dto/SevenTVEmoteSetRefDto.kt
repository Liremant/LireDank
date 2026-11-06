package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * A reference to one of a user's 7TV emote sets. The account's personal set is the one carrying
 * [SevenTVEmoteSetFlags.PERSONAL] in [flags].
 */
@Keep
@Serializable
data class SevenTVEmoteSetRefDto(
    val id: String,
    val flags: Long = 0,
)

object SevenTVEmoteSetFlags {
    const val IMMUTABLE = 1L
    const val PRIVILEGED = 1L shl 1
    const val PERSONAL = 1L shl 2
    const val COMMERCIAL = 1L shl 3
}
