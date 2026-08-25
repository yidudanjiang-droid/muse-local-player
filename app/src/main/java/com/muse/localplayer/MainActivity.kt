package com.muse.localplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.muse.localplayer.playback.PlayerViewModel
import com.muse.localplayer.ui.MuseLaunchSplash
import com.muse.localplayer.ui.MuseMusicApp
import com.muse.localplayer.ui.theme.MuseTheme

class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        playerViewModel.onAudioPermissionResult(permissions[audioPermission] == true || hasAudioPermission())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseTheme {
                var showLaunchSplash by remember { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    MuseMusicApp(
                        viewModel = playerViewModel,
                        onRequestPermission = ::requestMediaPermissions,
                        onOpenAudioEffects = ::openAudioEffects
                    )
                    AnimatedVisibility(
                        visible = showLaunchSplash,
                        exit = fadeOut(animationSpec = tween(durationMillis = 380))
                    ) {
                        MuseLaunchSplash(onFinished = { showLaunchSplash = false })
                    }
                }
            }
        }
        if (hasAudioPermission()) playerViewModel.reloadLibrary()
    }

    private fun requestMediaPermissions() {
        if (hasAudioPermission()) {
            // 通知授权不影响读取或播放设备音乐；避免用户每次手动扫描都被重复打扰。
            playerViewModel.reloadLibrary()
            return
        }
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissions = buildList {
            add(audioPermission)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openAudioEffects() {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, OUTPUT_MIX_AUDIO_SESSION)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "此设备未提供可用的音效面板", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        // Android 音效框架约定：0 代表全局输出混音会话。
        const val OUTPUT_MIX_AUDIO_SESSION = 0
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
