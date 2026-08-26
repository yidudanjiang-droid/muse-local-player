package com.muse.localplayer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muse.localplayer.R
import kotlinx.coroutines.delay

/**
 * 应用首启时覆盖主界面的品牌开屏。动画总时长保持在两秒内，避免影响进入音乐库的速度。
 */
@Composable
fun MuseLaunchSplash(onFinished: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val brandAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "brandAlpha"
    )
    val brandScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing),
        label = "brandScale"
    )
    val networkAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0.2f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "networkAlpha"
    )
    val shimmer = rememberInfiniteTransition(label = "cloudShimmer")
    val cloudDrift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudDrift"
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(SPLASH_HOLD_MILLIS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF062A67)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.muse_launch_cloud_atmosphere),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.03f + cloudDrift * 0.025f
                    scaleY = 1.03f + cloudDrift * 0.025f
                    translationY = -18f + cloudDrift * 36f
                }
                .alpha(networkAlpha)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x4A010235),
                        0.42f to Color(0x15010235),
                        1f to Color(0x82010235)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .alpha(brandAlpha)
                .scale(brandScale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = Color(0xD9FFFFFF),
                shadowElevation = 18.dp,
                tonalElevation = 0.dp
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_muse_launcher),
                    contentDescription = "Muse 本地音乐",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "白云科技",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "6G 电信",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFD9ECFF),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "MUSE · LOCAL MUSIC",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xDDF2F7FF),
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "云端声场，正在抵达",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xD7DCEBFF),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp)
                .alpha(brandAlpha)
        )
    }
}

private const val SPLASH_HOLD_MILLIS = 1_650L
