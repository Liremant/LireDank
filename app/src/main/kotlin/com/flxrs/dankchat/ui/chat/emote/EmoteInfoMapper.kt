package com.flxrs.dankchat.ui.chat.emote

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType

private const val SEVEN_TV_BASE_LINK = "https://7tv.app/emotes/"
private const val FFZ_BASE_LINK = "https://www.frankerfacez.com/emoticon/"
private const val BTTV_BASE_LINK = "https://betterttv.com/emotes/"
private const val TWITCH_BASE_LINK = "https://chatvau.lt/emote/twitch/"

fun EmoteSheetData.toBasicEmoteInfoItem(): EmoteInfoItem = EmoteInfoItem(
    id = id,
    name = code,
    imageUrl = url,
    baseName = when (type) {
        is ChatMessageEmoteType.ChannelSevenTVEmote -> type.baseName
        is ChatMessageEmoteType.PersonalSevenTVEmote -> type.baseName
        is ChatMessageEmoteType.GlobalSevenTVEmote -> type.baseName
        else -> null
    },
    creatorName = when (type) {
        is ChatMessageEmoteType.ChannelSevenTVEmote -> type.creator
        is ChatMessageEmoteType.PersonalSevenTVEmote -> type.creator
        is ChatMessageEmoteType.GlobalSevenTVEmote -> type.creator
        is ChatMessageEmoteType.ChannelBTTVEmote -> type.creator
        is ChatMessageEmoteType.ChannelFFZEmote -> type.creator
        is ChatMessageEmoteType.GlobalFFZEmote -> type.creator
        else -> null
    },
    providerUrl = when (type) {
        is ChatMessageEmoteType.ChannelSevenTVEmote,
        is ChatMessageEmoteType.GlobalSevenTVEmote,
        -> "$SEVEN_TV_BASE_LINK$id"

        is ChatMessageEmoteType.ChannelBTTVEmote,
        ChatMessageEmoteType.GlobalBTTVEmote,
        -> "$BTTV_BASE_LINK$id"

        is ChatMessageEmoteType.ChannelFFZEmote,
        is ChatMessageEmoteType.GlobalFFZEmote,
        -> "$FFZ_BASE_LINK$id-$code"

        else -> "$TWITCH_BASE_LINK$id"
    },
    isZeroWidth = false,
    emoteType = when (type) {
        is ChatMessageEmoteType.ChannelBTTVEmote -> when {
            type.isShared -> R.string.emote_sheet_bttv_shared_emote
            else -> R.string.emote_sheet_bttv_channel_emote
        }

        is ChatMessageEmoteType.ChannelFFZEmote -> R.string.emote_sheet_ffz_channel_emote

        is ChatMessageEmoteType.ChannelSevenTVEmote -> R.string.emote_sheet_seventv_channel_emote

        is ChatMessageEmoteType.PersonalSevenTVEmote -> R.string.emote_sheet_seventv_personal_emote

        ChatMessageEmoteType.GlobalBTTVEmote -> R.string.emote_sheet_bttv_global_emote

        is ChatMessageEmoteType.GlobalFFZEmote -> R.string.emote_sheet_ffz_global_emote

        is ChatMessageEmoteType.GlobalSevenTVEmote -> R.string.emote_sheet_seventv_global_emote

        else -> R.string.emote_sheet_twitch_emote
    },
)
