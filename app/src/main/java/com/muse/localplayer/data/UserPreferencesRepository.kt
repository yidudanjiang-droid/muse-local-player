package com.muse.localplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
        private val KEY_SLEEP_TIMER_END_EPOCH_MS = longPreferencesKey("sleep_timer_end_epoch_ms")
        private val KEY_SLEEP_AFTER_CURRENT_TRACK_ID = longPreferencesKey("sleep_after_current_track_id")
        private val KEY_RESUME_TRACK_ID = longPreferencesKey("resume_track_id")
        private val KEY_RESUME_POSITION_MS = longPreferencesKey("resume_position_ms")
        private val KEY_PLAYBACK_BOOKMARKS = stringPreferencesKey("playback_bookmarks")
        private const val HISTORY_LIMIT = 50
        private const val BOOKMARK_LIMIT = 30
        private const val BOOKMARK_SEPARATOR = ";"
        private const val BOOKMARK_FIELD_SEPARATOR = "|"
    }
}
