package com.muse.localplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LibraryLoadResult {
    data class Success(val tracks: List<Track>) : LibraryLoadResult
    data object PermissionDenied : LibraryLoadResult
    data class Failed(val message: String) : LibraryLoadResult
}

class MusicRepository(private val context: Context) {
    suspend fun loadTracks(): LibraryLoadResult = withContext(Dispatchers.IO) {
        runCatching {
            // 基于 MusicX（MIT）本地媒体库实现：Android 10+ 使用外部卷 URI。
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR
            )
            val selectionClauses = mutableListOf(
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                "${MediaStore.Audio.Media.DURATION} >= ?"
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                selectionClauses += "${MediaStore.MediaColumns.IS_PENDING} = 0"
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                selectionClauses += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
            }
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            buildList {
                context.contentResolver.query(
                    collection,
                    projection,
                    selectionClauses.joinToString(" AND "),
                    arrayOf("15000"),
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        add(
                            Track(
                                id = id,
                                title = cursor.textOrFallback(titleColumn, "未命名歌曲"),
                                artist = cursor.textOrFallback(artistColumn, "未知艺人"),
                                album = cursor.textOrFallback(albumColumn, "未知专辑"),
                                albumId = albumId,
                                durationMs = cursor.getLong(durationColumn),
                                uri = ContentUris.withAppendedId(collection, id),
                                artworkUri = albumId.takeIf { it > 0L }?.let {
                                    ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), it)
                                },
                                dateAddedSeconds = cursor.getLong(dateAddedColumn),
                                trackNumber = cursor.getInt(trackColumn) % 1_000,
                                year = cursor.getInt(yearColumn)
                            )
                        )
                    }
                }
            }
        }.fold(
            onSuccess = { LibraryLoadResult.Success(it) },
            onFailure = { error ->
                when (error) {
                    is SecurityException -> LibraryLoadResult.PermissionDenied
                    else -> LibraryLoadResult.Failed(error.message ?: "无法读取设备媒体库")
                }
            }
        )
    }

    private fun android.database.Cursor.textOrFallback(columnIndex: Int, fallback: String): String {
        return getString(columnIndex)
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("<unknown>", ignoreCase = true) }
            ?: fallback
    }
}
