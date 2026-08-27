package com.muse.localplayer.playback

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.muse.localplayer.data.ContentSearchKind
import com.muse.localplayer.data.ContentSearchResult
import com.muse.localplayer.data.FeaturedAudioPack
import com.muse.localplayer.data.FeaturedAudioRepository
import com.muse.localplayer.data.FeaturedPackMetadata
import com.muse.localplayer.data.FeaturedTrackProgram
import com.muse.localplayer.data.TrackSource
import com.muse.localplayer.data.LibraryLoadResult
import com.muse.localplayer.data.LyricLine
import com.muse.localplayer.data.LyricsRepository
import com.muse.localplayer.data.MediaStoreObserver
import com.muse.localplayer.data.MusicRepository
import com.muse.localplayer.data.PlaybackBookmark
import com.muse.localplayer.data.PlaybackResumeState
import com.muse.localplayer.data.Track
import com.muse.localplayer.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerFeedbackAction {
    RETRY_FAILED_TRACK,
    RESUME_PLAYBACK
}

data class PlayerFeedback(
    val text: String,
    val actionLabel: String? = null,
    val action: PlayerFeedbackAction? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    data class BookmarkItem(
        val bookmark: PlaybackBookmark,
        val track: Track
    )

    private data class RemovedQueueItem(
        val track: Track,
        val index: Int
    )

    data class FeaturedJourneyState(
        val completedTrackIds: Set<Long> = emptySet(),
        val resumeTrack: Track? = null,
        val resumePositionMs: Long = 0L,
        val completedCount: Int = 0,
        val totalCount: Int = 0,
        val nextTrack: Track? = null,
        val isComplete: Boolean = false
    )

    private val musicRepository = MusicRepository(application)
    private val featuredAudioRepository = FeaturedAudioRepository(application)
    private val lyricsRepository = LyricsRepository(application)
    private val mediaStoreObserver = MediaStoreObserver(application)
    private val preferencesRepository = UserPreferencesRepository(application)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _featuredTracks = MutableStateFlow<List<Track>>(emptyList())
    val featuredTracks = _featuredTracks.asStateFlow()

    private val _featuredJourney = MutableStateFlow(FeaturedJourneyState())
    val featuredJourney = _featuredJourney.asStateFlow()

    private val _featuredPackMetadata = MutableStateFlow(FeaturedPackMetadata())
    val featuredPackMetadata = _featuredPackMetadata.asStateFlow()

    private val _libraryUiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Idle)
    val libraryUiState = _libraryUiState.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _currentProgram = MutableStateFlow<FeaturedTrackProgram?>(null)
    val currentProgram = _currentProgram.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _playbackHistory = MutableStateFlow<List<Track>>(emptyList())
    val playbackHistory = _playbackHistory.asStateFlow()

    private val _bookmarkItems = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarkItems = _bookmarkItems.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Track>>(emptyList())
    val recentlyAdded = _recentlyAdded.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs = _sleepTimerRemainingMs.asStateFlow()

    private val _mixingPlaybackEnabled = MutableStateFlow(false)
    val mixingPlaybackEnabled = _mixingPlaybackEnabled.asStateFlow()

    private val _fadeTransitionsEnabled = MutableStateFlow(true)
    val fadeTransitionsEnabled = _fadeTransitionsEnabled.asStateFlow()

    private val _controllerReady = MutableStateFlow(false)
    val controllerReady = _controllerReady.asStateFlow()

    private val _playerMessage = MutableStateFlow<PlayerFeedback?>(null)
    val playerMessage = _playerMessage.asStateFlow()

    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val _audioOutputLabel = MutableStateFlow("正在识别输出设备")
    val audioOutputLabel = _audioOutputLabel.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var libraryScanJob: Job? = null
    private var mediaStoreObserverJob: Job? = null
    private var lyricsLoadJob: Job? = null
    private var lyricsTrackId: Long? = null
    private var pendingTrack: Track? = null
    private var restoredQueueIds: List<Long> = emptyList()
    private var resumeState = PlaybackResumeState(trackId = null, positionMs = 0L)
    private var featuredJourneyResumeState = PlaybackResumeState(trackId = null, positionMs = 0L)
    private var lastResumePersistenceAt = 0L
    private var lastFeaturedJourneyResumePersistenceAt = 0L
    private var cachedFeaturedPack: FeaturedAudioPack? = null
    private var featuredProgramsByTrackId: Map<Long, FeaturedTrackProgram> = emptyMap()
    private var trackIndex: Map<Long, Track> = emptyMap()
    private var tracksBySource: Map<TrackSource, List<Track>> = emptyMap()
    private var timelineTrackId: String? = null
    private var stableDurationMs: Long = 0L
    private var playbackHistoryIds: List<Long> = emptyList()
    private var savedBookmarks: List<PlaybackBookmark> = emptyList()
    private var completedFeaturedTrackIds: Set<Long> = emptySet()
    private var pendingBookmark: BookmarkItem? = null
    private var lastClearedQueue: List<Track> = emptyList()
    private var lastClearedCurrentTrack: Track? = null
    private var lastRemovedQueueItem: RemovedQueueItem? = null
    private var lastTrimmedQueueItems: List<Track> = emptyList()
    private var lastFailedTrack: Track? = null
    private val failedTrackIds = mutableSetOf<Long>()
    private val sessionToken = SessionToken(application, ComponentName(application, MusicPlaybackService::class.java))
    private val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = refreshAudioOutputLabel()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = refreshAudioOutputLabel()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                progressJob?.cancel()
                progressJob = null
                refreshPlaybackState(forcePersist = true)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady || _currentTrack.value == null) return
            val text = when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "其他音频已接管播放，Muse 已暂停。"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "音频设备已断开，Muse 已暂停以保护收听体验。"
                else -> return
            }
            _playerMessage.value = PlayerFeedback(
                text = text,
                actionLabel = "继续",
                action = PlayerFeedbackAction.RESUME_PLAYBACK
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resetTimelineFor(mediaItem)
            syncCurrentTrack(mediaItem)
            mediaItem?.mediaId?.toLongOrNull()?.let(::recordPlayback)
            refreshPlaybackState(forcePersist = true)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncQueueFromController(persist = true)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (_repeatMode.value == repeatMode) return
            _repeatMode.value = repeatMode
            viewModelScope.launch { preferencesRepository.saveRepeatMode(repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (_shuffleEnabled.value == shuffleModeEnabled) return
            _shuffleEnabled.value = shuffleModeEnabled
            viewModelScope.launch { preferencesRepository.saveShuffleEnabled(shuffleModeEnabled) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackSpeed.value = playbackParameters.speed
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // 成功播放下一首不应遗忘本轮已失败文件；否则随机/循环可能再次跳回坏文件。
            refreshPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            progressJob?.cancel()
            progressJob = null
            _isPlaying.value = false
            val failedTrack = controller?.currentMediaItem?.mediaId?.toLongOrNull()?.let(trackIndex::get)
            lastFailedTrack = failedTrack
            val recovered = controller?.let(::skipFailedTrack) == true
            refreshPlaybackState(forcePersist = true)
            val title = failedTrack?.title?.let { "《$it》" } ?: "当前音频"
            _playerMessage.value = PlayerFeedback(
                text = if (recovered) {
                    "$title 无法播放，已自动跳过并继续下一首。"
                } else {
                    "$title 无法播放。请确认文件仍在设备中且格式受支持。"
                },
                actionLabel = failedTrack?.let { "重试" },
                action = failedTrack?.let { PlayerFeedbackAction.RETRY_FAILED_TRACK }
            )
        }
    }

    init {
        runCatching {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }
        refreshAudioOutputLabel()
        observePreferences()
        reloadLibrary()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess { connectedController ->
                controller = connectedController
                connectedController.addListener(playerListener)
                _controllerReady.value = true
                connectedController.repeatMode = _repeatMode.value
                connectedController.shuffleModeEnabled = _shuffleEnabled.value
                connectedController.playbackParameters = PlaybackParameters(_playbackSpeed.value)
                applyAudioFocusPolicy(connectedController)
                syncCurrentTrack(connectedController.currentMediaItem)
                syncQueueFromController(persist = false)
                restoreSavedQueueIfPossible()
                pendingBookmark?.let {
                    pendingBookmark = null
                    playBookmark(it)
                } ?: pendingTrack?.let {
                    pendingTrack = null
                    play(it)
                }
            }.onFailure {
                _playerMessage.value = PlayerFeedback("播放器服务连接失败，请重新打开应用后重试。")
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.favoriteIds.collect { _favoriteIds.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.repeatMode.collect { savedMode ->
                _repeatMode.value = savedMode
                controller?.repeatMode = savedMode
            }
        }
        viewModelScope.launch {
            preferencesRepository.shuffleEnabled.collect { savedEnabled ->
                _shuffleEnabled.value = savedEnabled
                controller?.shuffleModeEnabled = savedEnabled
            }
        }
        viewModelScope.launch {
            preferencesRepository.playbackSpeed.collect { savedSpeed ->
                _playbackSpeed.value = savedSpeed
                controller?.playbackParameters = PlaybackParameters(savedSpeed)
            }
        }
        viewModelScope.launch {
            preferencesRepository.mixingPlaybackEnabled.collect { enabled ->
                _mixingPlaybackEnabled.value = enabled
                controller?.let(::applyAudioFocusPolicy)
            }
        }
        viewModelScope.launch {
            preferencesRepository.fadeTransitionsEnabled.collect { enabled ->
                _fadeTransitionsEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            preferencesRepository.playbackHistoryIds.collect { ids ->
                playbackHistoryIds = ids
                refreshPlaybackHistory()
            }
        }
        viewModelScope.launch {
            preferencesRepository.playbackBookmarks.collect { bookmarks ->
                savedBookmarks = bookmarks
                refreshBookmarks()
            }
        }
        viewModelScope.launch {
            preferencesRepository.featuredCompletedTrackIds.collect { completedIds ->
                completedFeaturedTrackIds = completedIds
                refreshFeaturedJourney()
            }
        }
        viewModelScope.launch {
            preferencesRepository.featuredJourneyResumeState.collect { savedResumeState ->
                featuredJourneyResumeState = savedResumeState
                refreshFeaturedJourney()
            }
        }
        viewModelScope.launch {
            preferencesRepository.sleepTimerEndEpochMs.collect(::scheduleSleepTimer)
        }
        viewModelScope.launch {
            preferencesRepository.queueIds.collect { savedQueueIds ->
                restoredQueueIds = savedQueueIds
                restoreSavedQueueIfPossible()
            }
        }
        viewModelScope.launch {
            preferencesRepository.playbackResumeState.collect { savedResumeState ->
                resumeState = savedResumeState
                restoreSavedQueueIfPossible()
            }
        }
    }

    fun reloadLibrary() {
        if (libraryScanJob?.isActive == true) return
        libraryScanJob = viewModelScope.launch {
            _libraryUiState.value = LibraryUiState.Loading
            val featuredPack = loadFeaturedPackIfNeeded()
            val featured = featuredPack.tracks
            featuredProgramsByTrackId = featuredPack.programsByTrackId
        _featuredTracks.value = featured
        refreshFeaturedJourney()
        _featuredPackMetadata.value = featuredPack.metadata
            when (val result = musicRepository.loadTracks()) {
                is LibraryLoadResult.Success -> {
                    updateTracks(featured, result.tracks)
                    _libraryUiState.value = if (result.tracks.isEmpty()) {
                        LibraryUiState.Empty
                    } else {
                        LibraryUiState.Ready(result.tracks.size)
                    }
                    syncCurrentTrack(controller?.currentMediaItem)
                    restoreSavedQueueIfPossible()
                    startMediaStoreObservation()
                }
                LibraryLoadResult.PermissionDenied -> {
                    stopMediaStoreObservation()
                    updateTracks(featured, emptyList())
                    _libraryUiState.value = LibraryUiState.PermissionRequired
                    syncCurrentTrack(controller?.currentMediaItem)
                    restoreSavedQueueIfPossible()
                }
                is LibraryLoadResult.Failed -> {
                    updateTracks(featured, emptyList())
                    _libraryUiState.value = LibraryUiState.Error(result.message)
                    syncCurrentTrack(controller?.currentMediaItem)
                    restoreSavedQueueIfPossible()
                }
            }
        }
    }

    private suspend fun loadFeaturedPackIfNeeded(): FeaturedAudioPack {
        cachedFeaturedPack?.let { return it }
        return runCatching { featuredAudioRepository.loadPack() }
            .getOrElse { FeaturedAudioPack() }
            .also { cachedFeaturedPack = it }
    }

    private fun updateTracks(featured: List<Track>, deviceTracks: List<Track>) {
        val previousQueue = _queue.value
        val allTracks = featured + deviceTracks
        trackIndex = allTracks.associateBy(Track::id)
        tracksBySource = allTracks.groupBy(Track::source)
        _tracks.value = allTracks
        _recentlyAdded.value = deviceTracks
            .sortedByDescending(Track::dateAddedSeconds)
            .take(8)
        reconcileQueueAfterLibraryUpdate(previousQueue)
        refreshPlaybackHistory()
        refreshBookmarks()
    }

    /**
     * A MediaStore refresh can invalidate device URIs while the app is alive. Remove only tracks
     * that no longer resolve in the refreshed index, and rebuild Media3's queue only when needed.
     */
    private fun reconcileQueueAfterLibraryUpdate(previousQueue: List<Track>) {
        if (previousQueue.isEmpty()) return
        val reconciledQueue = previousQueue.mapNotNull { trackIndex[it.id] }
        val removedCount = previousQueue.size - reconciledQueue.size
        if (removedCount == 0) {
            _currentTrack.value?.id?.let(trackIndex::get)?.let { _currentTrack.value = it }
            return
        }

        val activeController = controller
        val wasPlaying = activeController?.isPlaying == true
        val previousCurrentId = activeController?.currentMediaItem?.mediaId?.toLongOrNull()
            ?: _currentTrack.value?.id
        if (activeController != null && activeController.mediaItemCount > 0) {
            if (reconciledQueue.isEmpty()) {
                activeController.clearMediaItems()
                resetTimelineForTrackId(null)
                _currentTrack.value = null
                _currentProgram.value = null
                loadLyricsFor(null)
                _isPlaying.value = false
            } else {
                val resumeIndex = reconciledQueue.indexOfFirst { it.id == previousCurrentId }
                    .takeIf { it >= 0 }
                    ?: activeController.currentMediaItemIndex.coerceIn(0, reconciledQueue.lastIndex)
                val resumePosition = activeController.currentPosition.coerceAtLeast(0L)
                resetTimelineForTrackId(reconciledQueue[resumeIndex].id.toString())
                activeController.setMediaItems(
                    reconciledQueue.map { it.toMediaItem() },
                    resumeIndex,
                    resumePosition
                )
                activeController.prepare()
                if (wasPlaying) activeController.play()
                _currentTrack.value = reconciledQueue[resumeIndex]
            }
        }
        updateQueueState(reconciledQueue)
        _playerMessage.value = PlayerFeedback(
            if (reconciledQueue.isEmpty()) {
                "媒体库更新后，播放队列中的文件已不可用。"
            } else {
                "媒体库更新后，已从播放队列移除 ${removedCount} 首不可用音频。"
            }
        )
    }

    private fun startMediaStoreObservation() {
        if (mediaStoreObserverJob != null) return
        mediaStoreObserverJob = viewModelScope.launch {
            var isFirstSignal = true
            mediaStoreObserver.changes().collect {
                if (isFirstSignal) {
                    isFirstSignal = false
                } else {
                    reloadLibrary()
                }
            }
        }
    }

    private fun stopMediaStoreObservation() {
        mediaStoreObserverJob?.cancel()
        mediaStoreObserverJob = null
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (granted) reloadLibrary() else _libraryUiState.value = LibraryUiState.PermissionRequired
    }

    fun play(track: Track) {
        // 用户主动点按可重试此前失败的文件；若仍失败则继续沿用自动跳过保护。
        failedTrackIds.remove(track.id)
        val activeController = controller ?: run {
            pendingTrack = track
            return
        }
        val queueToPlay = _queue.value.ifEmpty {
            tracksBySource[track.source].orEmpty()
        }
        if (queueToPlay.none { it.id == track.id }) {
            setQueue(queueToPlay + track, track, shouldPlay = true)
            return
        }
        if (activeController.currentMediaItem?.mediaId != track.id.toString()) {
            val startIndex = queueToPlay.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            transitionToQueue(activeController, queueToPlay, startIndex)
        } else {
            startOrResume(activeController)
        }
        _currentTrack.value = track
        _isPlaying.value = true
    }

    /** Starts the packaged featured collection in its repository order, independent of any previous queue. */
    fun playFeaturedTracks() {
        val featuredQueue = _featuredTracks.value
        if (featuredQueue.isEmpty()) return
        setQueue(featuredQueue, featuredQueue.first(), shouldPlay = true)
    }

    fun continueFeaturedJourney() {
        val featuredQueue = _featuredTracks.value
        val journey = _featuredJourney.value
        val resumeTrack = journey.resumeTrack
        if (resumeTrack != null) {
            // 先固定完整专题队列，再复用按时间点播放路径，确保断点恢复不会退化为单曲播放。
            setQueue(featuredQueue, resumeTrack, shouldPlay = false)
            playTrackAtPosition(
                track = resumeTrack,
                positionMs = journey.resumePositionMs,
                feedback = "已从 ${formatTimestamp(journey.resumePositionMs)} 继续《${resumeTrack.title}》的专题旅程。"
            )
            return
        }
        val nextTrack = journey.nextTrack ?: featuredQueue.firstOrNull() ?: return
        setQueue(featuredQueue, nextTrack, shouldPlay = true)
    }

    fun restartFeaturedJourney() {
        val featuredQueue = _featuredTracks.value
        if (featuredQueue.isEmpty()) return
        // 先刷新内存状态，避免首页在 DataStore 流回写前短暂保留“已完成”。
        completedFeaturedTrackIds = emptySet()
        featuredJourneyResumeState = PlaybackResumeState(trackId = null, positionMs = 0L)
        refreshFeaturedJourney()
        viewModelScope.launch {
            preferencesRepository.clearFeaturedJourneyProgress()
            preferencesRepository.saveFeaturedJourneyResumeState(trackId = null, positionMs = 0L)
        }
        setQueue(featuredQueue, featuredQueue.first(), shouldPlay = true)
    }

    fun playQueueItem(index: Int) {
        val activeController = controller ?: return
        val currentQueue = _queue.value
        if (index !in currentQueue.indices) return
        failedTrackIds.remove(currentQueue[index].id)
        if (activeController.mediaItemCount == 0) {
            transitionToQueue(activeController, currentQueue, index)
            return
        }
        val switchItem = {
            activeController.seekToDefaultPosition(index)
            startOrResume(activeController, forceFadeIn = true)
        }
        if (activeController.isPlaying && _fadeTransitionsEnabled.value) {
            fadeOutThen(activeController, switchItem)
        } else {
            switchItem()
        }
    }

    fun playAlbum(albumTracks: List<Track>, startTrack: Track? = albumTracks.firstOrNull()) {
        val orderedTracks = albumTracks
            .distinctBy(Track::id)
            .sortedWith(
                compareBy<Track> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        if (orderedTracks.isEmpty()) return
        val selectedTrack = startTrack?.takeIf { candidate -> orderedTracks.any { it.id == candidate.id } }
            ?: orderedTracks.first()
        setQueue(orderedTracks, selectedTrack, shouldPlay = true)
    }

    fun setQueue(tracks: List<Track>, startTrack: Track? = tracks.firstOrNull(), shouldPlay: Boolean = false) {
        val cleanedQueue = tracks.distinctBy { it.id }
        val activeController = controller
        if (activeController == null) {
            updateQueueState(cleanedQueue)
            return
        }
        if (cleanedQueue.isEmpty()) {
            activeController.clearMediaItems()
            updateQueueState(emptyList())
            return
        }
        val startIndex = cleanedQueue.indexOfFirst { it.id == startTrack?.id }.coerceAtLeast(0)
        if (shouldPlay) {
            transitionToQueue(activeController, cleanedQueue, startIndex)
        } else {
            resetTimelineForTrackId(cleanedQueue[startIndex].id.toString())
            activeController.setMediaItems(cleanedQueue.map { it.toMediaItem() }, startIndex, 0L)
            activeController.prepare()
            updateQueueState(cleanedQueue)
        }
    }

    fun addToQueue(track: Track): Int = addTracksToQueue(listOf(track))

    fun addTracksToQueue(tracks: List<Track>): Int {
        val additions = tracks.distinctBy(Track::id).filter { candidate ->
            _queue.value.none { it.id == candidate.id }
        }
        if (additions.isEmpty()) return 0
        val activeController = controller
        if (activeController == null || activeController.mediaItemCount == 0) {
            updateQueueState(_queue.value + additions)
        } else {
            activeController.addMediaItems(additions.map { it.toMediaItem() })
            updateQueueState(_queue.value + additions)
        }
        return additions.size
    }

    fun playNext(track: Track) {
        val activeController = controller
        val currentQueue = _queue.value
        if (activeController == null || activeController.mediaItemCount == 0 || currentQueue.isEmpty()) {
            setQueue(listOf(track), track, shouldPlay = false)
            return
        }
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            moveQueueItem(
                existingIndex,
                (activeController.currentMediaItemIndex + 1).coerceAtMost(currentQueue.lastIndex)
            )
            return
        }
        val insertIndex = (activeController.currentMediaItemIndex + 1).coerceIn(0, currentQueue.size)
        activeController.addMediaItem(insertIndex, track.toMediaItem())
        updateQueueState(currentQueue.toMutableList().also { it.add(insertIndex, track) })
    }

    fun removeFromQueue(index: Int): Track? {
        val currentQueue = _queue.value
        if (index !in currentQueue.indices) return null
        val removedTrack = currentQueue[index]
        controller?.removeMediaItem(index)
        updateQueueState(currentQueue.toMutableList().also { it.removeAt(index) })
        lastRemovedQueueItem = RemovedQueueItem(removedTrack, index)
        lastTrimmedQueueItems = emptyList()
        return removedTrack
    }

    /** Restores the most recently removed queue item during the short UI undo window. */
    fun restoreLastRemovedQueueItem(): Boolean {
        val removedItem = lastRemovedQueueItem ?: return false
        val currentQueue = _queue.value
        if (currentQueue.any { it.id == removedItem.track.id }) return false
        val insertIndex = removedItem.index.coerceIn(0, currentQueue.size)
        val restoredQueue = currentQueue.toMutableList().also { it.add(insertIndex, removedItem.track) }
        val activeController = controller
        if (activeController != null && activeController.mediaItemCount > 0) {
            activeController.addMediaItem(insertIndex, removedItem.track.toMediaItem())
        }
        updateQueueState(restoredQueue)
        lastRemovedQueueItem = null
        return true
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.value.indices || toIndex !in _queue.value.indices || fromIndex == toIndex) return
        controller?.moveMediaItem(fromIndex, toIndex)
        val updatedQueue = _queue.value.toMutableList()
        val item = updatedQueue.removeAt(fromIndex)
        updatedQueue.add(toIndex, item)
        updateQueueState(updatedQueue)
        lastRemovedQueueItem = null
        lastTrimmedQueueItems = emptyList()
    }

    /** Removes only entries before the active item, keeping the current song and all upcoming songs intact. */
    fun removePlayedQueueItems(): Int {
        val currentQueue = _queue.value
        val controllerIndex = controller?.currentMediaItemIndex ?: C.INDEX_UNSET
        val currentIndex = controllerIndex.takeIf { it in currentQueue.indices }
            ?: currentQueue.indexOfFirst { it.id == _currentTrack.value?.id }
        if (currentIndex <= 0) return 0
        lastTrimmedQueueItems = currentQueue.take(currentIndex)
        controller?.removeMediaItems(0, currentIndex)
        updateQueueState(currentQueue.drop(currentIndex))
        lastRemovedQueueItem = null
        return lastTrimmedQueueItems.size
    }

    /** Restores entries removed by [removePlayedQueueItems] during the short UI undo window. */
    fun restoreLastTrimmedQueueItems(): Boolean {
        val trimmedItems = lastTrimmedQueueItems
        if (trimmedItems.isEmpty()) return false
        val currentQueue = _queue.value
        val activeController = controller
        if (activeController != null && activeController.mediaItemCount > 0) {
            activeController.addMediaItems(0, trimmedItems.map { it.toMediaItem() })
        }
        updateQueueState(trimmedItems + currentQueue)
        lastTrimmedQueueItems = emptyList()
        return true
    }

    fun clearQueue() {
        lastClearedQueue = _queue.value
        lastClearedCurrentTrack = _currentTrack.value
        lastRemovedQueueItem = null
        lastTrimmedQueueItems = emptyList()
        controller?.clearMediaItems()
        _currentTrack.value = null
        _currentProgram.value = null
        loadLyricsFor(null)
        _isPlaying.value = false
        resetTimelineForTrackId(null)
        updateQueueState(emptyList())
        viewModelScope.launch { preferencesRepository.savePlaybackResumeState(null, 0L) }
    }

    fun restoreLastClearedQueue(): Boolean {
        val savedQueue = lastClearedQueue
        if (savedQueue.isEmpty()) return false
        val startTrack = lastClearedCurrentTrack?.takeIf { current -> savedQueue.any { it.id == current.id } }
            ?: savedQueue.first()
        setQueue(savedQueue, startTrack, shouldPlay = false)
        lastClearedQueue = emptyList()
        lastClearedCurrentTrack = null
        return true
    }

    fun togglePlayback() {
        controller?.let { activeController ->
            if (activeController.isPlaying) {
                fadeOutThen(activeController) { activeController.pause() }
            } else {
                activeController.volume = if (_fadeTransitionsEnabled.value) 0f else DEFAULT_PLAYER_VOLUME
                activeController.play()
                fadeIn(activeController)
            }
        }
    }

    fun skipNext() {
        controller?.let { activeController ->
            fadeOutThen(activeController) {
                activeController.seekToNextMediaItem()
                fadeIn(activeController)
            }
        }
    }

    fun skipPrevious() {
        controller?.let { activeController ->
            fadeOutThen(activeController) {
                if (activeController.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                    activeController.seekTo(0L)
                } else {
                    activeController.seekToPreviousMediaItem()
                }
                fadeIn(activeController)
            }
        }
    }

    fun dismissPlayerMessage() {
        _playerMessage.value = null
    }

    /** Retries the most recently failed track without weakening automatic skip protection for other files. */
    fun resumePlaybackFromInterruption(): Boolean {
        val activeController = controller ?: return false
        if (activeController.mediaItemCount == 0 || activeController.currentMediaItem == null) return false
        startOrResume(activeController, forceFadeIn = true)
        return true
    }

    fun retryLastFailedTrack(): Boolean {
        val track = lastFailedTrack ?: return false
        failedTrackIds.remove(track.id)
        val activeController = controller
        if (activeController != null && activeController.currentMediaItem?.mediaId == track.id.toString()) {
            // 单曲或队列已耗尽时，控制器仍可能停在错误媒体项；必须重新 prepare 才能真正重试。
            resetTimelineForTrackId(track.id.toString())
            activeController.prepare()
            startOrResume(activeController, forceFadeIn = true)
            _currentTrack.value = track
            _isPlaying.value = true
        } else {
            play(track)
        }
        _playerMessage.value = PlayerFeedback("正在重新尝试《${track.title}》。")
        return true
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L)))
        refreshPlaybackState()
    }

    fun seekToProgress(progress: Float) {
        seekTo((progress.coerceIn(0f, 1f) * _durationMs.value).toLong())
    }

    fun setPlaybackStrategy(repeatMode: Int, shuffleEnabled: Boolean) {
        val safeRepeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL -> repeatMode
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = safeRepeatMode
        _shuffleEnabled.value = shuffleEnabled
        controller?.apply {
            this.repeatMode = safeRepeatMode
            this.shuffleModeEnabled = shuffleEnabled
        }
        viewModelScope.launch {
            preferencesRepository.saveRepeatMode(safeRepeatMode)
            preferencesRepository.saveShuffleEnabled(shuffleEnabled)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        controller?.playbackParameters = PlaybackParameters(safeSpeed)
        _playbackSpeed.value = safeSpeed
        viewModelScope.launch { preferencesRepository.savePlaybackSpeed(safeSpeed) }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val endEpochMs = System.currentTimeMillis() + minutes.coerceIn(1, MAX_SLEEP_TIMER_MINUTES) * 60_000L
        scheduleSleepTimer(endEpochMs)
        viewModelScope.launch { preferencesRepository.saveSleepTimerEndEpochMs(endEpochMs) }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = 0L
        viewModelScope.launch { preferencesRepository.saveSleepTimerEndEpochMs(0L) }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch { preferencesRepository.clearPlaybackHistory() }
    }

    fun addBookmark(): Boolean {
        val track = _currentTrack.value ?: return false
        val position = _positionMs.value.coerceAtLeast(0L)
        viewModelScope.launch { preferencesRepository.addPlaybackBookmark(track.id, position) }
        _playerMessage.value = PlayerFeedback("已保存《${track.title}》的 ${formatTimestamp(position)} 书签。")
        return true
    }

    fun removeBookmark(item: BookmarkItem) {
        viewModelScope.launch { preferencesRepository.removePlaybackBookmark(item.bookmark) }
    }

    fun playBookmark(item: BookmarkItem) {
        playTrackAtPosition(
            track = item.track,
            positionMs = item.bookmark.positionMs,
            feedback = "已从 ${formatTimestamp(item.bookmark.positionMs)} 继续《${item.track.title}》。"
        )
    }

    fun playSearchResult(result: ContentSearchResult) {
        result.bookmark?.let { bookmark ->
            playBookmark(BookmarkItem(bookmark, result.track))
            return
        }
        val feedback = if (result.kind == ContentSearchKind.TRACK) {
            "正在播放《${result.track.title}》。"
        } else {
            "已定位到《${result.track.title}》的 ${formatTimestamp(result.positionMs)}。"
        }
        playTrackAtPosition(result.track, result.positionMs, feedback)
    }

    private fun playTrackAtPosition(track: Track, positionMs: Long, feedback: String) {
        failedTrackIds.remove(track.id)
        val activeController = controller ?: run {
            pendingBookmark = BookmarkItem(
                bookmark = PlaybackBookmark(track.id, positionMs.coerceAtLeast(0L), System.currentTimeMillis()),
                track = track
            )
            return
        }
        val queueToPlay = _queue.value.ifEmpty { tracksBySource[track.source].orEmpty() }
        val targetQueue = if (queueToPlay.any { it.id == track.id }) queueToPlay else queueToPlay + track
        val targetIndex = targetQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        if (activeController.currentMediaItem?.mediaId == track.id.toString()) {
            activeController.seekTo(positionMs.coerceAtLeast(0L))
            startOrResume(activeController, forceFadeIn = true)
        } else {
            transitionToQueue(activeController, targetQueue, targetIndex, positionMs)
        }
        _currentTrack.value = track
        _isPlaying.value = true
        _playerMessage.value = PlayerFeedback(feedback)
    }

    fun setMixingPlaybackEnabled(enabled: Boolean) {
        _mixingPlaybackEnabled.value = enabled
        controller?.let(::applyAudioFocusPolicy)
        viewModelScope.launch { preferencesRepository.saveMixingPlaybackEnabled(enabled) }
    }

    fun setFadeTransitionsEnabled(enabled: Boolean) {
        _fadeTransitionsEnabled.value = enabled
        if (!enabled) {
            fadeJob?.cancel()
            fadeJob = null
            controller?.volume = DEFAULT_PLAYER_VOLUME
        }
        viewModelScope.launch { preferencesRepository.saveFadeTransitionsEnabled(enabled) }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { preferencesRepository.toggleFavorite(track.id) }
    }

    fun isFavorite(track: Track): Boolean = track.id in _favoriteIds.value

    private fun recordPlayback(trackId: Long) {
        viewModelScope.launch { preferencesRepository.recordPlayback(trackId) }
    }

    private fun refreshPlaybackHistory() {
        _playbackHistory.value = playbackHistoryIds.mapNotNull(trackIndex::get)
    }

    private fun refreshFeaturedJourney() {
        val featured = _featuredTracks.value
        val completed = completedFeaturedTrackIds.intersect(featured.map(Track::id).toSet())
        val nextTrack = featured.firstOrNull { it.id !in completed }
        val savedResumeTrack = featuredJourneyResumeState.trackId
            ?.takeIf { it !in completed }
            ?.let { trackId -> featured.firstOrNull { it.id == trackId } }
            ?.takeIf { it.id == nextTrack?.id }
            ?.takeIf { featuredJourneyResumeState.positionMs >= FEATURED_JOURNEY_RESUME_MIN_POSITION_MS }
            ?.takeIf { track ->
                track.durationMs <= 0L || featuredJourneyResumeState.positionMs < track.durationMs - RESUME_END_TOLERANCE_MS
            }
        _featuredJourney.value = FeaturedJourneyState(
            completedTrackIds = completed,
            resumeTrack = savedResumeTrack,
            resumePositionMs = savedResumeTrack?.let { featuredJourneyResumeState.positionMs } ?: 0L,
            completedCount = completed.size,
            totalCount = featured.size,
            nextTrack = nextTrack,
            isComplete = featured.isNotEmpty() && completed.size == featured.size
        )
    }

    private fun refreshBookmarks() {
        val resolved = savedBookmarks.mapNotNull { bookmark ->
            trackIndex[bookmark.trackId]?.let { track -> BookmarkItem(bookmark, track) }
        }
        _bookmarkItems.value = resolved
        if (resolved.size != savedBookmarks.size) {
            savedBookmarks.filter { bookmark -> resolved.none { it.bookmark == bookmark } }
                .forEach { stale -> viewModelScope.launch { preferencesRepository.removePlaybackBookmark(stale) } }
        }
    }

    suspend fun searchContent(rawQuery: String): List<ContentSearchResult> {
        val query = rawQuery.trim()
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<ContentSearchResult>()
        _tracks.value.forEach { track ->
            if (matchesQuery(query, track.title, track.artist, track.album)) {
                results += ContentSearchResult(
                    kind = ContentSearchKind.TRACK,
                    track = track,
                    title = track.title,
                    supportingText = "${track.artist} · ${track.album}"
                )
            }
        }
        featuredProgramsByTrackId.forEach { (trackId, program) ->
            val track = trackIndex[trackId] ?: return@forEach
            program.chapters.filter { chapter -> matchesQuery(query, chapter.title, track.title) }
                .forEach { chapter ->
                    results += ContentSearchResult(
                        kind = ContentSearchKind.CHAPTER,
                        track = track,
                        title = chapter.title,
                        supportingText = "章节 · ${track.title} · ${formatTimestamp(chapter.timestampMs)}",
                        positionMs = chapter.timestampMs
                    )
                }
            program.notes.filter { note -> matchesQuery(query, note, track.title) }
                .forEach { note ->
                    results += ContentSearchResult(
                        kind = ContentSearchKind.NOTE,
                        track = track,
                        title = note,
                        supportingText = "节目笔记 · ${track.title}"
                    )
                }
        }
        _featuredTracks.value.forEach { track ->
            lyricsRepository.loadFeaturedLyrics(track)
                .filter { line -> matchesQuery(query, line.text, track.title) }
                .forEach { line ->
                    results += ContentSearchResult(
                        kind = ContentSearchKind.LYRIC,
                        track = track,
                        title = line.text,
                        supportingText = "歌词 · ${track.title} · ${formatTimestamp(line.timestampMs)}",
                        positionMs = line.timestampMs
                    )
                }
        }
        _bookmarkItems.value.filter { item ->
            matchesQuery(query, "书签", item.track.title, item.track.artist, formatTimestamp(item.bookmark.positionMs))
        }.forEach { item ->
            results += ContentSearchResult(
                kind = ContentSearchKind.BOOKMARK,
                track = item.track,
                title = item.track.title,
                supportingText = "书签 · ${formatTimestamp(item.bookmark.positionMs)} · ${item.track.artist}",
                positionMs = item.bookmark.positionMs,
                bookmark = item.bookmark
            )
        }
        return results
            .distinctBy { "${it.kind}:${it.track.id}:${it.positionMs}:${it.title}" }
            .sortedWith(compareBy<ContentSearchResult> { it.kind.ordinal }.thenBy { it.title.lowercase() })
            .take(80)
    }

    private fun matchesQuery(query: String, vararg candidates: String): Boolean =
        candidates.any { it.contains(query, ignoreCase = true) }

    private fun scheduleSleepTimer(endEpochMs: Long) {
        sleepTimerJob?.cancel()
        if (endEpochMs <= System.currentTimeMillis()) {
            _sleepTimerRemainingMs.value = 0L
            if (endEpochMs > 0L) {
                controller?.let { activeController ->
                    fadeOutThen(activeController) { activeController.pause() }
                }
                _playerMessage.value = PlayerFeedback("睡眠定时已结束，已暂停播放。")
                viewModelScope.launch { preferencesRepository.saveSleepTimerEndEpochMs(0L) }
            }
            return
        }
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (endEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
                if (remaining == 0L) {
                    controller?.let { activeController ->
                        fadeOutThen(activeController) { activeController.pause() }
                    }
                    _playerMessage.value = PlayerFeedback("睡眠定时已结束，已暂停播放。")
                    preferencesRepository.saveSleepTimerEndEpochMs(0L)
                    break
                }
                delay(minOf(SLEEP_TIMER_TICK_MS, remaining))
            }
        }
    }

    /**
     * 本地库可能在扫描后被删除、移动，或包含设备解码器不支持的文件。
     * 保留已失败曲目集合，避免在循环/随机策略下反复卡在同一个坏文件。
     */
    private fun skipFailedTrack(activeController: MediaController): Boolean {
        val failedTrackId = activeController.currentMediaItem?.mediaId?.toLongOrNull() ?: return false
        failedTrackIds += failedTrackId
        if (failedTrackIds.size >= activeController.mediaItemCount) return false

        // 使用 Media3 当前的顺序、随机和循环导航规则逐个前进，跳过已失败项目。
        repeat(activeController.mediaItemCount - 1) {
            if (!activeController.hasNextMediaItem()) return false
            activeController.seekToNextMediaItem()
            val candidateId = activeController.currentMediaItem?.mediaId?.toLongOrNull() ?: return@repeat
            if (candidateId !in failedTrackIds) {
                activeController.prepare()
                startOrResume(activeController, forceFadeIn = true)
                return true
            }
        }
        return false
    }

    private fun transitionToQueue(
        activeController: MediaController,
        queue: List<Track>,
        startIndex: Int,
        startPositionMs: Long = 0L
    ) {
        // 新的显式播放上下文应重新允许各文件被尝试一次。
        failedTrackIds.clear()
        val replaceAndPlay = {
            resetTimelineForTrackId(queue[startIndex].id.toString())
            activeController.setMediaItems(queue.map { it.toMediaItem() }, startIndex, startPositionMs.coerceAtLeast(0L))
            activeController.prepare()
            updateQueueState(queue)
            startOrResume(activeController, forceFadeIn = true)
        }
        if (activeController.isPlaying && _fadeTransitionsEnabled.value) {
            fadeOutThen(activeController, replaceAndPlay)
        } else {
            replaceAndPlay()
        }
    }

    private fun startOrResume(activeController: MediaController, forceFadeIn: Boolean = false) {
        val shouldFadeIn = _fadeTransitionsEnabled.value && (forceFadeIn || !activeController.isPlaying)
        if (shouldFadeIn) activeController.volume = 0f
        activeController.play()
        if (shouldFadeIn) fadeIn(activeController)
    }

    private fun applyAudioFocusPolicy(activeController: MediaController) {
        activeController.setAudioAttributes(MUSIC_AUDIO_ATTRIBUTES, !_mixingPlaybackEnabled.value)
    }

    private fun fadeOutThen(activeController: MediaController, action: () -> Unit) {
        if (!_fadeTransitionsEnabled.value || !activeController.isPlaying) {
            action()
            return
        }
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            animateVolume(activeController, activeController.volume, 0f)
            fadeJob = null
            action()
        }
    }

    private fun fadeIn(activeController: MediaController) {
        if (!_fadeTransitionsEnabled.value) {
            activeController.volume = DEFAULT_PLAYER_VOLUME
            return
        }
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            delay(80L)
            animateVolume(activeController, activeController.volume, DEFAULT_PLAYER_VOLUME)
        }
    }

    private suspend fun animateVolume(activeController: MediaController, from: Float, to: Float) {
        val start = from.coerceIn(0f, DEFAULT_PLAYER_VOLUME)
        repeat(FADE_STEPS) { step ->
            val progress = (step + 1).toFloat() / FADE_STEPS
            activeController.volume = start + (to - start) * progress
            delay(FADE_STEP_DELAY_MS)
        }
        activeController.volume = to
    }

    private fun restoreSavedQueueIfPossible() {
        val activeController = controller ?: return
        if (_tracks.value.isEmpty() || activeController.mediaItemCount > 0) return
        val restoredQueue = restoredQueueIds.mapNotNull(trackIndex::get)
            .ifEmpty {
                resumeState.trackId?.let(trackIndex::get)?.let(::listOf).orEmpty()
            }
        if (restoredQueue.isEmpty()) return

        val resumeIndex = restoredQueue.indexOfFirst { it.id == resumeState.trackId }.coerceAtLeast(0)
        val resumePosition = if (restoredQueue.getOrNull(resumeIndex)?.id == resumeState.trackId) {
            resumeState.positionMs.coerceAtLeast(0L)
        } else 0L
        resetTimelineForTrackId(restoredQueue[resumeIndex].id.toString())
        activeController.setMediaItems(restoredQueue.map { it.toMediaItem() }, resumeIndex, resumePosition)
        activeController.prepare()
        val savedQueueIsStale = restoredQueue.map { it.id } != restoredQueueIds
        updateQueueState(restoredQueue, persist = savedQueueIsStale)
        val restoredTrack = restoredQueue.getOrNull(resumeIndex)
        _currentTrack.value = restoredTrack
        _currentProgram.value = restoredTrack?.id?.let(featuredProgramsByTrackId::get)
        loadLyricsFor(restoredTrack)
        refreshPlaybackState()
    }

    private fun syncCurrentTrack(mediaItem: MediaItem?) {
        val track = mediaItem?.mediaId?.toLongOrNull()?.let(trackIndex::get)
        _currentTrack.value = track
        _currentProgram.value = track?.id?.let(featuredProgramsByTrackId::get)
        loadLyricsFor(track)
    }

    private fun loadLyricsFor(track: Track?) {
        if (lyricsTrackId == track?.id) return
        lyricsTrackId = track?.id
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        _currentLyrics.value = emptyList()
        if (track == null || !track.isFeaturedAsset) return
        lyricsLoadJob = viewModelScope.launch {
            _currentLyrics.value = lyricsRepository.loadFeaturedLyrics(track)
        }
    }

    private fun syncQueueFromController(persist: Boolean) {
        val activeController = controller ?: return
        val currentQueue = (0 until activeController.mediaItemCount).mapNotNull { index ->
            activeController.getMediaItemAt(index).mediaId.toLongOrNull()?.let(trackIndex::get)
        }
        if (currentQueue.isNotEmpty() || activeController.mediaItemCount == 0) {
            updateQueueState(currentQueue, persist)
        }
    }

    private fun updateQueueState(newQueue: List<Track>, persist: Boolean = true) {
        val cleanedQueue = newQueue.distinctBy { it.id }
        if (_queue.value.map(Track::id) == cleanedQueue.map(Track::id)) return
        _queue.value = cleanedQueue
        if (persist) {
            viewModelScope.launch { preferencesRepository.saveQueue(cleanedQueue.map { it.id }) }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive && controller?.isPlaying == true) {
                refreshPlaybackState()
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun resetTimelineFor(mediaItem: MediaItem?) {
        resetTimelineForTrackId(mediaItem?.mediaId)
    }

    private fun resetTimelineForTrackId(trackId: String?) {
        if (timelineTrackId == trackId) return
        timelineTrackId = trackId
        stableDurationMs = 0L
        _durationMs.value = 0L
        _positionMs.value = 0L
        _playbackProgress.value = 0f
    }

    private fun refreshPlaybackState(forcePersist: Boolean = false) {
        controller?.let { activeController ->
            resetTimelineFor(activeController.currentMediaItem)
            val reportedDuration = activeController.duration
            if (
                activeController.playbackState == Player.STATE_READY &&
                reportedDuration != C.TIME_UNSET &&
                reportedDuration >= MIN_VALID_DURATION_MS
            ) {
                stableDurationMs = reportedDuration
            }

            val position = activeController.currentPosition
                .takeIf { it != C.TIME_UNSET && it >= 0L }
                ?: 0L
            val duration = stableDurationMs
            if (duration > 0L) updateResolvedDuration(duration)
            val boundedPosition = if (duration > 0L) position.coerceAtMost(duration) else position
            _durationMs.value = duration
            _positionMs.value = boundedPosition
            _playbackProgress.value = if (duration > 0L) {
                (boundedPosition.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val currentTrack = _currentTrack.value
            if (
                currentTrack?.isFeaturedAsset == true &&
                duration > 0L &&
                boundedPosition >= duration - FEATURED_COMPLETION_TOLERANCE_MS &&
                currentTrack.id !in completedFeaturedTrackIds
            ) {
                // 在持久化前即时更新，避免进度轮询在 DataStore 发射前重复排队写入同一曲目。
                completedFeaturedTrackIds = completedFeaturedTrackIds + currentTrack.id
                refreshFeaturedJourney()
                viewModelScope.launch { preferencesRepository.markFeaturedTrackCompleted(currentTrack.id) }
            }
            persistResumeState(forcePersist)
        }
    }

    /** Writes Media3's decoder-resolved duration back to UI models once it becomes available. */
    private fun updateResolvedDuration(durationMs: Long) {
        val trackId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val current = trackIndex[trackId] ?: return
        if (current.durationMs == durationMs) return

        val updated = current.copy(durationMs = durationMs)
        trackIndex = trackIndex + (trackId to updated)
        _tracks.value = _tracks.value.map { if (it.id == trackId) updated else it }
        _featuredTracks.value = _featuredTracks.value.map { if (it.id == trackId) updated else it }
        _queue.value = _queue.value.map { if (it.id == trackId) updated else it }
        if (_currentTrack.value?.id == trackId) _currentTrack.value = updated
        tracksBySource = _tracks.value.groupBy(Track::source)
    }

    private fun refreshAudioOutputLabel() {
        val devices = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        _audioOutputLabel.value = when {
            devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } -> "蓝牙音频设备"
            devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "有线耳机"
            devices.any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY } -> "USB 音频设备"
            else -> "手机扬声器"
        }
    }

    private fun formatTimestamp(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun persistResumeState(force: Boolean) {
        val trackId = _currentTrack.value?.id ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastResumePersistenceAt < RESUME_PERSIST_INTERVAL_MS) return
        lastResumePersistenceAt = now
        val duration = _durationMs.value
        val rawPosition = _positionMs.value
        // 避免在一首歌刚播放完时将“结尾位置”持久化，重启后误触发立即结束。
        val position = if (
            duration > 0L && rawPosition >= duration - RESUME_END_TOLERANCE_MS
        ) {
            0L
        } else {
            rawPosition
        }
        resumeState = PlaybackResumeState(trackId, position)
        viewModelScope.launch { preferencesRepository.savePlaybackResumeState(trackId, position) }
        persistFeaturedJourneyResumeState(trackId, position, force)
    }

    private fun persistFeaturedJourneyResumeState(trackId: Long, positionMs: Long, force: Boolean) {
        val track = trackIndex[trackId] ?: return
        if (!track.isFeaturedAsset) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastFeaturedJourneyResumePersistenceAt < RESUME_PERSIST_INTERVAL_MS) return
        lastFeaturedJourneyResumePersistenceAt = now
        val isEligible = trackId !in completedFeaturedTrackIds && positionMs >= FEATURED_JOURNEY_RESUME_MIN_POSITION_MS
        featuredJourneyResumeState = if (isEligible) {
            PlaybackResumeState(trackId, positionMs)
        } else {
            PlaybackResumeState(trackId = null, positionMs = 0L)
        }
        refreshFeaturedJourney()
        viewModelScope.launch {
            preferencesRepository.saveFeaturedJourneyResumeState(
                trackId = featuredJourneyResumeState.trackId,
                positionMs = featuredJourneyResumeState.positionMs
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        progressJob = null
        fadeJob?.cancel()
        fadeJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        controller?.volume = DEFAULT_PLAYER_VOLUME
        libraryScanJob?.cancel()
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        stopMediaStoreObservation()
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            // 基于 MusicX（MIT）媒体描述映射思路，提供完整显示标题、作者、专辑、封面和时长。
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDescription("$artist · $album")
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()

    private companion object {
        const val RESUME_PERSIST_INTERVAL_MS = 10_000L
        const val FEATURED_JOURNEY_RESUME_MIN_POSITION_MS = 10_000L
        const val RESUME_END_TOLERANCE_MS = 2_000L
        const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        val MUSIC_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        const val DEFAULT_PLAYER_VOLUME = 1f
        const val FADE_STEPS = 8
        const val FADE_STEP_DELAY_MS = 35L
        const val SLEEP_TIMER_TICK_MS = 1_000L
        const val MAX_SLEEP_TIMER_MINUTES = 180
        const val MIN_VALID_DURATION_MS = 1_000L
        const val FEATURED_COMPLETION_TOLERANCE_MS = 2_000L
    }
}
