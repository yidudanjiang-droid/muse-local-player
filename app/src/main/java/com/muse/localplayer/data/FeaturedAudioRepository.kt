package com.muse.localplayer.data

import android.content.Context
import android.content.res.AssetManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.muse.localplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Loads publisher-authored audio from APK assets.
 *
 * Legacy packages may keep one topic in featured_audio/pack.json. New packages can place multiple
 * independent topics under featured_audio/topics/<topic-id>/pack.json. Each topic owns its own
 * fallback metadata, cover, track overrides and program entries while retaining the same resilient
 * compressed-asset playback path used by second-packaged APKs.
 */
class FeaturedAudioRepository(private val context: Context) {
    suspend fun loadCatalog(): FeaturedAudioCatalog = withContext(Dispatchers.IO) {
        clearStaleMaterializedAssets()
        val packs = listTopicRoots().mapNotNull { rootAssetPath ->
            runCatching { loadPackFromRoot(rootAssetPath) }.getOrNull()
        }.sortedWith(
            compareBy<FeaturedAudioPack> { it.metadata.sortOrder }
                .thenBy { it.metadata.title.lowercase() }
                .thenBy(FeaturedAudioPack::id)
        )
        FeaturedAudioCatalog(topics = packs)
    }

    /** Legacy compatibility for callers that expect the first configured topic. */
    suspend fun loadPack(): FeaturedAudioPack = loadCatalog().activeFallback ?: FeaturedAudioPack()

    suspend fun loadTracks(): List<Track> = loadCatalog().topics.flatMap(FeaturedAudioPack::tracks)

    private fun loadPackFromRoot(rootAssetPath: String): FeaturedAudioPack {
        val configuration = readPackConfiguration(rootAssetPath)
        val artworkUri = featuredArtworkUri(configuration.metadata, rootAssetPath)
        val entries = listAudioAssetPaths(rootAssetPath)
            .mapNotNull { assetPath ->
                // A bad second-packaged file must never make the remaining topic disappear.
                runCatching {
                    val override = configuration.overrideFor(assetPath, rootAssetPath)
                    assetPath to assetPath.toFeaturedTrack(
                        packId = configuration.id,
                        pack = configuration.metadata,
                        override = override,
                        artworkUri = artworkUri
                    )
                }.getOrNull()
            }
            .sortedWith(
                compareBy<Pair<String, Track>> { it.second.trackNumber }
                    .thenBy { it.second.title.lowercase() }
                    .thenBy { it.first.lowercase() }
            )
        val tracks = entries.map { it.second }
        val programsByTrackId = entries.mapNotNull { (assetPath, track) ->
            configuration.overrideFor(assetPath, rootAssetPath)?.program
                ?.takeIf(FeaturedTrackProgram::hasContent)
                ?.let { track.id to it }
        }.toMap()
        return FeaturedAudioPack(
            id = configuration.id,
            rootAssetPath = rootAssetPath,
            metadata = configuration.metadata,
            tracks = tracks,
            programsByTrackId = programsByTrackId
        )
    }

    private fun listTopicRoots(): List<String> {
        val topicDirectory = "$FEATURED_AUDIO_ROOT/$TOPICS_DIRECTORY"
        val declaredTopics = context.assets.list(topicDirectory)
            .orEmpty()
            .map { name -> "$topicDirectory/$name" }
            .filter { root ->
                context.assets.list(root).orEmpty().isNotEmpty() &&
                    (assetExists("$root/$PACK_FILE_NAME") || listAudioAssetPaths(root).isNotEmpty())
            }
        // A populated topics directory intentionally becomes the catalog source. This prevents its
        // nested audio from being silently duplicated into the legacy default topic.
        return declaredTopics.ifEmpty { listOf(FEATURED_AUDIO_ROOT) }
    }

    private fun readPackConfiguration(rootAssetPath: String): PackConfiguration {
        val fallbackId = if (rootAssetPath == FEATURED_AUDIO_ROOT) {
            DEFAULT_TOPIC_ID
        } else {
            rootAssetPath.substringAfterLast('/').normalizeTopicId().ifBlank { DEFAULT_TOPIC_ID }
        }
        return runCatching {
            val rawJson = context.assets.open("$rootAssetPath/$PACK_FILE_NAME").bufferedReader().use { it.readText() }
            val json = JSONObject(rawJson)
            PackConfiguration(
                id = json.optionalText("id")?.normalizeTopicId()?.ifBlank { fallbackId } ?: fallbackId,
                metadata = FeaturedPackMetadata(
                    title = json.textOrDefault("title", DEFAULT_TITLE),
                    eyebrow = json.textOrDefault("eyebrow", DEFAULT_EYEBROW),
                    description = json.textOrDefault("description", DEFAULT_DESCRIPTION),
                    defaultArtist = json.textOrDefault("defaultArtist", DEFAULT_ARTIST),
                    defaultAlbum = json.textOrDefault("defaultAlbum", DEFAULT_ALBUM),
                    playLabel = json.textOrDefault("playLabel", DEFAULT_PLAY_LABEL),
                    identityLabel = json.optionalText("identity"),
                    editionLabel = json.optionalText("edition"),
                    listeningGuide = json.optionalText("listeningGuide"),
                    coverAssetPath = json.optionalText("coverAsset"),
                    sortOrder = json.optInt("sortOrder", 0)
                ),
                trackOverrides = json.trackOverrides()
            )
        }.getOrElse { PackConfiguration(id = fallbackId) }
    }

    private fun JSONObject.textOrDefault(key: String, fallback: String): String =
        optString(key).trim().takeIf { it.isNotBlank() } ?: fallback

    private fun JSONObject.optionalText(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() }

    private fun String.normalizeTopicId(): String =
        trim().lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .take(MAX_TOPIC_ID_LENGTH)

    private fun JSONObject.trackOverrides(): Map<String, TrackOverride> {
        val tracksObject = optJSONObject("tracks") ?: return emptyMap()
        val overrides = linkedMapOf<String, TrackOverride>()
        val keys = tracksObject.keys()
        while (keys.hasNext()) {
            val rawAssetPath = keys.next()
            val assetPath = rawAssetPath.trim().replace('\\', '/')
            val item = tracksObject.optJSONObject(rawAssetPath) ?: continue
            if (assetPath.isBlank()) continue
            overrides[assetPath] = TrackOverride(
                title = item.optionalText("title"),
                artist = item.optionalText("artist"),
                album = item.optionalText("album"),
                trackNumber = item.optInt("trackNumber", 0).takeIf { it > 0 },
                year = item.optInt("year", 0).takeIf { it > 0 },
                program = item.toFeaturedTrackProgram()
            )
        }
        return overrides
    }

    private fun PackConfiguration.overrideFor(assetPath: String, rootAssetPath: String): TrackOverride? =
        trackOverrides[assetPath]
            ?: trackOverrides[assetPath.removePrefix("$FEATURED_AUDIO_ROOT/")]
            ?: trackOverrides[assetPath.removePrefix("$rootAssetPath/")]

    private fun JSONObject.toFeaturedTrackProgram(): FeaturedTrackProgram? {
        val chapters = optJSONArray("chapters").toFeaturedChapters()
        val notes = optJSONArray("notes").toProgramNotes()
        return FeaturedTrackProgram(chapters = chapters, notes = notes).takeIf(FeaturedTrackProgram::hasContent)
    }

    private fun JSONArray?.toFeaturedChapters(): List<FeaturedChapter> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val title = item.optionalText("title") ?: continue
                val timestampMs = item.optLong("timestampMs", -1L).takeIf { it >= 0L }
                    ?: item.optionalText("time")?.toTimestampMs()
                    ?: continue
                add(FeaturedChapter(timestampMs = timestampMs, title = title))
            }
        }.distinctBy { it.timestampMs to it.title }.sortedBy(FeaturedChapter::timestampMs)
    }

    private fun JSONArray?.toProgramNotes(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }

    private fun String.toTimestampMs(): Long? {
        val match = TIMESTAMP_REGEX.matchEntire(trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues.getOrElse(3) { "" }.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        if (seconds !in 0..59) return null
        return minutes * 60_000L + seconds * 1_000L + fraction
    }

    private fun listAudioAssetPaths(directory: String): List<String> {
        return context.assets.list(directory).orEmpty().flatMap { name ->
            val childPath = "$directory/$name"
            val children = context.assets.list(childPath).orEmpty()
            when {
                children.isNotEmpty() -> listAudioAssetPaths(childPath)
                name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS -> listOf(childPath)
                else -> emptyList()
            }
        }
    }

    private fun String.toFeaturedTrack(
        packId: String,
        pack: FeaturedPackMetadata,
        override: TrackOverride?,
        artworkUri: Uri
    ): Track {
        val fileName = substringAfterLast('/').substringBeforeLast('.')
        val fallbackTitle = fileName
            .replace(Regex("^\\s*\\d+[._ -]*"), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { "未命名专题音频" }
        val fallbackTrackNumber = Regex("^\\s*(\\d+)").find(fileName)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0
        val metadata = readMetadata(this)
        val albumTitle = override?.album ?: metadata.album ?: pack.defaultAlbum
        return Track(
            id = stableAssetId(this),
            title = override?.title ?: metadata.title ?: fallbackTitle,
            artist = override?.artist ?: metadata.artist ?: pack.defaultArtist,
            album = albumTitle,
            albumId = -abs(("$packId:$albumTitle").hashCode().toLong()) - 2L,
            durationMs = metadata.durationMs,
            uri = resolvePlayableUri(this),
            artworkUri = artworkUri,
            trackNumber = override?.trackNumber ?: metadata.trackNumber ?: fallbackTrackNumber,
            year = override?.year ?: metadata.year ?: 0,
            source = TrackSource.FEATURED_ASSET,
            featuredTopicId = packId
        )
    }

    /**
     * Standard builds keep audio uncompressed for direct Media3 asset playback. If a secondary
     * packer compresses added audio, materialize a private copy and continue with file playback.
     */
    private fun resolvePlayableUri(assetPath: String): Uri {
        if (runCatching { context.assets.openFd(assetPath).use { } }.isSuccess) {
            return Uri.parse("asset:///$assetPath")
        }
        val cacheDirectory = materializedAssetCacheDirectory()
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
        val fileName = assetPath.substringAfterLast('/').ifBlank { "audio" }
        val safeName = "${assetCacheKey(assetPath)}_$fileName"
        val outputFile = File(cacheDirectory, safeName)
        if (outputFile.exists() && outputFile.length() > 0L) return Uri.fromFile(outputFile)

        val stagingFile = File(cacheDirectory, "$safeName.part")
        runCatching {
            context.assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                stagingFile.outputStream().use(input::copyTo)
            }
            if (outputFile.exists()) outputFile.delete()
            check(stagingFile.renameTo(outputFile)) { "无法准备内置音频缓存" }
        }.getOrElse {
            stagingFile.delete()
            throw it
        }
        return Uri.fromFile(outputFile)
    }

    private fun materializedAssetCacheDirectory(): File {
        val updateTimestamp = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        return File(context.cacheDir, "$MATERIALIZED_ASSET_DIRECTORY-$updateTimestamp")
    }

    private fun clearStaleMaterializedAssets() {
        val activeDirectory = materializedAssetCacheDirectory()
        context.cacheDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith(MATERIALIZED_ASSET_DIRECTORY) && it.name != activeDirectory.name }
            .forEach { staleDirectory -> runCatching { staleDirectory.deleteRecursively() } }
        activeDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".part") }
            .forEach { partialFile -> runCatching { partialFile.delete() } }
    }

    private fun stableAssetId(assetPath: String): Long {
        val bits = UUID.nameUUIDFromBytes(assetPath.toByteArray(Charsets.UTF_8)).leastSignificantBits and Long.MAX_VALUE
        return -bits - 1L
    }

    private fun assetCacheKey(assetPath: String): String =
        UUID.nameUUIDFromBytes(assetPath.toByteArray(Charsets.UTF_8)).toString()

    private fun readMetadata(assetPath: String): AssetMetadata {
        readMetadataFromDescriptor(assetPath).getOrNull()?.let { return it }
        // Secondary packers may compress new assets; probe a disposable private copy instead.
        return readMetadataFromTemporaryFile(assetPath).getOrDefault(AssetMetadata())
    }

    private fun readMetadataFromDescriptor(assetPath: String): Result<AssetMetadata> = runCatching {
        withRetriever { retriever ->
            context.assets.openFd(assetPath).use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                retriever.toAssetMetadata()
            }
        }
    }

    private fun readMetadataFromTemporaryFile(assetPath: String): Result<AssetMetadata> = runCatching {
        val suffix = ".${assetPath.substringAfterLast('.', "audio")}"
        val temporaryFile = File.createTempFile("muse_asset_probe_", suffix, context.cacheDir)
        try {
            context.assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                temporaryFile.outputStream().use(input::copyTo)
            }
            withRetriever { retriever ->
                retriever.setDataSource(temporaryFile.absolutePath)
                retriever.toAssetMetadata()
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private inline fun <T> withRetriever(block: (MediaMetadataRetriever) -> T): T {
        val retriever = MediaMetadataRetriever()
        return try {
            block(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.toAssetMetadata(): AssetMetadata = AssetMetadata(
        title = text(MediaMetadataRetriever.METADATA_KEY_TITLE),
        artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
        album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
        durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L } ?: 0L,
        trackNumber = text(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull(),
        year = text(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
    )

    private fun MediaMetadataRetriever.text(key: Int): String? =
        extractMetadata(key)?.trim()?.takeUnless { it.isBlank() || it.equals("<unknown>", true) }

    private fun featuredArtworkUri(pack: FeaturedPackMetadata, rootAssetPath: String): Uri {
        val configuredPath = resolveCoverAssetPath(pack.coverAssetPath, rootAssetPath)
        return configuredPath?.let { Uri.parse("file:///android_asset/$it") }
            ?: Uri.parse("android.resource://${context.packageName}/${R.drawable.muse_featured_hero}")
    }

    private fun resolveCoverAssetPath(rawPath: String?, rootAssetPath: String): String? {
        val normalized = rawPath
            ?.trim()
            ?.replace('\\', '/')
            ?.removePrefix("./")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val candidates = buildList {
            add(normalized)
            if (!normalized.startsWith("$FEATURED_AUDIO_ROOT/")) add("$rootAssetPath/$normalized")
        }.distinct()
        return candidates.firstOrNull { candidate ->
            candidate.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS && assetExists(candidate)
        }
    }

    private fun assetExists(path: String): Boolean =
        runCatching { context.assets.open(path).close() }.isSuccess

    private data class PackConfiguration(
        val id: String = DEFAULT_TOPIC_ID,
        val metadata: FeaturedPackMetadata = FeaturedPackMetadata(),
        val trackOverrides: Map<String, TrackOverride> = emptyMap()
    )

    private data class TrackOverride(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val trackNumber: Int? = null,
        val year: Int? = null,
        val program: FeaturedTrackProgram? = null
    )

    private data class AssetMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
        val trackNumber: Int? = null,
        val year: Int? = null
    )

    private companion object {
        const val FEATURED_AUDIO_ROOT = "featured_audio"
        const val TOPICS_DIRECTORY = "topics"
        const val PACK_FILE_NAME = "pack.json"
        const val MATERIALIZED_ASSET_DIRECTORY = "muse_featured_audio"
        const val DEFAULT_TOPIC_ID = "default"
        const val DEFAULT_TITLE = "本期专题音频"
        const val DEFAULT_EYEBROW = "MUSE · FEATURED"
        const val DEFAULT_DESCRIPTION = "内置于 APK 的专题声音内容。"
        const val DEFAULT_ARTIST = "精选内容"
        const val DEFAULT_ALBUM = "专题音频包"
        const val DEFAULT_PLAY_LABEL = "从头播放"
        const val MAX_TOPIC_ID_LENGTH = 48
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac")
        val IMAGE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg")
        val TIMESTAMP_REGEX = Regex("(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?")
    }
}
