package com.muse.localplayer.data

data class FeaturedAudioPack(
    val metadata: FeaturedPackMetadata = FeaturedPackMetadata(),
    val tracks: List<Track> = emptyList(),
    val programsByTrackId: Map<Long, FeaturedTrackProgram> = emptyMap()
)

/** Optional, publisher-authored context for one featured asset. */
data class FeaturedTrackProgram(
    val chapters: List<FeaturedChapter> = emptyList(),
    val notes: List<String> = emptyList()
) {
    val hasContent: Boolean
        get() = chapters.isNotEmpty() || notes.isNotEmpty()
}

data class FeaturedChapter(
    val timestampMs: Long,
    val title: String
)

data class ChapterPlaybackState(
    val activeChapter: FeaturedChapter?,
    val activeIndex: Int,
    val completedCount: Int,
    val nextChapter: FeaturedChapter?,
    val chapterProgress: Float,
    val remainingInChapterMs: Long?
) {
    val isTransitionImminent: Boolean
        get() = nextChapter != null && (remainingInChapterMs ?: Long.MAX_VALUE) in 0L..20_000L
}

fun FeaturedTrackProgram.chapterPlaybackState(positionMs: Long, durationMs: Long): ChapterPlaybackState {
    val position = positionMs.coerceAtLeast(0L)
    val activeIndex = chapters.indexOfLast { it.timestampMs <= position }
    val activeChapter = chapters.getOrNull(activeIndex)
    val nextChapter = chapters.getOrNull(activeIndex + 1)
    val chapterEndMs = nextChapter?.timestampMs
        ?: durationMs.takeIf { it > (activeChapter?.timestampMs ?: 0L) }
    val chapterDurationMs = activeChapter?.let { chapter -> chapterEndMs?.minus(chapter.timestampMs) }
    val chapterProgress = if (activeChapter != null && chapterDurationMs != null && chapterDurationMs > 0L) {
        ((position - activeChapter.timestampMs).toFloat() / chapterDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    return ChapterPlaybackState(
        activeChapter = activeChapter,
        activeIndex = activeIndex,
        completedCount = activeIndex.coerceAtLeast(0),
        nextChapter = nextChapter,
        chapterProgress = chapterProgress,
        remainingInChapterMs = chapterEndMs?.minus(position)?.coerceAtLeast(0L)
    )
}

data class FeaturedPackMetadata(
    val title: String = "本期专题音频",
    val eyebrow: String = "MUSE · FEATURED",
    val description: String = "内置于 APK 的专题声音内容。",
    val defaultArtist: String = "精选内容",
    val defaultAlbum: String = "专题音频包",
    val playLabel: String = "从头播放",
    /** Optional image asset path configured by pack.json, for example featured_audio/cover.webp. */
    val coverAssetPath: String? = null
)
