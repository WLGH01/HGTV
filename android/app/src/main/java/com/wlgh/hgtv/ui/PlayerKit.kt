package com.wlgh.hgtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
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
            PlayerView(context).apply {
                player = exoPlayer
                useController = showController
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier
    )
}
