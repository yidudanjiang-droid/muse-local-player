package com.muse.localplayer.data

enum class ContentSearchKind(val label: String) {
    TRACK("曲目"),
    CHAPTER("章节"),
    LYRIC("歌词"),
    NOTE("笔记"),
    BOOKMARK("书签")
}

/** A local-only result that can start a track directly or seek to a publisher/user-defined point. */
data class ContentSearchResult(
    val kind: ContentSearchKind,
    val track: Track,
    val title: String,
    val supportingText: String,
    val positionMs: Long = 0L,
    val bookmark: PlaybackBookmark? = null
)
