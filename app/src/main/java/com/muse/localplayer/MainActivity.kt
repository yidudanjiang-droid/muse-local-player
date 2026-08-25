package com.muse.localplayer

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
    private val permissionPreferences: SharedPreferences by lazy {
        getSharedPreferences("muse_permission_prompt_state", MODE_PRIVATE)
    }
    private var audioPermissionGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        audioPermissionGranted = hasAudioPermission()
        notificationPermissionGranted = hasNotificationPermission()
        playerViewModel.onAudioPermissionResult(audioPermissionGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioPermissionGranted = hasAudioPermission()
        notificationPermissionGranted = hasNotificationPermission()
        setContent {
            MuseTheme {
                var showLaunchSplash by remember { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    MuseMusicApp(
                        viewModel = playerViewModel,
                        audioPermissionGranted = audioPermissionGranted,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onRequestAudioPermission = ::requestAudioPermission,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onOpenAudioEffects = ::openAudioEffects
                    )
                    AnimatedVisibility(
                        visible = showLaunchSplash,
                        exit = fadeOut(animationSpec = tween(durationMillis = 380))
                    ) {
                        MuseLaunchSplash(onFinished = {
                            showLaunchSplash = false
                            requestMissingPermissions()
                        })
                    }
                }
            }
        }
        if (hasAudioPermission()) playerViewModel.reloadLibrary()
    }

    /** Requests both optional system surfaces on first entry, then keeps each action available separately. */
    private fun requestMissingPermissions() {
        val permissions = buildList {
            if (!hasAudioPermission() && !wasPrompted(audioPermission())) add(audioPermission())
            if (!hasNotificationPermission() && !wasPrompted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) {
            playerViewModel.reloadLibrary()
        } else {
            markPrompted(permissions)
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestAudioPermission() {
        if (hasAudioPermission()) {
            audioPermissionGranted = true
            playerViewModel.reloadLibrary()
        } else if (wasPrompted(audioPermission()) && !shouldShowRequestPermissionRationale(audioPermission())) {
            openApplicationSettings()
        } else {
            markPrompted(listOf(audioPermission()))
            permissionLauncher.launch(arrayOf(audioPermission()))
        }
    }

    private fun requestNotificationPermission() {
        if (hasNotificationPermission()) {
            notificationPermissionGranted = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (wasPrompted(Manifest.permission.POST_NOTIFICATIONS) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openApplicationSettings()
            } else {
                markPrompted(listOf(Manifest.permission.POST_NOTIFICATIONS))
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        audioPermissionGranted = hasAudioPermission()
        notificationPermissionGranted = hasNotificationPermission()
        if (audioPermissionGranted) playerViewModel.onAudioPermissionResult(true)
    }

    private fun markPrompted(permissions: Collection<String>) {
        permissionPreferences.edit().apply {
            permissions.forEach { putBoolean("prompted_$it", true) }
        }.apply()
    }

    private fun wasPrompted(permission: String): Boolean = permissionPreferences.getBoolean("prompted_$permission", false)

    private fun openApplicationSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
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

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
