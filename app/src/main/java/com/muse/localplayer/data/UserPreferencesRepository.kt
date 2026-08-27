package com.muse.localplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.musePreferences by preferencesDataStore(name = "muse_preferences")

data class PlaybackResumeState(
    val trackId: Long?,
    val positionMs: Long
)

data class PlaybackBookmark(
    val trackId: Long,
    val positionMs: Long,
    val savedAtEpochMs: Long
)

data class FeaturedTopicProgress(
    val completedTrackIds: Set<Long> = emptySet(),
    val resumeState: PlaybackResumeState = PlaybackResumeState(trackId = null, positionMs = 0L)
)

class UserPreferencesRepository(private val context: Context) {
    private val preferences: Flow<Preferences> = context.musePreferences.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val favoriteIds: Flow<Set<Long>> = preferences.map { preferences ->
        preferences[KEY_FAVORITES].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    val queueIds: Flow<List<Long>> = preferences.map { preferences ->
        preferences[KEY_QUEUE]
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .distinct()
    }

    val repeatMode: Flow<Int> = preferences.map { preferences ->
        preferences[KEY_REPEAT_MODE] ?: 0
    }

    val shuffleEnabled: Flow<Boolean> = preferences.map { preferences ->
        preferences[KEY_SHUFFLE] ?: false
    }

    val playbackSpeed: Flow<Float> = preferences.map { preferences ->
        preferences[KEY_PLAYBACK_SPEED] ?: 1f
    }

    val mixingPlaybackEnabled: Flow<Boolean> = preferences.map { preferences ->
        preferences[KEY_MIXING_PLAYBACK] ?: false
    }

    val fadeTransitionsEnabled: Flow<Boolean> = preferences.map { preferences ->
        preferences[KEY_FADE_TRANSITIONS] ?: true
    }

    val playbackHistoryIds: Flow<List<Long>> = preferences.map { preferences ->
        preferences[KEY_PLAYBACK_HISTORY]
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .distinct()
            .take(HISTORY_LIMIT)
    }

    val featuredCompletedTrackIds: Flow<Set<Long>> = preferences.map { preferences ->
        preferences[KEY_FEATURED_COMPLETED_TRACK_IDS]
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    val featuredJourneyResumeState: Flow<PlaybackResumeState> = preferences.map { preferences ->
        PlaybackResumeState(
            trackId = preferences[KEY_FEATURED_JOURNEY_RESUME_TRACK_ID],
            positionMs = preferences[KEY_FEATURED_JOURNEY_RESUME_POSITION_MS] ?: 0L
        )
    }

    val activeFeaturedTopicId: Flow<String?> = preferences.map { preferences ->
        preferences[KEY_ACTIVE_FEATURED_TOPIC_ID]?.takeIf { it.isNotBlank() }
    }

    /**
     * Per-topic progress state. The legacy single-topic value remains a read fallback for the
     * default topic until that topic receives its first v1.9 write.
     */
    fun featuredTopicProgress(topicId: String): Flow<FeaturedTopicProgress> {
        val normalizedId = normalizeTopicId(topicId)
        val completedKey = featuredTopicCompletedKey(normalizedId)
        val resumeTrackKey = featuredTopicResumeTrackKey(normalizedId)
        val resumePositionKey = featuredTopicResumePositionKey(normalizedId)
        return preferences.map { preferences ->
            val initialized = normalizedId in preferences[KEY_INITIALIZED_FEATURED_TOPIC_IDS].orEmpty()
            val useLegacy = !initialized && normalizedId == DEFAULT_FEATURED_TOPIC_ID
            val completed = if (useLegacy) {
                preferences[KEY_FEATURED_COMPLETED_TRACK_IDS].orEmpty()
            } else {
                preferences[completedKey].orEmpty()
            }.mapNotNull { it.toLongOrNull() }.toSet()
            val resumeState = if (useLegacy) {
                PlaybackResumeState(
                    trackId = preferences[KEY_FEATURED_JOURNEY_RESUME_TRACK_ID],
                    positionMs = preferences[KEY_FEATURED_JOURNEY_RESUME_POSITION_MS] ?: 0L
                )
            } else {
                PlaybackResumeState(
                    trackId = preferences[resumeTrackKey],
                    positionMs = preferences[resumePositionKey] ?: 0L
                )
            }
            FeaturedTopicProgress(completedTrackIds = completed, resumeState = resumeState)
        }
    }

    val sleepTimerEndEpochMs: Flow<Long> = preferences.map { preferences ->
        preferences[KEY_SLEEP_TIMER_END_EPOCH_MS] ?: 0L
    }

    val sleepAfterCurrentTrackId: Flow<Long?> = preferences.map { preferences ->
        preferences[KEY_SLEEP_AFTER_CURRENT_TRACK_ID]
    }

    val playbackResumeState: Flow<PlaybackResumeState> = preferences.map { preferences ->
        PlaybackResumeState(
            trackId = preferences[KEY_RESUME_TRACK_ID],
            positionMs = preferences[KEY_RESUME_POSITION_MS] ?: 0L
        )
    }

    val playbackBookmarks: Flow<List<PlaybackBookmark>> = preferences.map { preferences ->
        preferences[KEY_PLAYBACK_BOOKMARKS]
            .orEmpty()
            .split(BOOKMARK_SEPARATOR)
            .mapNotNull(::decodeBookmark)
            .distinctBy { it.trackId to it.positionMs }
            .sortedByDescending(PlaybackBookmark::savedAtEpochMs)
            .take(BOOKMARK_LIMIT)
    }

    suspend fun toggleFavorite(trackId: Long) {
        context.musePreferences.edit { preferences ->
            val updated = preferences[KEY_FAVORITES].orEmpty().toMutableSet()
            val value = trackId.toString()
            if (value in updated) updated.remove(value) else updated.add(value)
            preferences[KEY_FAVORITES] = updated
        }
    }

    suspend fun saveQueue(trackIds: List<Long>) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_QUEUE] = trackIds.distinct().joinToString(",")
        }
    }

    suspend fun saveRepeatMode(mode: Int) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_REPEAT_MODE] = mode
        }
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_SHUFFLE] = enabled
        }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2f)
        }
    }

    suspend fun saveMixingPlaybackEnabled(enabled: Boolean) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_MIXING_PLAYBACK] = enabled
        }
    }

    suspend fun saveFadeTransitionsEnabled(enabled: Boolean) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_FADE_TRANSITIONS] = enabled
        }
    }

    suspend fun recordPlayback(trackId: Long) {
        context.musePreferences.edit { preferences ->
            val history = preferences[KEY_PLAYBACK_HISTORY]
                .orEmpty()
                .split(",")
                .mapNotNull { it.toLongOrNull() }
                .filterNot { it == trackId }
                .toMutableList()
            history.add(0, trackId)
            preferences[KEY_PLAYBACK_HISTORY] = history.take(HISTORY_LIMIT).joinToString(",")
        }
    }

    suspend fun clearPlaybackHistory() {
        context.musePreferences.edit { preferences -> preferences.remove(KEY_PLAYBACK_HISTORY) }
    }

    suspend fun markFeaturedTrackCompleted(trackId: Long) {
        context.musePreferences.edit { preferences ->
            val completed = preferences[KEY_FEATURED_COMPLETED_TRACK_IDS].orEmpty().toMutableSet()
            completed += trackId.toString()
            preferences[KEY_FEATURED_COMPLETED_TRACK_IDS] = completed
        }
    }

    suspend fun clearFeaturedJourneyProgress() {
        context.musePreferences.edit { preferences -> preferences.remove(KEY_FEATURED_COMPLETED_TRACK_IDS) }
    }

    suspend fun saveActiveFeaturedTopicId(topicId: String) {
        context.musePreferences.edit { preferences ->
            preferences[KEY_ACTIVE_FEATURED_TOPIC_ID] = normalizeTopicId(topicId)
        }
    }

    suspend fun markFeaturedTopicTrackCompleted(topicId: String, trackId: Long) {
        val normalizedId = normalizeTopicId(topicId)
        context.musePreferences.edit { preferences ->
            preferences.migrateLegacyDefaultTopicIfNeeded(normalizedId)
            val key = featuredTopicCompletedKey(normalizedId)
            preferences[key] = preferences[key].orEmpty() + trackId.toString()
            preferences.markFeaturedTopicInitialized(normalizedId)
        }
    }

    suspend fun clearFeaturedTopicJourneyProgress(topicId: String) {
        val normalizedId = normalizeTopicId(topicId)
        context.musePreferences.edit { preferences ->
            preferences.remove(featuredTopicCompletedKey(normalizedId))
            preferences.remove(featuredTopicResumeTrackKey(normalizedId))
            preferences.remove(featuredTopicResumePositionKey(normalizedId))
            preferences.markFeaturedTopicInitialized(normalizedId)
        }
    }

    suspend fun saveFeaturedTopicJourneyResumeState(topicId: String, trackId: Long?, positionMs: Long) {
        val normalizedId = normalizeTopicId(topicId)
        context.musePreferences.edit { preferences ->
            preferences.migrateLegacyDefaultTopicIfNeeded(normalizedId)
            if (trackId == null) {
                preferences.remove(featuredTopicResumeTrackKey(normalizedId))
                preferences.remove(featuredTopicResumePositionKey(normalizedId))
            } else {
                preferences[featuredTopicResumeTrackKey(normalizedId)] = trackId
                preferences[featuredTopicResumePositionKey(normalizedId)] = positionMs.coerceAtLeast(0L)
            }
            preferences.markFeaturedTopicInitialized(normalizedId)
        }
    }

    suspend fun saveFeaturedJourneyResumeState(trackId: Long?, positionMs: Long) {
        context.musePreferences.edit { preferences ->
            if (trackId == null) {
                preferences.remove(KEY_FEATURED_JOURNEY_RESUME_TRACK_ID)
                preferences.remove(KEY_FEATURED_JOURNEY_RESUME_POSITION_MS)
            } else {
                preferences[KEY_FEATURED_JOURNEY_RESUME_TRACK_ID] = trackId
                preferences[KEY_FEATURED_JOURNEY_RESUME_POSITION_MS] = positionMs.coerceAtLeast(0L)
            }
        }
    }

    suspend fun saveSleepTimerEndEpochMs(endEpochMs: Long) {
        context.musePreferences.edit { preferences ->
            if (endEpochMs > 0L) preferences[KEY_SLEEP_TIMER_END_EPOCH_MS] = endEpochMs
            else preferences.remove(KEY_SLEEP_TIMER_END_EPOCH_MS)
        }
    }

    suspend fun saveSleepAfterCurrentTrackId(trackId: Long?) {
        context.musePreferences.edit { preferences ->
            if (trackId == null) preferences.remove(KEY_SLEEP_AFTER_CURRENT_TRACK_ID)
            else preferences[KEY_SLEEP_AFTER_CURRENT_TRACK_ID] = trackId
        }
    }

    suspend fun savePlaybackResumeState(trackId: Long?, positionMs: Long) {
        context.musePreferences.edit { preferences ->
            if (trackId == null) {
                preferences.remove(KEY_RESUME_TRACK_ID)
                preferences[KEY_RESUME_POSITION_MS] = 0L
            } else {
                preferences[KEY_RESUME_TRACK_ID] = trackId
                preferences[KEY_RESUME_POSITION_MS] = positionMs.coerceAtLeast(0L)
            }
        }
    }

    suspend fun addPlaybackBookmark(trackId: Long, positionMs: Long) {
        val bookmark = PlaybackBookmark(
            trackId = trackId,
            positionMs = positionMs.coerceAtLeast(0L),
            savedAtEpochMs = System.currentTimeMillis()
        )
        context.musePreferences.edit { preferences ->
            val updated = preferences[KEY_PLAYBACK_BOOKMARKS]
                .orEmpty()
                .split(BOOKMARK_SEPARATOR)
                .mapNotNull(::decodeBookmark)
                .filterNot { it.trackId == bookmark.trackId && it.positionMs == bookmark.positionMs }
                .toMutableList()
            updated.add(0, bookmark)
            preferences[KEY_PLAYBACK_BOOKMARKS] = updated
                .sortedByDescending(PlaybackBookmark::savedAtEpochMs)
                .take(BOOKMARK_LIMIT)
                .joinToString(BOOKMARK_SEPARATOR) { it.encode() }
        }
    }

    suspend fun removePlaybackBookmark(bookmark: PlaybackBookmark) {
        context.musePreferences.edit { preferences ->
            val updated = preferences[KEY_PLAYBACK_BOOKMARKS]
                .orEmpty()
                .split(BOOKMARK_SEPARATOR)
                .mapNotNull(::decodeBookmark)
                .filterNot { it.savedAtEpochMs == bookmark.savedAtEpochMs }
            if (updated.isEmpty()) preferences.remove(KEY_PLAYBACK_BOOKMARKS)
            else preferences[KEY_PLAYBACK_BOOKMARKS] = updated.joinToString(BOOKMARK_SEPARATOR) { it.encode() }
        }
    }

    private fun MutablePreferences.markFeaturedTopicInitialized(topicId: String) {
        this[KEY_INITIALIZED_FEATURED_TOPIC_IDS] = this[KEY_INITIALIZED_FEATURED_TOPIC_IDS].orEmpty() + topicId
    }

    private fun MutablePreferences.migrateLegacyDefaultTopicIfNeeded(topicId: String) {
        if (topicId != DEFAULT_FEATURED_TOPIC_ID || topicId in this[KEY_INITIALIZED_FEATURED_TOPIC_IDS].orEmpty()) return
        val legacyCompleted = this[KEY_FEATURED_COMPLETED_TRACK_IDS].orEmpty()
        if (legacyCompleted.isNotEmpty()) this[featuredTopicCompletedKey(topicId)] = legacyCompleted
        val legacyTrackId = this[KEY_FEATURED_JOURNEY_RESUME_TRACK_ID]
        if (legacyTrackId != null) {
            this[featuredTopicResumeTrackKey(topicId)] = legacyTrackId
            this[featuredTopicResumePositionKey(topicId)] = this[KEY_FEATURED_JOURNEY_RESUME_POSITION_MS] ?: 0L
        }
    }

    private fun normalizeTopicId(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(MAX_TOPIC_ID_LENGTH)
            .ifBlank { DEFAULT_FEATURED_TOPIC_ID }

    private fun featuredTopicCompletedKey(topicId: String) = stringSetPreferencesKey("featured_topic_${topicId}_completed")
    private fun featuredTopicResumeTrackKey(topicId: String) = longPreferencesKey("featured_topic_${topicId}_resume_track")
    private fun featuredTopicResumePositionKey(topicId: String) = longPreferencesKey("featured_topic_${topicId}_resume_position")

    private fun PlaybackBookmark.encode(): String = "$trackId$BOOKMARK_FIELD_SEPARATOR$positionMs$BOOKMARK_FIELD_SEPARATOR$savedAtEpochMs"

    private fun decodeBookmark(raw: String): PlaybackBookmark? {
        val fields = raw.split(BOOKMARK_FIELD_SEPARATOR)
        if (fields.size != 3) return null
        val trackId = fields[0].toLongOrNull() ?: return null
        val positionMs = fields[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val savedAt = fields[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
        return PlaybackBookmark(trackId, positionMs, savedAt)
    }

    companion object {
        private val KEY_FAVORITES = stringSetPreferencesKey("favorite_ids")
        private val KEY_QUEUE = stringPreferencesKey("queue_ids")
        private val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        private val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        private val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_MIXING_PLAYBACK = booleanPreferencesKey("mixing_playback_enabled")
        private val KEY_FADE_TRANSITIONS = booleanPreferencesKey("fade_transitions_enabled")
        private val KEY_PLAYBACK_HISTORY = stringPreferencesKey("playback_history")
        private val KEY_FEATURED_COMPLETED_TRACK_IDS = stringSetPreferencesKey("featured_completed_track_ids")
        private val KEY_FEATURED_JOURNEY_RESUME_TRACK_ID = longPreferencesKey("featured_journey_resume_track_id")
        private val KEY_FEATURED_JOURNEY_RESUME_POSITION_MS = longPreferencesKey("featured_journey_resume_position_ms")
        private val KEY_ACTIVE_FEATURED_TOPIC_ID = stringPreferencesKey("active_featured_topic_id")
        private val KEY_INITIALIZED_FEATURED_TOPIC_IDS = stringSetPreferencesKey("initialized_featured_topic_ids")
        private val KEY_SLEEP_TIMER_END_EPOCH_MS = longPreferencesKey("sleep_timer_end_epoch_ms")
        private val KEY_SLEEP_AFTER_CURRENT_TRACK_ID = longPreferencesKey("sleep_after_current_track_id")
        private val KEY_RESUME_TRACK_ID = longPreferencesKey("resume_track_id")
        private val KEY_RESUME_POSITION_MS = longPreferencesKey("resume_position_ms")
        private val KEY_PLAYBACK_BOOKMARKS = stringPreferencesKey("playback_bookmarks")
        private const val HISTORY_LIMIT = 50
        private const val BOOKMARK_LIMIT = 30
        private const val BOOKMARK_SEPARATOR = ";"
        private const val BOOKMARK_FIELD_SEPARATOR = "|"
        private const val DEFAULT_FEATURED_TOPIC_ID = "default"
        private const val MAX_TOPIC_ID_LENGTH = 48
    }
}
