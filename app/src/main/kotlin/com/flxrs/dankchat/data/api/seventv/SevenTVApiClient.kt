package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVCosmeticsResponse
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteSetDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class SevenTVApiClient(
    private val sevenTVApi: SevenTVApi,
    private val json: Json,
) {
    suspend fun getSevenTVChannelEmotes(channelId: UserId): Result<SevenTVUserDto?> = runCatching {
        sevenTVApi
            .getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body<SevenTVUserDto>()
    }.recoverNotFoundWith(default = null)

    suspend fun getSevenTVEmoteSet(emoteSetId: String): Result<SevenTVEmoteSetDto> = runCatching {
        sevenTVApi
            .getEmoteSet(emoteSetId)
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getSevenTVGlobalEmotes(): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi
            .getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body<SevenTVEmoteSetDto>()
            .emotes
            .orEmpty()
    }

    /** Paint and badge for a Twitch user id. The GraphQL endpoint answers 200 with a null user when unknown. */
    suspend fun getSevenTVCosmetics(twitchUserId: String): Result<SevenTVCosmeticsResponse?> = runCatching {
        val response =
            sevenTVApi
                .getCosmetics(twitchUserId)
                .throwApiErrorOnFailure(json)
                .body<SevenTVCosmeticsResponse>()

        // A rejected document answers 200 with data:null, so surface the reason instead of hiding it
        if (response.errors.isNotEmpty()) {
            throw IllegalStateException("7TV cosmetics query rejected: ${response.errors.joinToString { it.message }}")
        }
        response
    }.recoverNotFoundWith(default = null)
}
