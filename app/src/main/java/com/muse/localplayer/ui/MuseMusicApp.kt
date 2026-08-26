package com.muse.localplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.muse.localplayer.BuildConfig
import com.muse.localplayer.R
import com.muse.localplayer.data.Album
import com.muse.localplayer.data.FeaturedPackMetadata
import com.muse.localplayer.data.FeaturedTrackProgram
import com.muse.localplayer.data.LibraryTab
import com.muse.localplayer.data.LyricLine
import com.muse.localplayer.data.Track
import com.muse.localplayer.playback.LibraryUiState
import com.muse.localplayer.playback.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuseMusicApp(
    viewModel: PlayerViewModel,
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAudioEffects: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val featuredTracks by viewModel.featuredTracks.collectAsStateWithLifecycle()
    val featuredPackMetadata by viewModel.featuredPackMetadata.collectAsStateWithLifecycle()
    val libraryUiState by viewModel.libraryUiState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val mixingPlaybackEnabled by viewModel.mixingPlaybackEnabled.collectAsStateWithLifecycle()
    val fadeTransitionsEnabled by viewModel.fadeTransitionsEnabled.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarkItems.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val playerMessage by viewModel.playerMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(LibraryTab.HOME) }
    var searchMode by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var appliedSearchText by remember { mutableStateOf("") }
    var playerOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(searchText) {
        if (searchText.isBlank()) {
            appliedSearchText = ""
        } else {
            delay(160)
            appliedSearchText = searchText
        }
    }
    val filteredTracks = remember(tracks, appliedSearchText) {
        val query = appliedSearchText.trim()
        if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }
    val compactLayout = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(playerMessage) {
        playerMessage?.let { feedback ->
            val result = snackbarHostState.showSnackbar(
                message = feedback.text,
                actionLabel = feedback.actionLabel
            )
            viewModel.dismissPlayerMessage()
            if (result == SnackbarResult.ActionPerformed && feedback.actionLabel != null) {
                viewModel.retryLastFailedTrack()
            }
        }
    }
    val destinations = remember {
        listOf(
            Triple(LibraryTab.HOME, Icons.Default.Home, "首页"),
            Triple(LibraryTab.SONGS, Icons.Default.LibraryMusic, "设备"),
            Triple(LibraryTab.ALBUMS, Icons.Default.Album, "专辑"),
            Triple(LibraryTab.PLAYLISTS, Icons.Default.FavoriteBorder, "收藏")
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(320.dp)
            ) {
                NavigationDrawerHeader()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("设备音乐") },
                    selected = selectedTab == LibraryTab.SONGS,
                    onClick = {
                        selectedTab = LibraryTab.SONGS
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("当前播放队列") },
                    selected = false,
                    badge = { if (queue.isNotEmpty()) Text(queue.size.toString()) },
                    onClick = {
                        queueOpen = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("音效与均衡器") },
                    selected = false,
                    onClick = {
                        onOpenAudioEffects()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.MusicNote, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("重新扫描设备") },
                    selected = false,
                    onClick = {
                        onRequestAudioPermission()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("关于 Muse") },
                    selected = false,
                    onClick = {
                        aboutOpen = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (searchMode) {
                    SearchTopBar(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onClose = {
                            searchMode = false
                            searchText = ""
                            appliedSearchText = ""
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { Text("Muse", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "打开导航菜单")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                selectedTab = LibraryTab.SONGS
                                searchMode = true
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索音乐")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = currentTrack != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        currentTrack?.let { track ->
                            MiniPlayerWithProgress(
                                viewModel = viewModel,
                                track = track,
                                isPlaying = isPlaying,
                                onOpen = { playerOpen = true },
                                onOpenQueue = { queueOpen = true }
                            )
                        }
                    }
                    if (compactLayout) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { (tab, icon, label) ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (compactLayout) {
                LibraryContent(
                    selectedTab = selectedTab,
                    tracks = tracks,
                    featuredTracks = featuredTracks,
                    featuredMetadata = featuredPackMetadata,
                    visibleTracks = filteredTracks,
                    isSearching = searchMode && appliedSearchText.isNotBlank(),
                    favoriteIds = favoriteIds,
                    libraryUiState = libraryUiState,
                    contentPadding = paddingValues,
                    audioPermissionGranted = audioPermissionGranted,
                    notificationPermissionGranted = notificationPermissionGranted,
                    playbackHistory = playbackHistory,
                    bookmarks = bookmarks,
                    recentlyAdded = recentlyAdded,
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    onClearPlaybackHistory = viewModel::clearPlaybackHistory,
                    onPlayBookmark = viewModel::playBookmark,
                    onRemoveBookmark = viewModel::removeBookmark,
                    onRequestPermission = onRequestAudioPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRescan = viewModel::reloadLibrary,
                    onPlay = viewModel::play,
                    onPlayFeaturedTracks = viewModel::playFeaturedTracks,
                    onAddTracksToQueue = { tracksToAdd ->
                        val addedCount = viewModel.addTracksToQueue(tracksToAdd)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (addedCount > 0) "已加入 ${addedCount} 首到播放队列" else "这些歌曲已在播放队列中"
                            )
                        }
                    },
                    onPlayAlbum = viewModel::playAlbum,
                    onShowSongs = { selectedTab = LibraryTab.SONGS },
                    onMore = { actionTrack = it }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { (tab, icon, label) ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        LibraryContent(
                            selectedTab = selectedTab,
                            tracks = tracks,
                            featuredTracks = featuredTracks,
                            featuredMetadata = featuredPackMetadata,
                            visibleTracks = filteredTracks,
                            isSearching = searchMode && appliedSearchText.isNotBlank(),
                            favoriteIds = favoriteIds,
                            libraryUiState = libraryUiState,
                            contentPadding = PaddingValues(),
                            audioPermissionGranted = audioPermissionGranted,
                            notificationPermissionGranted = notificationPermissionGranted,
                            playbackHistory = playbackHistory,
                            bookmarks = bookmarks,
                            recentlyAdded = recentlyAdded,
                            sleepTimerRemainingMs = sleepTimerRemainingMs,
                            onSetSleepTimer = viewModel::setSleepTimer,
                            onCancelSleepTimer = viewModel::cancelSleepTimer,
                            onClearPlaybackHistory = viewModel::clearPlaybackHistory,
                            onPlayBookmark = viewModel::playBookmark,
                            onRemoveBookmark = viewModel::removeBookmark,
                            onRequestPermission = onRequestAudioPermission,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onRescan = viewModel::reloadLibrary,
                            onPlay = viewModel::play,
                            onPlayFeaturedTracks = viewModel::playFeaturedTracks,
                            onAddTracksToQueue = { tracksToAdd ->
                                val addedCount = viewModel.addTracksToQueue(tracksToAdd)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (addedCount > 0) "已加入 ${addedCount} 首到播放队列" else "这些歌曲已在播放队列中"
                                    )
                                }
                            },
                            onPlayAlbum = viewModel::playAlbum,
                            onShowSongs = { selectedTab = LibraryTab.SONGS },
                            onMore = { actionTrack = it }
                        )
                    }
                }
            }
        }
    }

    actionTrack?.let { track ->
        TrackActionsSheet(
            track = track,
            isFavorite = track.id in favoriteIds,
            onDismiss = { actionTrack = null },
            onPlay = {
                viewModel.play(track)
                actionTrack = null
            },
            onAddToQueue = {
                val addedCount = viewModel.addToQueue(track)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (addedCount > 0) "已加入播放队列" else "该歌曲已在播放队列中"
                    )
                }
                actionTrack = null
            },
            onPlayNext = {
                viewModel.playNext(track)
                scope.launch { snackbarHostState.showSnackbar("已安排为下一首播放") }
                actionTrack = null
            },
            onToggleFavorite = {
                val wasFavorite = track.id in favoriteIds
                viewModel.toggleFavorite(track)
                scope.launch { snackbarHostState.showSnackbar(if (wasFavorite) "已取消收藏" else "已加入收藏") }
                actionTrack = null
            }
        )
    }

    if (playerOpen && currentTrack != null) {
        PlayerSheetWithProgress(
            viewModel = viewModel,
            track = currentTrack!!,
            isPlaying = isPlaying,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playbackSpeed,
            mixingPlaybackEnabled = mixingPlaybackEnabled,
            fadeTransitionsEnabled = fadeTransitionsEnabled,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            isFavorite = currentTrack!!.id in favoriteIds,
            onDismiss = { playerOpen = false },
            onToggle = viewModel::togglePlayback,
            onNext = viewModel::skipNext,
            onPrevious = viewModel::skipPrevious,
            onSeek = viewModel::seekToProgress,
            onSetPlaybackStrategy = { strategy ->
                viewModel.setPlaybackStrategy(strategy.repeatMode, strategy.shuffleEnabled)
            },
            onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
            onSetMixingPlayback = viewModel::setMixingPlaybackEnabled,
            onSetFadeTransitions = viewModel::setFadeTransitionsEnabled,
            onSetSleepTimer = viewModel::setSleepTimer,
            onCancelSleepTimer = viewModel::cancelSleepTimer,
            onAddBookmark = viewModel::addBookmark,
            onToggleFavorite = { viewModel.toggleFavorite(currentTrack!!) },
            onOpenQueue = {
                playerOpen = false
                queueOpen = true
            }
        )
    }

    if (queueOpen) {
        QueueSheet(
            queue = queue,
            currentTrack = currentTrack,
            onDismiss = { queueOpen = false },
            onPlay = viewModel::playQueueItem,
            onRemove = { index ->
                viewModel.removeFromQueue(index)?.let { removedTrack ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "已将《${removedTrack.title}》移出队列",
                            actionLabel = "撤销",
                            withDismissAction = true
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            if (viewModel.restoreLastRemovedQueueItem()) {
                                snackbarHostState.showSnackbar("已恢复到原队列位置")
                            }
                        }
                    }
                }
            },
            onMove = viewModel::moveQueueItem,
            onRemovePlayed = {
                val removedCount = viewModel.removePlayedQueueItems()
                if (removedCount > 0) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "已移除 ${removedCount} 首已播歌曲",
                            actionLabel = "撤销",
                            withDismissAction = true
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            if (viewModel.restoreLastTrimmedQueueItems()) {
                                snackbarHostState.showSnackbar("已恢复已播歌曲")
                            }
                        }
                    }
                }
            },
            onClear = {
                viewModel.clearQueue()
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "已清空播放队列",
                        actionLabel = "撤销",
                        withDismissAction = true
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        if (viewModel.restoreLastClearedQueue()) {
                            snackbarHostState.showSnackbar("已恢复播放队列")
                        }
                    }
                }
            }
        )
    }

    if (aboutOpen) {
        AboutSheet(onDismiss = { aboutOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索专题与设备音乐") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotBlank()) {
                    { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "清除搜索") } }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出搜索")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun NavigationDrawerHeader() {
    Row(
        modifier = Modifier.padding(start = 28.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_muse_brand_mark),
                    contentDescription = "Muse 图标",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Muse 本地音乐", style = MaterialTheme.typography.titleLarge)
            Text("专题音频与设备音乐", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HomeScreen(
    featuredTracks: List<Track>,
    featuredMetadata: FeaturedPackMetadata,
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
    onAddFeaturedTracksToQueue: () -> Unit,
    onSongsClick: () -> Unit,
    onMore: (Track) -> Unit
) {
    val featuredDuration = featuredTracks.sumOf { it.durationMs }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text("精选专题", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(featuredMetadata.title, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    featuredMetadata.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FeaturedPackageHero(
                metadata = featuredMetadata,
                artworkUri = featuredTracks.firstOrNull()?.artworkUri,
                trackCount = featuredTracks.size,
                totalDurationMs = featuredDuration,
                onPlay = if (featuredTracks.isEmpty()) null else onPlayFeaturedTracks,
                onAddToQueue = if (featuredTracks.isEmpty()) null else onAddFeaturedTracksToQueue
            )
        }
        item {
            SleepTimerCard(
                remainingMs = sleepTimerRemainingMs,
                onSetTimer = onSetSleepTimer,
                onCancelTimer = onCancelSleepTimer
            )
        }
        if (bookmarks.isNotEmpty()) {
            item {
                Text("继续收听", style = MaterialTheme.typography.titleLarge)
            }
            items(bookmarks.take(HOME_PREVIEW_LIMIT), key = { "bookmark_${it.bookmark.savedAtEpochMs}" }) { item ->
                BookmarkListItem(
                    item = item,
                    onPlay = { onPlayBookmark(item) },
                    onRemove = { onRemoveBookmark(item) }
                )
            }
        }
        if (playbackHistory.isNotEmpty()) {
            item { SectionHeader("最近播放", "清空", onClearPlaybackHistory) }
            items(playbackHistory.take(HOME_PREVIEW_LIMIT), key = { "history_${it.id}" }) { track ->
                TrackListItem(track = track, onClick = { onPlay(track) }, onMore = { onMore(track) })
            }
        }
        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader("最近加入", "设备资料库", onSongsClick) }
            items(recentlyAdded, key = { "recent_${it.id}" }) { track ->
                TrackListItem(track = track, onClick = { onPlay(track) }, onMore = { onMore(track) })
            }
        }
        if (featuredTracks.isEmpty()) {
            item { EmptyFeaturedAudioCard(featuredMetadata) }
        } else {
            item { SectionHeader("专题曲目", "全部播放", onPlayFeaturedTracks) }
            items(featuredTracks, key = { it.id }) { track ->
                TrackListItem(track = track, onClick = { onPlay(track) }, onMore = { onMore(track) })
            }
        }
        item {
            PermissionStatusCard(
                audioPermissionGranted = audioPermissionGranted,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestAudioPermission = onRequestPermission,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }
        item { SectionHeader("设备音乐", "打开资料库", onSongsClick) }
        item { LibraryStatusCard(libraryUiState = libraryUiState, onRequestPermission = onRequestPermission, onRescan = onRescan) }
    }
}

@Composable
private fun BookmarkListItem(
    item: PlayerViewModel.BookmarkItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(Modifier.size(48.dp), item.track.artworkUri, item.track.title, ArtEmphasis.Secondary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "从 ${formatTime(item.bookmark.positionMs)} 继续 · ${item.track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onRemove) { Text("移除") }
        }
    }
}

@Composable
private fun FeaturedPackageHero(
    metadata: FeaturedPackMetadata,
    artworkUri: android.net.Uri?,
    trackCount: Int,
    totalDurationMs: Long,
    onPlay: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?
) {
    Card(
        onClick = { onPlay?.invoke() },
        enabled = onPlay != null,
        modifier = Modifier.fillMaxWidth().height(238.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri ?: R.drawable.muse_featured_hero)
                    .size(640)
                    .memoryCacheKey("featured_hero_${artworkUri ?: "default"}")
                    .diskCacheKey("featured_hero_${artworkUri ?: "default"}")
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0x33000000)).padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Text(metadata.eyebrow, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(metadata.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (trackCount == 0) "等待你放入第一首音频" else "$trackCount 首 · ${formatTime(totalDurationMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.86f)
                    )
                    if (onPlay != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = onPlay) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(metadata.playLabel)
                            }
                            if (onAddToQueue != null) {
                                OutlinedButton(onClick = onAddToQueue) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("加入队列")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeaturedAudioCard(metadata: FeaturedPackMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("${metadata.title} 尚未加入音频", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("在 app/src/main/assets/featured_audio/ 放入 MP3、M4A、AAC、OGG、WAV 或 FLAC 文件。应用会在构建后自动扫描并展示它们，无需请求手机存储权限。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SleepTimerCard(
    remainingMs: Long,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("睡眠定时", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (remainingMs > 0L) "将在 ${formatTime(remainingMs)} 后平滑暂停播放" else "设置后将自动平滑暂停，适合睡前收听",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                    )
                }
                if (remainingMs > 0L) TextButton(onClick = onCancelTimer) { Text("关闭") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    AssistChip(
                        onClick = { onSetTimer(minutes) },
                        label = { Text("${minutes} 分钟") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("权限状态", style = MaterialTheme.typography.titleMedium)
            PermissionStatusRow(
                icon = Icons.Default.LibraryMusic,
                title = "设备音乐",
                description = if (audioPermissionGranted) {
                    "已允许 · 可以扫描并播放设备中的本地音频"
                } else {
                    "未授权 · 设备音乐资料库暂不可用"
                },
                granted = audioPermissionGranted,
                actionLabel = "授权音乐",
                onRequest = onRequestAudioPermission
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionStatusRow(
                icon = Icons.Default.Notifications,
                title = "播放通知",
                description = if (notificationPermissionGranted) {
                    "已允许 · 通知栏与锁屏会显示播放控制"
                } else {
                    "未授权 · 后台播放正常，但系统播放通知不会显示"
                },
                granted = notificationPermissionGranted,
                actionLabel = "授权通知",
                onRequest = onRequestNotificationPermission
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onRequest: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text("已允许", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onRequest) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LibraryStatusCard(libraryUiState: LibraryUiState, onRequestPermission: () -> Unit, onRescan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(libraryStatusTitle(libraryUiState), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(libraryStatusDescription(libraryUiState), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            when (libraryUiState) {
                LibraryUiState.PermissionRequired -> TextButton(onClick = onRequestPermission) { Text("授权") }
                is LibraryUiState.Error, LibraryUiState.Empty, is LibraryUiState.Ready -> TextButton(onClick = onRescan) { Text("扫描") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun FeaturedTrackCard(track: Track, onPlay: (Track) -> Unit) {
    Card(
        onClick = { onPlay(track) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(modifier = Modifier.size(88.dp), artworkUri = track.artworkUri, seed = track.title, emphasis = ArtEmphasis.Primary)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("从这里开始", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                Spacer(Modifier.height(3.dp))
                Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} · ${track.album}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = { onPlay(track) }) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放")
                }
            }
        }
    }
}

@Composable
internal fun SongsScreen(
    tracks: List<Track>,
    isSearching: Boolean,
    libraryUiState: LibraryUiState,
    contentPadding: PaddingValues,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    onPlay: (Track) -> Unit,
    onMore: (Track) -> Unit
) {
    var sortAscending by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf(SongSortMode.TITLE) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortedTracks = remember(tracks, sortAscending, sortMode) {
        val comparator = when (sortMode) {
            SongSortMode.TITLE -> compareBy<Track> { it.title.lowercase() }
            SongSortMode.ARTIST -> compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
            SongSortMode.ALBUM -> compareBy<Track> { it.album.lowercase() }.thenBy { it.trackNumber }.thenBy { it.title.lowercase() }
            SongSortMode.DATE_ADDED -> compareBy<Track> { it.dateAddedSeconds }.thenBy { it.title.lowercase() }
        }
        if (sortAscending) tracks.sortedWith(comparator) else tracks.sortedWith(comparator.reversed())
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(if (isSearching) "搜索结果" else "设备音乐", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSearching) "${tracks.size} 首匹配的专题音频或设备音乐" else "${tracks.size} 首设备本地音乐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    AssistChip(
                        onClick = { sortMenuExpanded = true },
                        label = { Text("排序：${sortMode.label}") },
                        leadingIcon = { Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        SongSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    sortMode = mode
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                AssistChip(
                    onClick = { sortAscending = !sortAscending },
                    label = { Text(if (sortAscending) "正序" else "倒序") },
                    leadingIcon = { Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (tracks.isEmpty()) item { EmptyLibraryCard(libraryUiState, onRequestPermission, onRescan) }
        else items(sortedTracks, key = { it.id }) { track ->
            TrackListItem(track = track, onClick = { onPlay(track) }, onMore = { onMore(track) })
        }
    }
}

@Composable
internal fun AlbumsScreen(
    albums: List<Album>,
    tracks: List<Track>,
    contentPadding: PaddingValues,
    onPlayAlbum: (List<Track>, Track?) -> Unit,
    onAddTracksToQueue: (List<Track>) -> Unit,
    onMore: (Track) -> Unit
) {
    var selectedAlbumId by remember { mutableStateOf<Long?>(null) }
    val selectedAlbum = remember(albums, selectedAlbumId) {
        selectedAlbumId?.let { id -> albums.firstOrNull { it.id == id } }
    }
    val selectedTracks = remember(tracks, selectedAlbumId) {
        selectedAlbumId?.let { id ->
            tracks.filter { it.albumId == id }
                .sortedWith(
                    compareBy<Track> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
        }.orEmpty()
    }

    if (selectedAlbum != null) {
        AlbumDetailScreen(
            album = selectedAlbum,
            tracks = selectedTracks,
            contentPadding = contentPadding,
            onBack = { selectedAlbumId = null },
            onPlayAll = { onPlayAlbum(selectedTracks, selectedTracks.firstOrNull()) },
            onAddToQueue = { onAddTracksToQueue(selectedTracks) },
            onPlayTrack = { track -> onPlayAlbum(selectedTracks, track) },
            onMore = onMore
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("专辑", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("点按专辑即可查看曲目并连续播放", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
        }
        if (albums.isEmpty()) item { EmptyAlbumsCard() }
        else items(albums, key = { it.id }) { album ->
            Card(
                onClick = { selectedAlbumId = album.id },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ListItem(
                    headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${album.artist} · ${album.trackCount} 首歌曲", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AlbumArt(Modifier.size(56.dp), album.artworkUri, album.title, ArtEmphasis.Tertiary) },
                    trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = "查看并播放 ${album.title}") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    album: Album,
    tracks: List<Track>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onMore: (Track) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回专辑列表")
                }
                Spacer(Modifier.width(4.dp))
                Text("专辑详情", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(Modifier.size(104.dp), album.artworkUri, album.title, ArtEmphasis.Tertiary)
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tracks.size} 首歌曲${if (album.year > 0) " · ${album.year}" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onPlayAll, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放整张专辑")
                }
                OutlinedButton(onClick = onAddToQueue, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("加入队列")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("曲目", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
        }
        items(tracks, key = { it.id }) { track ->
            TrackListItem(
                track = track,
                onClick = { onPlayTrack(track) },
                onMore = { onMore(track) }
            )
        }
    }
}

@Composable
internal fun FavoritesScreen(
    favoriteTracks: List<Track>,
    contentPadding: PaddingValues,
    onPlay: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onMore: (Track) -> Unit
) {
    if (favoriteTracks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(modifier = Modifier.size(92.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("还没有收藏", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("从歌曲菜单或播放页点击爱心，即可在这里保存喜爱的音乐。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text("收藏", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text("${favoriteTracks.size} 首你喜欢的音乐", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onPlayAll) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("播放全部收藏")
                    }
                    OutlinedButton(onClick = onAddAllToQueue) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("加入队列")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            items(favoriteTracks, key = { it.id }) { track ->
                TrackListItem(track = track, onClick = { onPlay(track) }, onMore = { onMore(track) })
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(libraryUiState: LibraryUiState, onRequestPermission: () -> Unit, onRescan: () -> Unit) {
    val needsPermission = libraryUiState == LibraryUiState.PermissionRequired
    val isLoading = libraryUiState == LibraryUiState.Loading
    val title = when (libraryUiState) {
        LibraryUiState.PermissionRequired -> "需要读取音乐的权限"
        LibraryUiState.Loading -> "正在扫描设备音乐"
        is LibraryUiState.Error -> "暂时无法读取媒体库"
        else -> "这里会出现你的音乐"
    }
    val description = when (libraryUiState) {
        LibraryUiState.PermissionRequired -> "授权后，Muse 才能读取设备中的本地音频。"
        LibraryUiState.Loading -> "正在整理歌曲、专辑和封面信息，请稍候。"
        is LibraryUiState.Error -> libraryUiState.message
        else -> "将音频放入设备的 Music 文件夹后，点击扫描即可添加。"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(if (needsPermission) Icons.Default.Folder else Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isLoading) {
                Spacer(Modifier.height(18.dp))
                FilledTonalButton(onClick = if (needsPermission) onRequestPermission else onRescan) {
                    Text(if (needsPermission) "授权并扫描" else "重新扫描")
                }
            }
        }
    }
}

@Composable
private fun EmptyAlbumsCard() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("扫描音乐后，这里会显示带有专辑标签的作品。", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun TrackListItem(track: Track, onClick: () -> Unit, onMore: () -> Unit) {
    ListItem(
        overlineContent = {
            Text(
                if (track.isFeaturedAsset) "专题音频包" else "设备音乐",
                style = MaterialTheme.typography.labelSmall,
                color = if (track.isFeaturedAsset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${track.artist} · ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AlbumArt(Modifier.size(52.dp), track.artworkUri, track.title, ArtEmphasis.Secondary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (track.durationMs > 0L) track.durationLabel else "—:—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, contentDescription = "${track.title} 的歌曲选项") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionsSheet(
    track: Track,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(Modifier.size(56.dp), track.artworkUri, track.title, ArtEmphasis.Primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            ActionRow(Icons.Default.PlayArrow, "立即播放", onPlay)
            ActionRow(Icons.Default.SkipNext, "下一首播放", onPlayNext)
            ActionRow(Icons.Default.Add, "加入播放队列", onAddToQueue)
            ActionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "取消收藏" else "加入收藏", onToggleFavorite)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
    )
}

private const val HOME_PREVIEW_LIMIT = 6

private enum class SongSortMode(val label: String) {
    TITLE("标题"),
    ARTIST("艺人"),
    ALBUM("专辑"),
    DATE_ADDED("最近加入")
}

private enum class PlaybackStrategy(
    val label: String,
    val description: String,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val icon: ImageVector
) {
    SEQUENTIAL_LOOP("顺序循环", "按队列顺序播放，播完后从第一首继续", Player.REPEAT_MODE_ALL, false, Icons.Default.Repeat),
    RANDOM("随机播放", "随机选择队列中的歌曲，播完当前队列后停止", Player.REPEAT_MODE_OFF, true, Icons.Default.Shuffle),
    SINGLE_REPEAT("单曲循环", "重复播放当前歌曲", Player.REPEAT_MODE_ONE, false, Icons.Default.RepeatOne),
    NO_REPEAT("不循环", "按队列顺序播放，播完后停止", Player.REPEAT_MODE_OFF, false, Icons.Default.SkipNext)
}

private fun playbackStrategyFor(repeatMode: Int, shuffleEnabled: Boolean): PlaybackStrategy = when {
    shuffleEnabled -> PlaybackStrategy.RANDOM
    repeatMode == Player.REPEAT_MODE_ONE -> PlaybackStrategy.SINGLE_REPEAT
    repeatMode == Player.REPEAT_MODE_ALL -> PlaybackStrategy.SEQUENTIAL_LOOP
    else -> PlaybackStrategy.NO_REPEAT
}

private enum class ArtEmphasis { Primary, Secondary, Tertiary }

@Composable
private fun AlbumArt(modifier: Modifier, seed: String, emphasis: ArtEmphasis) {
    AlbumArt(modifier = modifier, artworkUri = null, seed = seed, emphasis = emphasis)
}

@Composable
private fun AlbumArt(modifier: Modifier, artworkUri: android.net.Uri?, seed: String, emphasis: ArtEmphasis) {
    val context = LocalContext.current
    val artworkRequest = remember(artworkUri) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(384)
                .memoryCacheKey("muse-artwork:${uri}")
                .diskCacheKey("muse-artwork:${uri}")
                .crossfade(false)
                .build()
        }
    }
    val palette = when (emphasis) {
        ArtEmphasis.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ArtEmphasis.Secondary -> when (seed.hashCode().absoluteValue % 3) {
            0 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        ArtEmphasis.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = palette.first) {
        Box(contentAlignment = Alignment.Center) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.muse_default_album_art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerWithProgress(
    viewModel: PlayerViewModel,
    track: Track,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    MiniPlayer(
        track = track,
        isPlaying = isPlaying,
        progress = progress,
        onOpen = onOpen,
        onOpenQueue = onOpenQueue,
        onToggle = viewModel::togglePlayback,
        onNext = viewModel::skipNext
    )
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(Modifier.size(48.dp), track.artworkUri, track.title, ArtEmphasis.Primary)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "播放或暂停") }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "下一首") }
                IconButton(onClick = onOpenQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "打开播放队列") }
            }
        }
    }
}

@Composable
private fun PlayerSheetWithProgress(
    viewModel: PlayerViewModel,
    track: Track,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    playbackSpeed: Float,
    mixingPlaybackEnabled: Boolean,
    fadeTransitionsEnabled: Boolean,
    sleepTimerRemainingMs: Long,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSetPlaybackStrategy: (PlaybackStrategy) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetMixingPlayback: (Boolean) -> Unit,
    onSetFadeTransitions: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddBookmark: () -> Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val program by viewModel.currentProgram.collectAsStateWithLifecycle()
    PlayerSheet(
        track = track,
        isPlaying = isPlaying,
        progress = progress,
        positionMs = positionMs,
        durationMs = durationMs,
        lyrics = lyrics,
        program = program,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        playbackSpeed = playbackSpeed,
        mixingPlaybackEnabled = mixingPlaybackEnabled,
        fadeTransitionsEnabled = fadeTransitionsEnabled,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        isFavorite = isFavorite,
        onDismiss = onDismiss,
        onToggle = onToggle,
        onNext = onNext,
        onPrevious = onPrevious,
        onSeek = onSeek,
        onSetPlaybackStrategy = onSetPlaybackStrategy,
        onSetPlaybackSpeed = onSetPlaybackSpeed,
        onSetMixingPlayback = onSetMixingPlayback,
        onSetFadeTransitions = onSetFadeTransitions,
        onSetSleepTimer = onSetSleepTimer,
        onCancelSleepTimer = onCancelSleepTimer,
        onAddBookmark = onAddBookmark,
        onToggleFavorite = onToggleFavorite,
        onOpenQueue = onOpenQueue
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    lyrics: List<LyricLine>,
    program: FeaturedTrackProgram?,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    playbackSpeed: Float,
    mixingPlaybackEnabled: Boolean,
    fadeTransitionsEnabled: Boolean,
    sleepTimerRemainingMs: Long,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSetPlaybackStrategy: (PlaybackStrategy) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetMixingPlayback: (Boolean) -> Unit,
    onSetFadeTransitions: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddBookmark: () -> Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var strategyMenuExpanded by remember { mutableStateOf(false) }
    var programExpanded by remember(track.id) { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val sliderProgress = if (isSeeking) scrubProgress else progress
    val playbackStrategy = playbackStrategyFor(repeatMode, shuffleEnabled)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DynamicPlaybackAtmosphere(
                isPlaying = isPlaying,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD9121A31))
                    .padding(start = 28.dp, end = 28.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("正在播放", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(track.album, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E1F6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { speedMenuExpanded = true }) { Text("${formatSpeed(playbackSpeed)}×") }
                DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${formatSpeed(speed)}×") },
                            onClick = {
                                onSetPlaybackSpeed(speed)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
                IconButton(onClick = onOpenQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "打开播放队列") }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭播放页") }
            }
            Spacer(Modifier.height(20.dp))
            AlbumArt(
                modifier = if (lyrics.isEmpty()) {
                    Modifier.fillMaxWidth().aspectRatio(1f)
                } else {
                    Modifier.fillMaxWidth().height(224.dp)
                },
                artworkUri = track.artworkUri,
                seed = track.title,
                emphasis = ArtEmphasis.Primary
            )
            if (lyrics.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LyricsStage(lyrics = lyrics, positionMs = positionMs)
            }
            Spacer(Modifier.height(if (lyrics.isEmpty()) 28.dp else 18.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD6E1F6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { onAddBookmark() }) { Text("存书签") }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (isFavorite) "取消收藏" else "加入收藏", tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = sliderProgress,
                onValueChange = {
                    isSeeking = true
                    scrubProgress = it
                },
                onValueChangeFinished = {
                    onSeek(scrubProgress)
                    isSeeking = false
                },
                enabled = durationMs > 0L,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (durationMs > 0L) formatTime(if (isSeeking) (durationMs * scrubProgress).toLong() else positionMs) else "—:—",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD6E1F6)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (durationMs > 0L) formatTime(durationMs) else "正在读取时长",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD6E1F6)
                )
            }
            if (program?.hasContent == true) {
                Spacer(Modifier.height(16.dp))
                ProgramGuide(
                    program = program,
                    positionMs = positionMs,
                    expanded = programExpanded,
                    onToggleExpanded = { programExpanded = !programExpanded },
                    onChapterSelect = { timestampMs ->
                        if (durationMs > 0L) {
                            onSeek((timestampMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f))
                        }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "播放或暂停", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { strategyMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(playbackStrategy.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放方式：${playbackStrategy.label}")
                }
                DropdownMenu(
                    expanded = strategyMenuExpanded,
                    onDismissRequest = { strategyMenuExpanded = false }
                ) {
                    PlaybackStrategy.entries.forEach { strategy ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(strategy.icon, contentDescription = null) },
                            text = {
                                Column {
                                    Text(strategy.label)
                                    Text(
                                        strategy.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSetPlaybackStrategy(strategy)
                                strategyMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("声音处理", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onSetMixingPlayback(!mixingPlaybackEnabled) },
                    label = { Text(if (mixingPlaybackEnabled) "混音播放：开启" else "混音播放：关闭") },
                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (mixingPlaybackEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                AssistChip(
                    onClick = { onSetFadeTransitions(!fadeTransitionsEnabled) },
                    label = { Text(if (fadeTransitionsEnabled) "淡化：开启" else "淡化：关闭") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (fadeTransitionsEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (mixingPlaybackEnabled) "混音开启：Muse 不会抢占其他应用的音频焦点，可与其他声音同时播放。" else "独占播放：Muse 会按系统音频焦点规则协调其他应用。",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFD6E1F6)
            )
            Text(
                if (fadeTransitionsEnabled) "淡化开启：在应用内切歌、上一首、下一首和暂停时使用约 280ms 的淡入淡出。" else "淡化关闭：切歌与暂停立即执行。",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFD6E1F6)
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("睡眠定时", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (sleepTimerRemainingMs > 0L) "将在 ${formatTime(sleepTimerRemainingMs)} 后平滑暂停" else "未设置自动暂停",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFD6E1F6)
                    )
                }
                if (sleepTimerRemainingMs > 0L) {
                    TextButton(onClick = onCancelSleepTimer) { Text("关闭") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    AssistChip(onClick = { onSetSleepTimer(minutes) }, label = { Text("${minutes} 分钟") })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                playbackStrategy.description,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFD6E1F6)
            )
            }
        }
    }
}

@Composable
private fun ProgramGuide(
    program: FeaturedTrackProgram,
    positionMs: Long,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChapterSelect: (Long) -> Unit
) {
    val activeChapterIndex = program.chapters.indexOfLast { it.timestampMs <= positionMs }
    val activeChapter = activeChapterIndex.takeIf { it >= 0 }?.let(program.chapters::getOrNull)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0x3D26335C)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("章节与笔记", style = MaterialTheme.typography.titleSmall)
                    activeChapter?.let { chapter ->
                        Text(
                            "当前 · ${formatTime(chapter.timestampMs)}  ${chapter.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFD6E1F6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onToggleExpanded) { Text(if (expanded) "收起" else "展开") }
            }
            if (expanded) {
                program.chapters.forEachIndexed { index, chapter ->
                    TextButton(
                        onClick = { onChapterSelect(chapter.timestampMs) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${formatTime(chapter.timestampMs)}  ${chapter.title}",
                            style = if (index == activeChapterIndex) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            color = if (index == activeChapterIndex) Color.White else Color(0xFFBDCAE4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                program.notes.take(3).forEach { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6E1F6),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsStage(lyrics: List<LyricLine>, positionMs: Long) {
    val activeIndex = lyrics.indexOfLast { it.timestampMs <= positionMs }
    val anchorIndex = activeIndex.coerceAtLeast(0)
    val visibleLines = (anchorIndex - 1..anchorIndex + 1)
        .mapNotNull(lyrics::getOrNull)
        .distinctBy { it.timestampMs to it.text }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0x4526335C)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "离线逐行歌词",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFD6E1F6)
            )
            visibleLines.forEach { line ->
                val isActive = activeIndex >= 0 && line.timestampMs == lyrics.getOrNull(activeIndex)?.timestampMs
                Text(
                    line.text,
                    style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (isActive) Color.White else Color(0xFFBDCAE4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DynamicPlaybackAtmosphere(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val atmosphere = rememberInfiniteTransition(label = "playbackAtmosphere")
    val drift by atmosphere.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "atmosphereDrift"
    )
    val glow by atmosphere.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "atmosphereGlow"
    )
    val motion = if (isPlaying) drift else 0.5f
    val glowAlpha = if (isPlaying) glow else 0.26f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF071127))
    ) {
        Image(
            painter = painterResource(R.drawable.muse_playback_atmosphere),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.08f + motion * 0.05f
                    scaleY = 1.08f + motion * 0.05f
                    translationX = (motion - 0.5f) * 34f
                    translationY = (0.5f - motion) * 46f
                }
                .alpha(if (isPlaying) 0.94f else 0.72f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x73030718),
                        0.45f to Color(0x22030718),
                        1f to Color(0xB8070B18)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x443B8EFF).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<Track>,
    currentTrack: Track?,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemovePlayed: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(queue, currentTrack?.id) {
        queue.indexOfFirst { it.id == currentTrack?.id }
    }
    val upcomingTracks = remember(queue, currentIndex) {
        queue.drop(if (currentIndex >= 0) currentIndex + 1 else 0)
    }
    val upcomingDurationMs = remember(upcomingTracks) { upcomingTracks.sumOf { it.durationMs.coerceAtLeast(0L) } }
    val unresolvedUpcomingCount = remember(upcomingTracks) { upcomingTracks.count { it.durationMs <= 0L } }
    val queueSummary = remember(queue, currentIndex, upcomingTracks, upcomingDurationMs, unresolvedUpcomingCount) {
        if (queue.isEmpty()) {
            "队列为空"
        } else {
            buildString {
                append("${queue.size} 首歌曲")
                if (currentIndex >= 0) append(" · 正在播放第 ${currentIndex + 1} 首")
                if (upcomingTracks.isNotEmpty()) {
                    append(" · 接下来 ")
                    append(if (upcomingDurationMs > 0L) formatTime(upcomingDurationMs) else "正在读取时长")
                    if (unresolvedUpcomingCount > 0) append("（${unresolvedUpcomingCount} 首时长待解析）")
                }
            }
        }
    }
    LaunchedEffect(queue, currentTrack?.id) {
        if (currentIndex >= 0) {
            // 当前项上方保留分段标题，打开队列后直接落在正在播放区域。
            listState.scrollToItem(if (currentIndex > 0) currentIndex + 1 else 0)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("播放队列", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        queueSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (currentIndex > 0) TextButton(onClick = onRemovePlayed) { Text("移除已播") }
                if (queue.isNotEmpty()) TextButton(onClick = onClear) { Text("清空队列") }
            }
            Spacer(Modifier.height(8.dp))
            if (queue.isEmpty()) {
                Text("从歌曲菜单中选择“加入播放队列”，即可在这里管理播放顺序。", modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 26.dp)) {
                    if (currentIndex > 0) {
                        item(key = "queue_before_current") {
                            QueueSectionLabel("队列前序")
                        }
                        items(queue.take(currentIndex).withIndex().toList(), key = { "queue_before_${it.value.id}" }) { indexedTrack ->
                            QueueTrackItem(
                                index = indexedTrack.index,
                                item = indexedTrack.value,
                                isCurrent = false,
                                lastIndex = queue.lastIndex,
                                onPlay = onPlay,
                                onMove = onMove,
                                onRemove = onRemove
                            )
                        }
                    }
                    if (currentIndex >= 0) {
                        item(key = "queue_now_playing") {
                            QueueSectionLabel("正在播放")
                        }
                        item(key = "queue_current_${queue[currentIndex].id}") {
                            QueueTrackItem(
                                index = currentIndex,
                                item = queue[currentIndex],
                                isCurrent = true,
                                lastIndex = queue.lastIndex,
                                onPlay = onPlay,
                                onMove = onMove,
                                onRemove = onRemove
                            )
                        }
                    }
                    val nextStartIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                    if (nextStartIndex < queue.size) {
                        item(key = "queue_up_next") {
                            QueueSectionLabel(if (currentIndex >= 0) "接下来" else "播放队列")
                        }
                        items(queue.drop(nextStartIndex).withIndex().toList(), key = { "queue_next_${it.value.id}" }) { indexedTrack ->
                            QueueTrackItem(
                                index = nextStartIndex + indexedTrack.index,
                                item = indexedTrack.value,
                                isCurrent = false,
                                lastIndex = queue.lastIndex,
                                onPlay = onPlay,
                                onMove = onMove,
                                onRemove = onRemove
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp, start = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun QueueTrackItem(
    index: Int,
    item: Track,
    isCurrent: Boolean,
    lastIndex: Int,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AlbumArt(
                Modifier.size(48.dp),
                item.artworkUri,
                item.title,
                if (isCurrent) ArtEmphasis.Primary else ArtEmphasis.Secondary
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "上移") }
                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < lastIndex) { Icon(Icons.Default.ArrowDownward, "下移") }
                IconButton(onClick = { onRemove(index) }) { Icon(Icons.Default.Delete, "移出队列") }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPlay(index) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)) {
            Surface(modifier = Modifier.size(64.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Image(
                    painter = painterResource(R.drawable.ic_muse_brand_mark),
                    contentDescription = "Muse 图标",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Muse 本地音乐", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("一款以 Kotlin、Jetpack Compose 和 Media3 构建的专题音频与本地音乐播放器。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            AboutItem("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
            AboutItem("播放引擎", "AndroidX Media3 ExoPlayer")
            AboutItem("数据来源", "APK 内专题音频包与设备本地音频媒体库")
            AboutItem("开源许可", "MIT License")
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AboutItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSpeed(speed: Float): String = when (speed) {
    speed.toInt().toFloat() -> speed.toInt().toString()
    else -> speed.toString().trimEnd('0').trimEnd('.')
}

private fun formatTime(milliseconds: Long): String {
    val safeSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(safeSeconds / 60L, safeSeconds % 60L)
}

private fun repeatModeLabel(repeatMode: Int): String = when (repeatMode) {
    Player.REPEAT_MODE_ONE -> "单曲循环"
    Player.REPEAT_MODE_ALL -> "列表循环"
    else -> "不循环"
}

private fun libraryStatusTitle(state: LibraryUiState): String = when (state) {
    LibraryUiState.Idle -> "等待扫描设备音乐"
    LibraryUiState.Loading -> "正在扫描设备音乐"
    LibraryUiState.PermissionRequired -> "设备音乐需要访问权限"
    LibraryUiState.Empty -> "暂未发现音乐"
    is LibraryUiState.Ready -> "资料库已就绪"
    is LibraryUiState.Error -> "扫描遇到问题"
}

private fun libraryStatusDescription(state: LibraryUiState): String = when (state) {
    LibraryUiState.Idle -> "专题音频包无需权限；授权后还可读取设备中的本地音频。"
    LibraryUiState.Loading -> "正在读取歌曲、专辑和本地标签信息。"
    LibraryUiState.PermissionRequired -> "专题音频包可直接播放；授予权限后可额外扫描设备音乐。"
    LibraryUiState.Empty -> "请确认音频位于设备媒体库，或复制到 Music 文件夹后重试。"
    is LibraryUiState.Ready -> "已发现 ${state.trackCount} 首歌曲，按专辑信息自动整理。"
    is LibraryUiState.Error -> state.message
}
