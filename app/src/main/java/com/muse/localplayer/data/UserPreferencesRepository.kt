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

    val playbackResumeState: Flow<PlaybackResumeState> = preferences.map { preferences ->
        PlaybackResumeState(
            trackId = preferences[KEY_RESUME_TRACK_ID],
            positionMs = preferences[KEY_RESUME_POSITION_MS] ?: 0L
        )
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

    companion object {
        private val KEY_FAVORITES = stringSetPreferencesKey("favorite_ids")
        private val KEY_QUEUE = stringPreferencesKey("queue_ids")
        private val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        private val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        private val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_MIXING_PLAYBACK = booleanPreferencesKey("mixing_playback_enabled")
        private val KEY_FADE_TRANSITIONS = booleanPreferencesKey("fade_transitions_enabled")
        private val KEY_RESUME_TRACK_ID = longPreferencesKey("resume_track_id")
        private val KEY_RESUME_POSITION_MS = longPreferencesKey("resume_position_ms")
    }
}
