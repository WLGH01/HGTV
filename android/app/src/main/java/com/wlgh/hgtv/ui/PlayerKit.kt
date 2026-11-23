package com.wlgh.hgtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.wlgh.hgtv.R
import com.wlgh.hgtv.data.Api

val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

fun speedLabel(speed: Float): String = when {
    speed == 1f -> "1.0x"
    speed % 1f == 0f -> "${speed.toInt()}.0x"
    else -> "${speed}x"
}

@Composable
fun rememberExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Api.UA)
            .setDefaultRequestProperties(mapOf("Referer" to Api.SITE + "/"))
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
fun VideoSurface(
    exoPlayer: ExoPlayer,
    showController: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.view_player, null) as PlayerView).apply {
                player = exoPlayer
                useController = showController
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier
    )
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}
