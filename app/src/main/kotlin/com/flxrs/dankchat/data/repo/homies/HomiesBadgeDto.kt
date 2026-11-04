package com.flxrs.dankchat.data.repo.homies

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Payload of the three community badge lists.
 *
 * The lists come in two shapes: `<code>chatterinohomies.com</code> answers one entry per user, while the
 * `itzalex` pages answer one entry per badge holding the users that own it. Both shapes are tolerated, and
 * either can be wrapped in `{"badges": [...]}` or served as a bare array.
 */
@Keep
@Serializable
data class HomiesBadgeListDto(
    val badges: List<HomiesBadgeDto> = emptyList(),
)

@Keep
@Serializable
data class HomiesBadgeDto(
    val tooltip: String? = null,
    val userId: String? = null,
    val users: List<String> = emptyList(),
    val image1: String? = null,
    val image2: String? = null,
    val image3: String? = null,
)

/** A badge resolved for one user, ready to render. */
data class HomiesBadge(
    val userId: String,
    val tooltip: String,
    val imageUrl: String,
)
