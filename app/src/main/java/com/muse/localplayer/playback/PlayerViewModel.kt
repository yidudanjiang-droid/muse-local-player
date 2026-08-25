package com.muse.localplayer.playback

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
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
import com.muse.localplayer.data.FeaturedAudioPack
import com.muse.localplayer.data.FeaturedAudioRepository
import com.muse.localplayer.data.FeaturedPackMetadata
import com.muse.localplayer.data.TrackSource
import com.muse.localplayer.data.LibraryLoadResult
import com.muse.localplayer.data.MediaStoreObserver
import com.muse.localplayer.data.MusicRepository
import com.muse.localplayer.data.PlaybackResumeState
import com.muse.localplayer.data.Track
import com.muse.localplayer.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepository = MusicRepository(application)
    private val featuredAudioRepository = FeaturedAudioRepository(application)
    private val mediaStoreObserver = MediaStoreObserver(application)
    private val preferencesRepository = UserPreferencesRepository(application)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _featuredTracks = MutableStateFlow<List<Track>>(emptyList())
    val featuredTracks = _featuredTracks.asStateFlow()

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

    private val _mixingPlaybackEnabled = MutableStateFlow(false)
    val mixingPlaybackEnabled = _mixingPlaybackEnabled.asStateFlow()

    private val _fadeTransitionsEnabled = MutableStateFlow(true)
    val fadeTransitionsEnabled = _fadeTransitionsEnabled.asStateFlow()

    private val _controllerReady = MutableStateFlow(false)
    val controllerReady = _controllerReady.asStateFlow()

    private val _playerMessage = MutableStateFlow<String?>(null)
    val playerMessage = _playerMessage.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var libraryScanJob: Job? = null
    private var mediaStoreObserverJob: Job? = null
    private var pendingTrack: Track? = null
    private var restoredQueueIds: List<Long> = emptyList()
    private var resumeState = PlaybackResumeState(trackId = null, positionMs = 0L)
    private var lastResumePersistenceAt = 0L
    private var cachedFeaturedPack: FeaturedAudioPack? = null
    private var trackIndex: Map<Long, Track> = emptyMap()
    private var tracksBySource: Map<TrackSource, List<Track>> = emptyMap()
    private var timelineTrackId: String? = null
    private var stableDurationMs: Long = 0L
    private val sessionToken = SessionToken(application, ComponentName(application, MusicPlaybackService::class.java))
    private val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resetTimelineFor(mediaItem)
            syncCurrentTrack(mediaItem)
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
            refreshPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            progressJob?.cancel()
            progressJob = null
            _isPlaying.value = false
            refreshPlaybackState(forcePersist = true)
            _playerMessage.value = "无法播放当前音频。请确认文件仍在设备中且格式受支持。"
        }
    }

    init {
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
                pendingTrack?.let {
                    pendingTrack = null
                    play(it)
                }
            }.onFailure {
                _playerMessage.value = "播放器服务连接失败，请重新打开应用后重试。"
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
            _featuredTracks.value = featured
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
        val allTracks = featured + deviceTracks
        trackIndex = allTracks.associateBy(Track::id)
        tracksBySource = allTracks.groupBy(Track::source)
        _tracks.value = allTracks
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
            resetTimelineForTrackId(track.id.toString())
            activeController.setMediaItems(queueToPlay.map { it.toMediaItem() }, startIndex, 0L)
            activeController.prepare()
            updateQueueState(queueToPlay)
        }
        val shouldFadeIn = !activeController.isPlaying && _fadeTransitionsEnabled.value
        if (shouldFadeIn) activeController.volume = 0f
        activeController.play()
        if (shouldFadeIn) fadeIn(activeController)
        _currentTrack.value = track
        _isPlaying.value = true
    }

    fun playQueueItem(index: Int) {
        val activeController = controller ?: return
        if (index !in _queue.value.indices) return
        activeController.seekToDefaultPosition(index)
        val shouldFadeIn = !activeController.isPlaying && _fadeTransitionsEnabled.value
        if (shouldFadeIn) activeController.volume = 0f
        activeController.play()
        if (shouldFadeIn) fadeIn(activeController)
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
        resetTimelineForTrackId(cleanedQueue[startIndex].id.toString())
        activeController.setMediaItems(cleanedQueue.map { it.toMediaItem() }, startIndex, 0L)
        activeController.prepare()
        updateQueueState(cleanedQueue)
        if (shouldPlay) {
            activeController.volume = if (_fadeTransitionsEnabled.value) 0f else DEFAULT_PLAYER_VOLUME
            activeController.play()
            fadeIn(activeController)
        }
    }

    fun addToQueue(track: Track) {
        if (_queue.value.any { it.id == track.id }) return
        val activeController = controller
        if (activeController == null || activeController.mediaItemCount == 0) {
            updateQueueState(listOf(track))
        } else {
            activeController.addMediaItem(track.toMediaItem())
            updateQueueState(_queue.value + track)
        }
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

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        controller?.removeMediaItem(index)
        updateQueueState(_queue.value.toMutableList().also { it.removeAt(index) })
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.value.indices || toIndex !in _queue.value.indices || fromIndex == toIndex) return
        controller?.moveMediaItem(fromIndex, toIndex)
        val updatedQueue = _queue.value.toMutableList()
        val item = updatedQueue.removeAt(fromIndex)
        updatedQueue.add(toIndex, item)
        updateQueueState(updatedQueue)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        _currentTrack.value = null
        _isPlaying.value = false
        resetTimelineForTrackId(null)
        updateQueueState(emptyList())
        viewModelScope.launch { preferencesRepository.savePlaybackResumeState(null, 0L) }
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
        _currentTrack.value = restoredQueue.getOrNull(resumeIndex)
        refreshPlaybackState()
    }

    private fun syncCurrentTrack(mediaItem: MediaItem?) {
        _currentTrack.value = mediaItem?.mediaId?.toLongOrNull()?.let(trackIndex::get)
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

    private fun persistResumeState(force: Boolean) {
        val trackId = _currentTrack.value?.id ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastResumePersistenceAt < RESUME_PERSIST_INTERVAL_MS) return
        lastResumePersistenceAt = now
        val position = _positionMs.value
        resumeState = PlaybackResumeState(trackId, position)
        viewModelScope.launch { preferencesRepository.savePlaybackResumeState(trackId, position) }
    }

    override fun onCleared() {
        progressJob?.cancel()
        progressJob = null
        fadeJob?.cancel()
        fadeJob = null
        libraryScanJob?.cancel()
        stopMediaStoreObservation()
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
        const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        val MUSIC_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        const val DEFAULT_PLAYER_VOLUME = 1f
        const val FADE_STEPS = 8
        const val FADE_STEP_DELAY_MS = 35L
        const val MIN_VALID_DURATION_MS = 1_000L
    }
}
