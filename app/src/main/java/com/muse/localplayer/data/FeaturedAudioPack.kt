package com.muse.localplayer.data

data class FeaturedAudioPack(
    val metadata: FeaturedPackMetadata = FeaturedPackMetadata(),
    val tracks: List<Track> = emptyList()
)

data class FeaturedPackMetadata(
    val title: String = "本期专题音频",
    val eyebrow: String = "MUSE · FEATURED",
    val description: String = "内置于 APK 的专题声音内容。",
    val defaultArtist: String = "精选内容",
    val defaultAlbum: String = "专题音频包",
    val playLabel: String = "从头播放",
    /** Optional image asset path configured by pack.json, for example featured_audio/cover.webp. */
    val coverAssetPath: String? = null
)
