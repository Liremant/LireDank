package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.twitch.emote.CheermoteSet
import com.flxrs.dankchat.data.twitch.emote.GenericEmote

data class GlobalEmoteState(
    val twitchEmotes: List<GenericEmote> = emptyList(),
    val ffzEmotes: List<GenericEmote> = emptyList(),
    val bttvEmotes: List<GenericEmote> = emptyList(),
    val sevenTvEmotes: List<GenericEmote> = emptyList(),
)

data class ChannelEmoteState(
    val twitchEmotes: List<GenericEmote> = emptyList(),
    val ffzEmotes: List<GenericEmote> = emptyList(),
    val bttvEmotes: List<GenericEmote> = emptyList(),
    val sevenTvEmotes: List<GenericEmote> = emptyList(),
    val cheermoteSets: List<CheermoteSet> = emptyList(),
)

fun mergeEmotes(
    global: GlobalEmoteState,
    channel: ChannelEmoteState,
): Emotes {
    // Deduplicate twitch emotes by ID — channel (follower) emotes take precedence
    val channelEmoteIds = channel.twitchEmotes.mapTo(mutableSetOf()) { it.id }
    val deduplicatedGlobalTwitchEmotes = global.twitchEmotes.filterNot { it.id in channelEmoteIds }
    return Emotes(
        twitchEmotes = deduplicatedGlobalTwitchEmotes + channel.twitchEmotes,
        ffzChannelEmotes = channel.ffzEmotes,
        ffzGlobalEmotes = global.ffzEmotes,
        bttvChannelEmotes = channel.bttvEmotes,
        bttvGlobalEmotes = global.bttvEmotes,
        sevenTvChannelEmotes = channel.sevenTvEmotes,
        sevenTvGlobalEmotes = global.sevenTvEmotes,
    )
}

data class Emotes(
    val twitchEmotes: List<GenericEmote> = emptyList(),
    val ffzChannelEmotes: List<GenericEmote> = emptyList(),
    val ffzGlobalEmotes: List<GenericEmote> = emptyList(),
    val bttvChannelEmotes: List<GenericEmote> = emptyList(),
    val bttvGlobalEmotes: List<GenericEmote> = emptyList(),
    val sevenTvChannelEmotes: List<GenericEmote> = emptyList(),
    val sevenTvGlobalEmotes: List<GenericEmote> = emptyList(),
) {
    val sorted: List<GenericEmote> =
        buildList {
            addAll(twitchEmotes)

            addAll(ffzChannelEmotes)
            addAll(bttvChannelEmotes)
            addAll(sevenTvChannelEmotes)

            addAll(ffzGlobalEmotes)
            addAll(bttvGlobalEmotes)
            addAll(sevenTvGlobalEmotes)
        }.sortedBy(GenericEmote::code)

    val suggestions: List<GenericEmote> = sorted.distinctBy(GenericEmote::code)
}

/** Adds personal 7TV emotes to the suggestion list; personal entries win on code conflicts. */
fun Emotes.withPersonalEmotes(personal: List<GenericEmote>): Emotes {
    if (personal.isEmpty()) return this
    val personalCodes = personal.mapTo(mutableSetOf()) { it.code }
    val filtered =
        Emotes(
            twitchEmotes = twitchEmotes.filterNot { it.code in personalCodes },
            ffzChannelEmotes = ffzChannelEmotes.filterNot { it.code in personalCodes },
            ffzGlobalEmotes = ffzGlobalEmotes.filterNot { it.code in personalCodes },
            bttvChannelEmotes = bttvChannelEmotes.filterNot { it.code in personalCodes },
            bttvGlobalEmotes = bttvGlobalEmotes.filterNot { it.code in personalCodes },
            sevenTvChannelEmotes = sevenTvChannelEmotes.filterNot { it.code in personalCodes },
            sevenTvGlobalEmotes = sevenTvGlobalEmotes.filterNot { it.code in personalCodes },
        )
    return Emotes(
        twitchEmotes = filtered.twitchEmotes,
        ffzChannelEmotes = filtered.ffzChannelEmotes,
        ffzGlobalEmotes = filtered.ffzGlobalEmotes,
        bttvChannelEmotes = filtered.bttvChannelEmotes,
        bttvGlobalEmotes = filtered.bttvGlobalEmotes,
        sevenTvChannelEmotes = filtered.sevenTvChannelEmotes,
        sevenTvGlobalEmotes = filtered.sevenTvGlobalEmotes + personal,
    )
}
