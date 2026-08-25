package com.muse.localplayer.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.muse.localplayer.R

/**
 * 保留 Media3 标准媒体样式与锁屏控制，同时统一 Muse 的通知文案和状态栏图标。
 */
@UnstableApi
class MuseMediaNotificationProvider(
    context: Context,
    channelId: String
) : DefaultMediaNotificationProvider(
    context,
    DefaultMediaNotificationProvider.NotificationIdProvider { NOTIFICATION_ID },
    channelId,
    R.string.playback_notification_channel
) {
    private val appContext = context.applicationContext

    init {
        setSmallIcon(R.drawable.ic_stat_muse)
    }

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence {
        return metadata.displayTitle
            ?: metadata.title
            ?: appContext.getString(R.string.app_name)
    }

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence {
        val artist = metadata.artist?.toString()?.takeIf(String::isNotBlank)
        val album = metadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
        return listOfNotNull(artist, album)
            .distinct()
            .joinToString(" · ")
            .ifBlank { appContext.getString(R.string.playback_notification_fallback) }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
