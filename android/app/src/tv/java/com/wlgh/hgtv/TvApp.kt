package com.wlgh.hgtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wlgh.hgtv.data.Api
import com.wlgh.hgtv.data.Playback
import com.wlgh.hgtv.data.Post
import com.wlgh.hgtv.data.Video
import com.wlgh.hgtv.ui.Amber
import com.wlgh.hgtv.ui.Bg
import com.wlgh.hgtv.ui.CoverImage
import com.wlgh.hgtv.ui.Danger
import com.wlgh.hgtv.ui.Muted
import com.wlgh.hgtv.ui.SPEEDS
import com.wlgh.hgtv.ui.VideoSurface
import com.wlgh.hgtv.ui.rememberExoPlayer
import com.wlgh.hgtv.ui.speedLabel
import com.wlgh.hgtv.ui.stripHtml
import kotlinx.coroutines.delay

private sealed interface TvRoute {
    data object Home : TvRoute
    data class Detail(val id: String, val title: String) : TvRoute
    data class Player(val video: Video, val ep: Int) : TvRoute
    data class Post(val id: String) : TvRoute
}

@Composable
fun AppRoot() {
    var route by remember { mutableStateOf<TvRoute>(TvRoute.Home) }
    when (val current = route) {
        TvRoute.Home -> TvHome(
            onVideo = { route = TvRoute.Detail(it.id, it.title) },
            onPost = { route = TvRoute.Post(it) }
        )
        is TvRoute.Detail -> TvDetail(
            id = current.id,
            title = current.title,
            onBack = { route = TvRoute.Home },
            onPlay = { video, ep -> route = TvRoute.Player(video, ep) }
        )
        is TvRoute.Player -> TvPlayer(
            video = current.video,
            initialEp = current.ep,
            onBack = { route = TvRoute.Detail(current.video.id, current.video.title) }
        )
        is TvRoute.Post -> TvPost(
            id = current.id,
            onBack = { route = TvRoute.Home }
        )
    }
}

private fun Modifier.tvKey(onClick: () -> Unit): Modifier = this
    .onKeyEvent { event ->
        val activate = event.type == KeyEventType.KeyUp && (
            event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter ||
                event.key == Key.Spacebar
            )
        if (activate) {
            onClick()
            true
        } else {
            false
        }
    }
    .pointerInput(Unit) { detectTapGestures { onClick() } }

@Composable
private fun TvHome(onVideo: (Video) -> Unit, onPost: (String) -> Unit) {
    var nav by remember { mutableIntStateOf(0) }
    var rankType by remember { mutableStateOf("hot") }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val navLabels = listOf("短剧", "漫剧", "排行榜", "吃瓜")

    LaunchedEffect(nav, rankType) {
        loading = true
        error = null
        runCatching {
            when (nav) {
                0 -> videos = Api.category("ai-duanju", 1).items
                1 -> videos = Api.category("ai-manju", 1).items
                2 -> videos = Api.ranks(rankType, 1).items
                else -> posts = Api.posts(1).items
            }
        }.onFailure { error = it.message }
        loading = false
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        TvRail(navLabels, nav) { nav = it }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    navLabels[nav],
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(Modifier.width(16.dp))
                Text("原生 TV 版", color = Muted, fontSize = 14.sp)
            }
            if (nav == 2) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("热播" to "hot", "推荐" to "recommend", "潜力" to "potential")
                        .forEach { (label, value) ->
                            TvSmallButton(label, selected = rankType == value) { rankType = value }
                        }
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
                error != null -> Text("加载失败：$error", color = Danger)
                nav == 3 -> TvPostRow(posts, onPost)
                else -> {
                    val hero = videos.firstOrNull()
                    if (hero != null) {
                        Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp))) {
                            CoverImage(
                                hero.cover,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Bg.copy(alpha = 0.95f))
                                    )
                                )
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart).padding(20.dp)
                            ) {
                                Text(
                                    hero.title,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(hero.remarks, color = Muted, fontSize = 15.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    TvVideoRow(videos, onVideo)
                }
            }
        }
    }
}

@Composable
private fun TvRail(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val first = remember { FocusRequester() }
    Column(
        Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(Color(0xFF141414))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("黄果", color = Amber, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("解析 · TV", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        labels.forEachIndexed { index, label ->
            TvRailItem(
                text = label,
                selected = index == selected,
                focusRequester = if (index == 0) first else null
            ) { onSelect(index) }
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun TvRailItem(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (selected) Amber else Color.White,
        fontSize = 19.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey(onClick)
            .background(
                if (focused) Color(0xFF2A2A2A) else if (selected) Color(0xFF1F1F1F) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .border(
                if (focused) 2.dp else 0.dp,
                Amber,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun TvVideoRow(videos: List<Video>, onVideo: (Video) -> Unit) {
    if (videos.isEmpty()) {
        Text("暂无内容", color = Muted)
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        rowItemsIndexed(videos) { _, video ->
            TvVideoCard(video = video, onVideo = onVideo, focusRequester = null)
        }
    }
}

@Composable
private fun TvVideoCard(
    video: Video,
    onVideo: (Video) -> Unit,
    focusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-card-scale")
    Column(
        Modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey { onVideo(video) }
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Amber else Color(0xFF262626),
                RoundedCornerShape(14.dp)
            )
            .padding(6.dp)
    ) {
        CoverImage(
            video.cover,
            Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            video.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(video.remarks, color = Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun TvPostRow(posts: List<Post>, onPost: (String) -> Unit) {
    if (posts.isEmpty()) {
        Text("暂无内容", color = Muted)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        rowItemsIndexed(posts) { _, post ->
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .width(300.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .tvKey { onPost(post.id) }
                    .border(
                        if (focused) 3.dp else 1.dp,
                        if (focused) Amber else Color(0xFF262626),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                CoverImage(
                    post.cover,
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(post.title, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(post.created, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TvDetail(
    id: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Video, Int) -> Unit
) {
    var video by remember { mutableStateOf<Video?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(id) {
        runCatching { Api.detail(id) }
            .onSuccess { video = it }
            .onFailure { error = it.message }
        loading = false
    }
    LaunchedEffect(video) {
        if (video != null) {
            delay(200)
            runCatching { playFocus.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).padding(28.dp)) {
        TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
        val v = video
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            v == null -> Text("详情读取失败：${error ?: "未知错误"}", color = Danger)
            else -> Row(Modifier.fillMaxSize()) {
                CoverImage(
                    v.cover,
                    Modifier.width(260.dp).height(350.dp).clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(v.remarks, color = Muted, fontSize = 16.sp)
                    Text(
                        "${v.episodeCount} 集 · ${if (v.finished) "已完结" else "连载中"} · 评分 ${v.score}",
                        color = Muted,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    TvAction(
                        text = "播放第 1 集",
                        primary = true,
                        focusRequester = playFocus
                    ) { onPlay(v, v.episodes.firstOrNull()?.num ?: 1) }
                    Spacer(Modifier.height(16.dp))
                    if (v.desc.isNotBlank()) {
                        Text(
                            v.desc,
                            color = Color(0xFFCFCFCF),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("选集 · ${v.episodes.size} 集", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        v.episodes.chunked(8).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { episode ->
                                    TvSmallButton("${episode.num}") { onPlay(v, episode.num) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlayer(video: Video, initialEp: Int, onBack: () -> Unit) {
    val player = rememberExoPlayer()
    var ep by remember { mutableIntStateOf(initialEp) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val playFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    LaunchedEffect(ep) {
        loading = true
        error = null
        player.pause()
        runCatching { Playback.resolve(video.id, ep) }
            .onSuccess { info ->
                player.setMediaItem(MediaItem.fromUri(info.url))
                player.setPlaybackSpeed(speed)
                player.prepare()
                player.play()
            }
            .onFailure { error = it.message ?: "解析失败" }
        loading = false
    }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { playFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, showController = false, modifier = Modifier.fillMaxSize())

        if (showControls) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = Color.White, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${video.title} · 第${ep}集",
                    color = Color.White,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvSmallButton(if (isPlaying) "暂停" else "播放", focusRequester = playFocus) {
                        if (isPlaying) player.pause() else player.play()
                    }
                    TvSmallButton("上一集", enabled = ep > 1) { ep -= 1 }
                    TvSmallButton("下一集", enabled = ep < (video.episodes.maxOfOrNull { it.num } ?: ep)) {
                        ep += 1
                    }
                    TvSmallButton("后退 10s") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }
                    TvSmallButton("前进 10s") { player.seekTo(player.currentPosition + 10_000) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("倍速", color = Muted, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItemsIndexed(SPEEDS) { _, value ->
                            TvSmallButton(speedLabel(value), selected = speed == value) { speed = value }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItemsIndexed(video.episodes) { _, episode ->
                        TvSmallButton("${episode.num}", selected = episode.num == ep) { ep = episode.num }
                    }
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Amber)
        }
        error?.let {
            Text(it, color = Danger, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
    }
}

@Composable
private fun TvPost(id: String, onBack: () -> Unit) {
    var post by remember { mutableStateOf<Post?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(id) {
        runCatching { Api.post(id) }
            .onSuccess { post = it }
            .onFailure { error = it.message }
        loading = false
    }
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(32.dp)
    ) {
        TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
        when {
            loading -> CircularProgressIndicator(color = Amber)
            post == null -> Text("吃瓜详情读取失败：${error ?: "未知错误"}", color = Danger)
            else -> {
                val p = post!!
                Text(p.title, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("${p.author} · ${p.created}", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stripHtml(p.content),
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    color = Color(0xFFDDDDDD)
                )
            }
        }
    }
}

@Composable
private fun TvAction(
    text: String,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "tv-action-scale")
    Text(
        text = text,
        color = if (primary) Color.Black else Color.White,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey(onClick)
            .background(
                if (primary) Amber else Color(0xFF1C1C1C),
                RoundedCornerShape(10.dp)
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color(0xFF333333),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun TvSmallButton(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tv-small-scale")
    Text(
        text = text,
        color = when {
            !enabled -> Color(0xFF666666)
            selected -> Color.Black
            else -> Color.White
        },
        fontSize = 15.sp,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .tvKey { if (enabled) onClick() }
            .background(
                if (selected) Amber else Color(0xFF1C1C1C),
                RoundedCornerShape(8.dp)
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Amber else Color(0xFF333333),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
