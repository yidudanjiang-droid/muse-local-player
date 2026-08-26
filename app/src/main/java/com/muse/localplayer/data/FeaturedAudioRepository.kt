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
import org.json.JSONObject
import kotlin.math.abs

/**
 * Scans audio packaged inside app/src/main/assets/featured_audio.
 * The optional pack.json controls display copy and fallback metadata for one APK topic.
 */
class FeaturedAudioRepository(private val context: Context) {
    suspend fun loadPack(): FeaturedAudioPack = withContext(Dispatchers.IO) {
        clearStaleMaterializedAssets()
        val configuration = readPackConfiguration()
        val artworkUri = featuredArtworkUri(configuration.metadata)
        val tracks = listAudioAssetPaths()
            .mapNotNull { assetPath ->
                // 二次打包时单个文件损坏、标签异常或缓存准备失败，不应让整个专题消失。
                runCatching {
                    assetPath to assetPath.toFeaturedTrack(
                        pack = configuration.metadata,
                        override = configuration.trackOverrides[assetPath]
                            ?: configuration.trackOverrides[assetPath.removePrefix("$FEATURED_AUDIO_ROOT/")],
                        artworkUri = artworkUri
                    )
                }.getOrNull()
            }
            .sortedWith(
                compareBy<Pair<String, Track>> { it.second.trackNumber }
                    .thenBy { it.second.title.lowercase() }
                    .thenBy { it.first.lowercase() }
            )
            .map { it.second }
        FeaturedAudioPack(metadata = configuration.metadata, tracks = tracks)
    }

    suspend fun loadTracks(): List<Track> = loadPack().tracks

    private fun readPackConfiguration(): PackConfiguration {
        return runCatching {
            val rawJson = context.assets.open(PACK_METADATA_FILE).bufferedReader().use { it.readText() }
            val json = JSONObject(rawJson)
            PackConfiguration(
                metadata = FeaturedPackMetadata(
                    title = json.textOrDefault("title", DEFAULT_TITLE),
                    eyebrow = json.textOrDefault("eyebrow", DEFAULT_EYEBROW),
                    description = json.textOrDefault("description", DEFAULT_DESCRIPTION),
                    defaultArtist = json.textOrDefault("defaultArtist", DEFAULT_ARTIST),
                    defaultAlbum = json.textOrDefault("defaultAlbum", DEFAULT_ALBUM),
                    playLabel = json.textOrDefault("playLabel", DEFAULT_PLAY_LABEL),
                    coverAssetPath = json.optionalText("coverAsset")
                ),
                trackOverrides = json.trackOverrides()
            )
        }.getOrElse { PackConfiguration() }
    }

    private fun JSONObject.textOrDefault(key: String, fallback: String): String =
        optString(key).trim().takeIf { it.isNotBlank() } ?: fallback

    private fun JSONObject.optionalText(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() }

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
                year = item.optInt("year", 0).takeIf { it > 0 }
            )
        }
        return overrides
    }

    /**
     * 既支持标准的 featured_audio/ 目录，也支持二次打包时直接添加到任意 assets 子目录的音频。
     * assets 目录通常规模有限；递归发现能避免二次打包工具改变目录层级后内容被静默忽略。
     */
    private fun listAudioAssetPaths(directory: String = ""): List<String> {
        return context.assets.list(directory).orEmpty().flatMap { name ->
            val childPath = if (directory.isBlank()) name else "$directory/$name"
            val children = context.assets.list(childPath).orEmpty()
            when {
                children.isNotEmpty() -> listAudioAssetPaths(childPath)
                name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS -> listOf(childPath)
                else -> emptyList()
            }
        }
    }

    private fun String.toFeaturedTrack(
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
            albumId = -abs(albumTitle.hashCode().toLong()) - 2L,
            durationMs = metadata.durationMs,
            uri = resolvePlayableUri(this),
            artworkUri = artworkUri,
            trackNumber = override?.trackNumber ?: metadata.trackNumber ?: fallbackTrackNumber,
            year = override?.year ?: metadata.year ?: 0,
            source = TrackSource.FEATURED_ASSET
        )
    }

    /**
     * 标准构建会保留音频为未压缩 assets，Media3 可直接读取 asset:/// URI。
     * 若二次打包工具压缩了新加入的文件，则复制到应用私有缓存后以 file:// URI 播放。
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
        // 外部二次打包工具可能压缩新加入的音频，AssetFileDescriptor 将不可用。
        // 仅临时复制到 cache 探测标签，随后立刻删除，不会持久化或占用用户存储。
        return readMetadataFromTemporaryFile(assetPath).getOrDefault(AssetMetadata())
    }

    private fun readMetadataFromDescriptor(assetPath: String): Result<AssetMetadata> = runCatching {
        withRetriever { retriever ->
            context.assets.openFd(assetPath).use { descriptor ->
                retriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
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
        durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: 0L,
        trackNumber = text(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            ?.substringBefore('/')
            ?.toIntOrNull(),
        year = text(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
    )

    private fun MediaMetadataRetriever.text(key: Int): String? =
        extractMetadata(key)?.trim()?.takeUnless { it.isBlank() || it.equals("<unknown>", true) }

    private data class PackConfiguration(
        val metadata: FeaturedPackMetadata = FeaturedPackMetadata(),
        val trackOverrides: Map<String, TrackOverride> = emptyMap()
    )

    private data class TrackOverride(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val trackNumber: Int? = null,
        val year: Int? = null
    )

    private data class AssetMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
        val trackNumber: Int? = null,
        val year: Int? = null
    )

    private fun featuredArtworkUri(pack: FeaturedPackMetadata): Uri {
        val configuredPath = resolveCoverAssetPath(pack.coverAssetPath)
        return configuredPath?.let { Uri.parse("file:///android_asset/$it") }
            ?: Uri.parse("android.resource://${context.packageName}/${R.drawable.muse_featured_hero}")
    }

    private fun resolveCoverAssetPath(rawPath: String?): String? {
        val normalized = rawPath
            ?.trim()
            ?.replace('\\', '/')
            ?.removePrefix("./")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val candidates = buildList {
            add(normalized)
            if (!normalized.startsWith("$FEATURED_AUDIO_ROOT/")) {
                add("$FEATURED_AUDIO_ROOT/$normalized")
            }
        }
        return candidates.firstOrNull { candidate ->
            candidate.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS &&
                runCatching { context.assets.open(candidate).close() }.isSuccess
        }
    }

    private companion object {
        const val FEATURED_AUDIO_ROOT = "featured_audio"
        const val PACK_METADATA_FILE = "$FEATURED_AUDIO_ROOT/pack.json"
        const val MATERIALIZED_ASSET_DIRECTORY = "muse_featured_audio"
        const val DEFAULT_TITLE = "本期专题音频"
        const val DEFAULT_EYEBROW = "MUSE · FEATURED"
        const val DEFAULT_DESCRIPTION = "内置于 APK 的专题声音内容。"
        const val DEFAULT_ARTIST = "精选内容"
        const val DEFAULT_ALBUM = "专题音频包"
        const val DEFAULT_PLAY_LABEL = "从头播放"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac")
        val IMAGE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg")
    }
}
