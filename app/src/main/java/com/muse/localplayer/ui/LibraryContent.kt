package com.muse.localplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.muse.localplayer.data.Album
import com.muse.localplayer.data.FeaturedPackMetadata
import com.muse.localplayer.data.LibraryTab
import com.muse.localplayer.data.Track
import com.muse.localplayer.playback.LibraryUiState
import com.muse.localplayer.playback.PlayerViewModel

@Composable
internal fun LibraryContent(
    selectedTab: LibraryTab,
    tracks: List<Track>,
    featuredTracks: List<Track>,
    featuredMetadata: FeaturedPackMetadata,
    visibleTracks: List<Track>,
    isSearching: Boolean,
    favoriteIds: Set<Long>,
    libraryUiState: LibraryUiState,
    contentPadding: PaddingValues,
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    playbackHistory: List<Track>,
    bookmarks: List<PlayerViewModel.BookmarkItem>,
    recentlyAdded: List<Track>,
    sleepTimerRemainingMs: Long,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onClearPlaybackHistory: () -> Unit,
    onPlayBookmark: (PlayerViewModel.BookmarkItem) -> Unit,
    onRemoveBookmark: (PlayerViewModel.BookmarkItem) -> Unit,
    onRequestPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRescan: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayFeaturedTracks: () -> Unit,
    onAddTracksToQueue: (List<Track>) -> Unit,
    onPlayAlbum: (List<Track>, Track?) -> Unit,
    onShowSongs: () -> Unit,
    onMore: (Track) -> Unit
) {
    val songsForScreen = remember(visibleTracks, isSearching) {
        if (isSearching) visibleTracks else visibleTracks.filterNot { it.isFeaturedAsset }
    }
    val albums = remember(tracks) {
        tracks.groupBy { it.albumId }.map { (_, songs) ->
            val first = songs.first()
            Album(first.albumId, first.album, first.artist, first.artworkUri, songs.size, first.year)
        }.sortedWith(compareByDescending<Album> { it.year }.thenBy { it.title.lowercase() })
    }
    val favoriteTracks = remember(tracks, favoriteIds) {
        tracks.filter { it.id in favoriteIds }
    }

    when (selectedTab) {
        LibraryTab.HOME -> HomeScreen(
            featuredTracks = featuredTracks,
            featuredMetadata = featuredMetadata,
            libraryUiState = libraryUiState,
            contentPadding = contentPadding,
            audioPermissionGranted = audioPermissionGranted,
            notificationPermissionGranted = notificationPermissionGranted,
            playbackHistory = playbackHistory,
            bookmarks = bookmarks,
            recentlyAdded = recentlyAdded,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onSetSleepTimer = onSetSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onClearPlaybackHistory = onClearPlaybackHistory,
            onPlayBookmark = onPlayBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onRequestPermission = onRequestPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRescan = onRescan,
            onPlay = onPlay,
            onPlayFeaturedTracks = onPlayFeaturedTracks,
            onAddFeaturedTracksToQueue = { onAddTracksToQueue(featuredTracks) },
            onSongsClick = onShowSongs,
            onMore = onMore
        )
        LibraryTab.SONGS -> SongsScreen(
            tracks = songsForScreen,
            isSearching = isSearching,
            libraryUiState = libraryUiState,
            contentPadding = contentPadding,
            onRequestPermission = onRequestPermission,
            onRescan = onRescan,
            onPlay = onPlay,
            onMore = onMore
        )
        LibraryTab.ALBUMS -> AlbumsScreen(
            albums = albums,
            tracks = tracks,
            contentPadding = contentPadding,
            onPlayAlbum = onPlayAlbum,
            onAddTracksToQueue = onAddTracksToQueue,
            onMore = onMore
        )
        LibraryTab.PLAYLISTS -> FavoritesScreen(
            favoriteTracks = favoriteTracks,
            contentPadding = contentPadding,
            onPlay = onPlay,
            onPlayAll = { onPlayAlbum(favoriteTracks, favoriteTracks.firstOrNull()) },
            onAddAllToQueue = { onAddTracksToQueue(favoriteTracks) },
            onMore = onMore
        )
    }
}
