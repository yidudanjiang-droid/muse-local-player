package com.muse.localplayer.data

import android.net.Uri

enum class TrackSource {
    FEATURED_ASSET,
    DEVICE
}

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri? = null,
    val dateAddedSeconds: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val source: TrackSource = TrackSource.DEVICE
) {
    val durationLabel: String
        get() {
            val totalSeconds = durationMs / 1_000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val isFeaturedAsset: Boolean
        get() = source == TrackSource.FEATURED_ASSET
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val trackCount: Int,
    val year: Int = 0
)

enum class LibraryTab(val label: String) {
    HOME("首页"),
    SONGS("歌曲"),
    ALBUMS("专辑"),
    PLAYLISTS("收藏")
}
