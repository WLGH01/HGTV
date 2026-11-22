package com.wlgh.hgtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wlgh.hgtv.data.Api
import com.wlgh.hgtv.data.Episode
import com.wlgh.hgtv.data.Playback
import com.wlgh.hgtv.data.Post
import com.wlgh.hgtv.data.Video
import com.wlgh.hgtv.data.VideoPage
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
import kotlinx.coroutines.launch

private sealed interface PhoneRoute {
    data object Home : PhoneRoute
    data class Detail(val id: String, val title: String) : PhoneRoute
    data class Player(val video: Video, val ep: Int) : PhoneRoute
    data class Post(val id: String) : PhoneRoute
}

@Composable
fun AppRoot() {
    var route by remember { mutableStateOf<PhoneRoute>(PhoneRoute.Home) }
    when (val current = route) {
        PhoneRoute.Home -> PhoneHome(
            onVideo = { route = PhoneRoute.Detail(it.id, it.title) },
            onPost = { route = PhoneRoute.Post(it) }
        )
        is PhoneRoute.Detail -> PhoneDetail(
            id = current.id,
            title = current.title,
            onBack = { route = PhoneRoute.Home },
            onPlay = { video, ep -> route = PhoneRoute.Player(video, ep) }
        )
        is PhoneRoute.Player -> PhonePlayer(
            video = current.video,
            initialEp = current.ep,
            onBack = { route = PhoneRoute.Detail(current.video.id, current.video.title) }
        )
        is PhoneRoute.Post -> PhonePost(
            id = current.id,
            onBack = { route = PhoneRoute.Home }
        )
    }
}

@Composable
private fun PhoneHome(onVideo: (Video) -> Unit, onPost: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("短剧", "漫剧", "排行榜", "吃瓜")
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("黄果解析", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("原生版", color = Muted, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                PhoneChip(title, index == tab) { tab = index }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> VideoFeed("ai-duanju", onVideo)
                1 -> VideoFeed("ai-manju", onVideo)
                2 -> RankFeed(onVideo)
                else -> PostFeed(onPost)
            }
        }
    }
}

@Composable
private fun PhoneChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color(0xFFCCCCCC),
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Amber else Color(0xFF1F1F1F))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun VideoFeed(tid: String, onVideo: (Video) -> Unit) {
    var query by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    PagedVideoGrid(
        key = tid to keyword,
        load = { page ->
            if (keyword.isBlank()) Api.category(tid, page) else Api.search(keyword, page)
        },
        onVideo = onVideo,
        header = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                placeholder = { Text("搜索剧名") },
                trailingIcon = {
                    TextButton(onClick = { keyword = query.trim() }) { Text("搜索") }
                }
            )
        }
    )
}

@Composable
private fun RankFeed(onVideo: (Video) -> Unit) {
    var type by remember { mutableStateOf("hot") }
    val types = listOf("热播" to "hot", "推荐" to "recommend", "潜力" to "potential")
    PagedVideoGrid(
        key = type,
        load = { page -> Api.ranks(type, page) },
        onVideo = onVideo,
        header = {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { (label, value) ->
                    PhoneChip(label, value == type) { type = value }
                }
            }
        }
    )
}

@Composable
private fun PagedVideoGrid(
    key: Any,
    load: suspend (Int) -> VideoPage,
    onVideo: (Video) -> Unit,
    header: @Composable () -> Unit = {}
) {
    val items = remember { mutableStateListOf<Video>() }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(reset: Boolean) {
        loading = true
        error = null
        try {
            val next = if (reset) 1 else page + 1
            val result = load(next)
            if (reset) items.clear()
            items.addAll(result.items)
            page = result.page
            pages = result.pages
            started = true
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(key) {
        page = 0
        pages = 1
        started = false
        items.clear()
        fetch(true)
    }

    Column(Modifier.fillMaxSize()) {
        header()
        error?.let {
            Text(it, color = Danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (!started && loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            gridItems(items) { video -> VideoCard(video, onVideo) }
            item {
                if (started) {
                    if (page < pages) {
                        TextButton(
                            onClick = { scope.launch { fetch(false) } },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (loading) "加载中…" else "加载更多")
                        }
                    } else {
                        Text("已加载全部", color = Muted, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: Video, onVideo: (Video) -> Unit) {
    Column(Modifier.clickable { onVideo(video) }) {
        CoverImage(
            url = video.cover,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            video.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(video.remarks, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PostFeed(onPost: (String) -> Unit) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(page) {
        loading = true
        error = null
        runCatching { Api.posts(page) }
            .onSuccess { result ->
                posts = if (page == 1) result.items else posts + result.items
                pages = result.pages
            }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, color = Danger, modifier = Modifier.padding(16.dp)) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            gridItems(posts) { post ->
                Column(Modifier.clickable { onPost(post.id) }) {
                    CoverImage(
                        post.cover,
                        Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        post.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(post.created, color = Muted, fontSize = 11.sp, maxLines = 1)
                }
            }
            item {
                if (page < pages) {
                    TextButton(
                        onClick = { page += 1 },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loading) "加载中…" else "加载更多")
                    }
                } else {
                    Text("已加载全部", color = Muted, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PhoneDetail(
    id: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Video, Int) -> Unit
) {
    var video by remember { mutableStateOf<Video?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    LaunchedEffect(id) {
        runCatching { Api.detail(id) }
            .onSuccess { video = it }
            .onFailure { error = it.message }
        loading = false
    }
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 20.sp) }
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val v = video
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            v == null -> Text(
                "详情读取失败：${error ?: "未知错误"}",
                color = Danger,
                modifier = Modifier.padding(16.dp)
            )
            else -> {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    CoverImage(
                        v.cover,
                        Modifier.width(120.dp).height(160.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(v.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(v.remarks, color = Muted, fontSize = 13.sp)
                        Text(
                            "${v.episodeCount} 集 · ${if (v.finished) "已完结" else "连载中"}",
                            color = Muted,
                            fontSize = 13.sp
                        )
                        if (v.tags.isNotEmpty()) {
                            Text(v.tags.joinToString(" / "), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Button(
                    onClick = { onPlay(v, v.episodes.firstOrNull()?.num ?: 1) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("播放第 1 集")
                }
                if (v.desc.isNotBlank()) {
                    Text("简介", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Text(
                        v.desc,
                        color = Color(0xFFCFCFCF),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                Text(
                    "选集 · ${v.episodes.size} 集",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    v.episodes.chunked(5).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { episode ->
                                OutlinedButton(
                                    onClick = { onPlay(v, episode.num) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${episode.num}", fontSize = 13.sp)
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PhonePlayer(video: Video, initialEp: Int, onBack: () -> Unit) {
    val player = rememberExoPlayer()
    var ep by remember { mutableIntStateOf(initialEp) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
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

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, showController = false, modifier = Modifier.fillMaxSize())

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 20.sp) }
            Text(
                "${video.title} · 第${ep}集",
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { showSpeed = true }) {
                    Text(speedLabel(speed), color = Amber, fontSize = 14.sp)
                }
                DropdownMenu(expanded = showSpeed, onDismissRequest = { showSpeed = false }) {
                    SPEEDS.forEach { value ->
                        DropdownMenuItem(
                            text = { Text(speedLabel(value)) },
                            onClick = {
                                speed = value
                                showSpeed = false
                            }
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Slider(
                value = position.toFloat(),
                onValueChange = { value ->
                    position = value.toLong()
                    player.seekTo(position)
                },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { ep = (ep - 1).coerceAtLeast(1) },
                    enabled = ep > 1
                ) { Text("上一集", color = Color.White) }
                TextButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Text(if (isPlaying) "暂停" else "播放", color = Amber)
                }
                TextButton(
                    onClick = { ep = (ep + 1).coerceAtMost(video.episodes.maxOfOrNull { it.num } ?: ep) },
                    enabled = ep < (video.episodes.maxOfOrNull { it.num } ?: ep)
                ) { Text("下一集", color = Color.White) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems(video.episodes) { episode ->
                    Text(
                        "${episode.num}",
                        color = if (episode.num == ep) Color.Black else Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (episode.num == ep) Amber else Color(0xFF222222))
                            .clickable { ep = episode.num }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (loading || isBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Amber)
        }
        error?.let {
            Text(
                it,
                color = Danger,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
    }
}

@Composable
private fun PhonePost(id: String, onBack: () -> Unit) {
    var post by remember { mutableStateOf<Post?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    LaunchedEffect(id) {
        runCatching { Api.post(id) }
            .onSuccess { post = it }
            .onFailure { error = it.message }
        loading = false
    }
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
        when {
            loading -> CircularProgressIndicator(color = Amber, modifier = Modifier.padding(32.dp))
            post == null -> Text("吃瓜详情读取失败：${error ?: "未知错误"}", color = Danger)
            else -> {
                val p = post!!
                Text(p.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOf(p.author, p.created).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                CoverImage(
                    p.cover,
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(stripHtml(p.content), fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
    }
}
