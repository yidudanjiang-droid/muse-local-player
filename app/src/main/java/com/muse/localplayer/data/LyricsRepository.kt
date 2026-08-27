package com.muse.localplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads optional, user-maintained LRC sidecar files packaged next to featured audio assets.
 * No lyrics are fetched or inferred: an absent or malformed file simply produces an empty timeline.
 */
class LyricsRepository(private val context: Context) {
    private val featuredLyricCache = ConcurrentHashMap<String, List<LyricLine>>()

    suspend fun loadFeaturedLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        if (!track.isFeaturedAsset) return@withContext emptyList()
        val assetPath = track.uri.path
            ?.removePrefix("/")
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext emptyList()
        featuredLyricCache[assetPath] ?: loadLyricsFromAsset(assetPath).also { lines ->
            featuredLyricCache.putIfAbsent(assetPath, lines)
        }
    }

    private fun loadLyricsFromAsset(assetPath: String): List<LyricLine> {
        val lyricPath = assetPath.substringBeforeLast('.', assetPath) + LRC_EXTENSION
        val content = runCatching {
            context.assets.open(lyricPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return parseLrc(content)
    }

    internal fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        content.lineSequence().forEach { rawLine ->
            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isBlank()) return@forEach
            matches.mapNotNull { match -> match.toTimestampMs() }
                .forEach { timestampMs -> lines += LyricLine(timestampMs, text) }
        }
        return lines
            .distinctBy { it.timestampMs to it.text }
            .sortedBy(LyricLine::timestampMs)
    }

    private fun MatchResult.toTimestampMs(): Long? {
        val minutes = groups[1]?.value?.toLongOrNull() ?: return null
        val seconds = groups[2]?.value?.toLongOrNull() ?: return null
        val fraction = groups[3]?.value.orEmpty().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        if (seconds !in 0..59) return null
        return minutes * 60_000L + seconds * 1_000L + fraction
    }

    private companion object {
        const val LRC_EXTENSION = ".lrc"
        val TIMESTAMP_REGEX = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    }
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)
