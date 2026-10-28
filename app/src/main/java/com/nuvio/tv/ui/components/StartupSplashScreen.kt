package com.nuvio.tv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioPrimitives
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val avatarColor = remember(profileColorHex) {
        profileColorHex?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: NuvioPrimitives.blue500
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val imageData: Any = if (profileBackgroundUrl.startsWith("file:")) {
                java.io.File(java.net.URI(profileBackgroundUrl))
            } else {
                profileBackgroundUrl
            }
            val request = ImageRequest.Builder(LocalContext.current)
                .data(imageData)
                .crossfade(false)
            if (backgroundCacheKey != null) {
                request.memoryCacheKey(backgroundCacheKey)
                    .diskCacheKey(backgroundCacheKey)
                    .placeholderMemoryCacheKey(backgroundCacheKey)
            }
            AsyncImage(
                model = request.build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!skipGradient) {
            val baseBg = Color(0xFF121212)
            val baseBgElevated = NuvioPrimitives.neutral875
            val gradientTop = lerp(baseBgElevated, avatarColor, 0.3f)
            val gradientMid = lerp(baseBg, avatarColor, 0.14f)
            val halfFadeStrong = avatarColor.copy(alpha = 0.26f)
            val halfFadeSoft = avatarColor.copy(alpha = 0.08f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gradientTop,
                                0.42f to gradientMid,
                                1f to baseBg
                            )
                        )
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to halfFadeStrong,
                                0.45f to halfFadeSoft,
                                0.72f to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.mooncat_splash_v102),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // A few lightweight animated stars keep loading visible on low-memory TVs.
        val shimmer = rememberInfiniteTransition(label = "mooncat stars").animateFloat(
            initialValue = 0.28f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "star glow"
        )
        Canvas(Modifier.fillMaxSize()) {
            listOf(Offset(0.13f, 0.16f), Offset(0.82f, 0.12f), Offset(0.91f, 0.34f),
                Offset(0.16f, 0.66f)).forEachIndexed { index, point ->
                val alpha = if (index % 2 == 0) shimmer.value else 1f - shimmer.value / 2f
                drawCircle(Color(0xFFFFE9BD).copy(alpha = alpha), radius = 2.dp.toPx(),
                    center = Offset(size.width * point.x, size.height * point.y))
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            LoadingIndicator(modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.82f - 14.dp).size(28.dp))
        }
    }
}
