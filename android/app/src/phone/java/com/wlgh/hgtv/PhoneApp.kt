package com.wlgh.hgtv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wlgh.hgtv.data.Api
import com.wlgh.hgtv.data.Playback
import com.wlgh.hgtv.data.Post
import com.wlgh.hgtv.data.Store
import com.wlgh.hgtv.data.Topic
import com.wlgh.hgtv.data.Video
import com.wlgh.hgtv.data.VideoPage
import com.wlgh.hgtv.ui.Amber
import com.wlgh.hgtv.ui.Bg
import com.wlgh.hgtv.ui.CoverImage
import com.wlgh.hgtv.ui.Danger
import com.wlgh.hgtv.ui.Muted
import com.wlgh.hgtv.ui.SPEEDS
import com.wlgh.hgtv.ui.VideoSurface
import com.wlgh.hgtv.ui.formatCount
import com.wlgh.hgtv.ui.formatTime
import com.wlgh.hgtv.ui.rememberExoPlayer
import com.wlgh.hgtv.ui.speedLabel
import com.wlgh.hgtv.ui.stripHtml
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PhoneRoute {
    data object Home : PhoneRoute
    data class Detail(val id: String, val title: String, val startEp: Int? = null) : PhoneRoute
    data class Player(val video: Video, val ep: Int) : PhoneRoute
    data class Post(val id: String) : PhoneRoute
    data class Topic(val slug: String, val title: String) : PhoneRoute
    data class Tag(val slug: String, val title: String) : PhoneRoute
}

@Composable
fun AppRoot() {
    var route by remember { mutableStateOf<PhoneRoute>(PhoneRoute.Home) }
    when (val current = route) {
        PhoneRoute.Home -> PhoneHome(
            onVideo = { route = PhoneRoute.Detail(it.id, it.title) },
            onPost = { route = PhoneRoute.Post(it) },
            onTopic = { slug, title -> route = PhoneRoute.Topic(slug, title) },
            onTag = { slug, title -> route = PhoneRoute.Tag(slug, title) },
            onHistory = { id, title, ep -> route = PhoneRoute.Detail(id, title, ep) }
        )
        is PhoneRoute.Detail -> PhoneDetail(
            id = current.id,
            title = current.title,
            startEp = current.startEp,
            onBack = { route = PhoneRoute.Home },
            onPlay = { video, ep -> route = PhoneRoute.Player(video, ep) },
            onOpen = { video -> route = PhoneRoute.Detail(video.id, video.title) }
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
        is PhoneRoute.Topic -> PhoneTopic(
            slug = current.slug,
            title = current.title,
            onBack = { route = PhoneRoute.Home },
            onVideo = { route = PhoneRoute.Detail(it.id, it.title) }
        )
        is PhoneRoute.Tag -> PhoneTag(
            slug = current.slug,
            title = current.title,
            onBack = { route = PhoneRoute.Home },
            onVideo = { route = PhoneRoute.Detail(it.id, it.title) }
        )
    }
}

@Composable
private fun PhoneHome(
    onVideo: (Video) -> Unit,
    onPost: (String) -> Unit,
    onTopic: (String, String) -> Unit,
    onTag: (String, String) -> Unit,
    onHistory: (String, String, Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("首页", "短剧", "漫剧", "原创", "排行榜", "专题", "吃瓜", "博客", "我的")
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("黄果解析", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("原生版", color = Muted, fontSize = 12.sp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs) { title ->
                val index = tabs.indexOf(title)
                PhoneChip(title, index == tab) { tab = index }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeFeed(onVideo, onTopic, onTag)
                1 -> VideoFeed("ai-duanju", onVideo)
                2 -> VideoFeed("ai-manju", onVideo)
                3 -> PagedVideoGrid(
                    key = "original",
                    load = { page -> Api.original(page) },
                    onVideo = onVideo
                )
                4 -> RankFeed(onVideo)
                5 -> TopicFeed(onTopic)
                6 -> PostFeed(onPost)
                7 -> BlogFeed(onPost)
                else -> MineFeed(onHistory)
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
private fun HomeFeed(
    onVideo: (Video) -> Unit,
    onTopic: (String, String) -> Unit,
    onTag: (String, String) -> Unit
) {
    var featured by remember { mutableStateOf<List<Video>>(emptyList()) }
    var latest by remember { mutableStateOf<List<Video>>(emptyList()) }
    var original by remember { mutableStateOf<List<Video>>(emptyList()) }
    var hot by remember { mutableStateOf<List<Video>>(emptyList()) }
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var tags by remember { mutableStateOf<List<com.wlgh.hgtv.data.Tag>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        featured = runCatching { Api.featured(20) }.getOrDefault(emptyList())
        latest = runCatching { Api.category("ai-duanju", 1, 20, "latest").items }.getOrDefault(emptyList())
        original = runCatching { Api.original(1, 20).items }.getOrDefault(emptyList())
        hot = runCatching { Api.category("ai-duanju", 1, 20, "hot").items }.getOrDefault(emptyList())
        topics = runCatching { Api.topics() }.getOrDefault(emptyList())
        tags = runCatching { Api.tagCategories().flatMap { it.tags }.take(30) }.getOrDefault(emptyList())
        if (featured.isEmpty() && latest.isEmpty() && original.isEmpty()) {
            error = "首页内容加载失败，请检查网络后重试"
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        error?.let { msg ->
            item { Text(msg, color = Danger, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item { HomeVideoSection("精选推荐", featured, onVideo) }
        item { HomeVideoSection("最近上新", latest, onVideo) }
        item { HomeVideoSection("黄果原创", original, onVideo) }
        item { HomeVideoSection("热播短剧", hot, onVideo) }
        if (tags.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "热门标签",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tags) { tag ->
                            PhoneChip(tag.name, false) { onTag(tag.slug, tag.name) }
                        }
                    }
                }
            }
        }
        if (topics.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "专题推荐",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(topics) { topic ->
                            Column(
                                Modifier.width(160.dp).clickable { onTopic(topic.slug, topic.title) }
                            ) {
                                CoverImage(
                                    topic.cover,
                                    Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Text(
                                    topic.title,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${topic.count} 部", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HomeVideoSection(title: String, videos: List<Video>, onVideo: (Video) -> Unit) {
    if (videos.isEmpty()) return
    Column {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                Column(
                    Modifier.width(116.dp).clickable { onVideo(video) }
                ) {
                    Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(10.dp))) {
                        CoverImage(video.cover, Modifier.fillMaxSize())
                        if (video.score > 0) {
                            Text(
                                "%.1f".format(video.score),
                                color = Amber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        video.title,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BlogFeed(onPost: (String) -> Unit) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(page) {
        loading = true
        error = null
        runCatching { Api.blog(page) }
            .onSuccess { result ->
                posts = if (page == 1) result.items else posts + result.items
                pages = result.pages
            }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, color = Danger, modifier = Modifier.padding(16.dp)) }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(posts.size) { index ->
                val post = posts[index]
                Column(Modifier.clickable { onPost(post.id) }) {
                    CoverImage(
                        post.cover,
                        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        post.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(post.author, post.created).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted,
                        fontSize = 12.sp
                    )
                    if (post.content.isNotBlank()) {
                        Text(
                            post.content,
                            color = Color(0xFFBDBDBD),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
private fun PhoneTag(
    slug: String,
    title: String,
    onBack: () -> Unit,
    onVideo: (Video) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 20.sp) }
            Text("# $title", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        PagedVideoGrid(
            key = "tag:$slug",
            load = { page -> Api.tagVideos(slug, page) },
            onVideo = onVideo
        )
    }
}

@Composable
private fun VideoFeed(tid: String, onVideo: (Video) -> Unit) {
    var query by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("latest") }
    PagedVideoGrid(
        key = Triple(tid, keyword, sort),
        load = { page ->
            if (keyword.isBlank()) Api.category(tid, page, sort = sort) else Api.search(keyword, page)
        },
        onVideo = onVideo,
        header = {
            Column {
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
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhoneChip("最新更新", sort == "latest") { sort = "latest" }
                    PhoneChip("当前热播", sort == "hot") { sort = "hot" }
                }
            }
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
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp))
        ) {
            CoverImage(video.cover, Modifier.fillMaxSize())
            if (video.score > 0) {
                Text(
                    "%.1f".format(video.score),
                    color = Amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (video.episodeCount > 0) {
                Text(
                    "${video.episodeCount} 集",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
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
private fun TopicFeed(onTopic: (String, String) -> Unit) {
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.topics() }
            .onSuccess { topics = it }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, color = Danger, modifier = Modifier.padding(16.dp)) }
        if (loading) {
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
            gridItems(topics) { topic ->
                Column(Modifier.clickable { onTopic(topic.slug, topic.title) }) {
                    CoverImage(
                        topic.cover,
                        Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(topic.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${topic.count} 部", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PhoneTopic(
    slug: String,
    title: String,
    onBack: () -> Unit,
    onVideo: (Video) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 20.sp) }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        PagedVideoGrid(
            key = "topic:$slug",
            load = { page -> Api.topicVideos(slug, page) },
            onVideo = onVideo
        )
    }
}

@Composable
private fun MineFeed(onHistory: (String, String, Int) -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf<List<Store.Favorite>>(emptyList()) }
    var history by remember { mutableStateOf<List<Store.History>>(emptyList()) }
    fun refresh() {
        favorites = Store.favorites(context)
        history = Store.history(context)
    }
    LaunchedEffect(Unit) { refresh() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("继续观看", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = {
                    Store.clearHistory(context)
                    refresh()
                }) { Text("清空") }
            }
        }
        if (history.isEmpty()) {
            Text("还没有观看记录", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { item ->
                    Column(
                        Modifier.width(130.dp).clickable { onHistory(item.id, item.title, item.ep) }
                    ) {
                        CoverImage(
                            item.cover,
                            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(item.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("第${item.ep}集", color = Amber, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("我的收藏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (favorites.isEmpty()) {
            Text("还没有收藏", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                gridItems(favorites) { item ->
                    Column(
                        Modifier.clickable { onHistory(item.id, item.title, 1) }
                    ) {
                        CoverImage(
                            item.cover,
                            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("收藏和记录仅保存在本机", color = Muted, fontSize = 12.sp)
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
                        Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))
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
    startEp: Int?,
    onBack: () -> Unit,
    onPlay: (Video, Int) -> Unit,
    onOpen: (Video) -> Unit
) {
    val context = LocalContext.current
    var video by remember { mutableStateOf<Video?>(null) }
    var related by remember { mutableStateOf<List<Video>>(emptyList()) }
    var favorite by remember { mutableStateOf(Store.isFavorite(context, id)) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var autoStarted by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LaunchedEffect(id) {
        runCatching { Api.detail(id) }
            .onSuccess { video = it }
            .onFailure { error = it.message }
        loading = false
        runCatching { Api.related(id) }
            .onSuccess { list -> related = list.filter { it.id != id }.take(12) }
    }

    LaunchedEffect(video, startEp) {
        val v = video
        if (!autoStarted && v != null && startEp != null) {
            autoStarted = true
            onPlay(v, startEp)
        }
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
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
                        Modifier.width(124.dp).height(172.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(v.remarks, color = Amber, fontSize = 13.sp)
                        Text(
                            listOfNotNull(
                                v.year.takeIf { it.isNotBlank() },
                                v.author.takeIf { it.isNotBlank() },
                                "${v.episodeCount} 集",
                                if (v.finished) "已完结" else "连载中"
                            ).joinToString(" · "),
                            color = Muted,
                            fontSize = 12.sp
                        )
                        if (v.hot > 0 || v.playCount > 0) {
                            Text(
                                "热度 ${formatCount(v.hot)} · 播放 ${formatCount(v.playCount)}",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (v.tags.isNotEmpty()) {
                            Text(v.tags.joinToString(" / "), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlay(v, startEp ?: v.episodes.firstOrNull()?.num ?: 1) },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (startEp != null) "继续播放第${startEp}集" else "播放第 1 集") }
                    IconButton(onClick = {
                        favorite = Store.toggleFavorite(context, v)
                    }) {
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (favorite) Amber else Color.White
                        )
                    }
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
                if (related.isNotEmpty()) {
                    Text(
                        "猜你喜欢",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(related) { item ->
                            Column(
                                Modifier.width(120.dp).clickable { onOpen(item) }
                            ) {
                                CoverImage(
                                    item.cover,
                                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Text(item.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
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
    val context = LocalContext.current
    val player = rememberExoPlayer()
    val activity = remember(context) { context.findActivity() }
    var ep by remember { mutableIntStateOf(initialEp) }
    var speed by remember { mutableFloatStateOf(1f) }
    var tempSpeed by remember { mutableStateOf<Float?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragCurrent by remember { mutableStateOf(0L) }
    BackHandler(onBack = onBack)

    fun applyOrientation(land: Boolean) {
        landscape = land
        val a = activity ?: return
        a.requestedOrientation = if (land) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (land) hideSystemBars(a) else showSystemBars(a)
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let { showSystemBars(it) }
        }
    }

    val poke: () -> Unit = {
        controlsVisible = true
        interaction += 1
    }

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
                Store.addHistory(context, video, ep)
            }
            .onFailure { error = it.message ?: "解析失败" }
        loading = false
        poke()
    }

    LaunchedEffect(speed, tempSpeed) {
        player.setPlaybackSpeed(tempSpeed ?: speed)
    }

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

    LaunchedEffect(controlsVisible, interaction, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3800)
            controlsVisible = false
        }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            controlsVisible = true
            interaction += 1
        }
    }
    LaunchedEffect(pressed, dragging) {
        if (pressed && !dragging) {
            delay(380)
            if (pressed && !dragging) tempSpeed = 2f
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, showController = false, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                            if (tempSpeed != null) tempSpeed = null
                        },
                        onTap = {
                            controlsVisible = !controlsVisible
                            interaction += 1
                        }
                    )
                }
                .pointerInput(player) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragCurrent = player.currentPosition
                            controlsVisible = true
                            interaction += 1
                            if (tempSpeed != null) tempSpeed = null
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val dur = player.duration.coerceAtLeast(0L)
                            dragCurrent = (dragCurrent + (amount / size.width * dur).toLong())
                                .coerceIn(0L, dur)
                        },
                        onDragEnd = {
                            player.seekTo(dragCurrent)
                            position = dragCurrent
                            dragging = false
                            interaction += 1
                        },
                        onDragCancel = { dragging = false }
                    )
                }
        )

        if (controlsVisible) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
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
                Text(
                    if (landscape) "竖屏" else "横屏",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            applyOrientation(!landscape)
                            poke()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(if (dragging) dragCurrent else position), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = (if (dragging) dragCurrent else position).toFloat(),
                        onValueChange = { value ->
                            position = value.toLong()
                            player.seekTo(position)
                            interaction += 1
                        },
                        valueRange = 0f..(duration.coerceAtLeast(1L)).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { ep = (ep - 1).coerceAtLeast(1); poke() }, enabled = ep > 1) {
                        Text("上一集", color = Color.White)
                    }
                    TextButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); poke() }) {
                        Text("-10s", color = Color.White)
                    }
                    Button(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                            poke()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)
                    ) {
                        Text(if (isPlaying) "❚❚" else "▶", fontSize = 18.sp)
                    }
                    TextButton(onClick = { player.seekTo(player.currentPosition + 10_000); poke() }) {
                        Text("+10s", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            ep = (ep + 1).coerceAtMost(video.episodes.maxOfOrNull { it.num } ?: ep)
                            poke()
                        },
                        enabled = ep < (video.episodes.maxOfOrNull { it.num } ?: ep)
                    ) {
                        Text("下一集", color = Color.White)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box {
                        Text(
                            "倍速 ${speedLabel(speed)} ▾",
                            color = Amber,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    poke()
                                    showSpeed = true
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                        DropdownMenu(expanded = showSpeed, onDismissRequest = { showSpeed = false }) {
                            SPEEDS.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(speedLabel(value)) },
                                    onClick = {
                                        speed = value
                                        showSpeed = false
                                        poke()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "长按屏幕可 2 倍速",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "共 ${video.episodes.size} 集",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(video.episodes) { episode ->
                        Text(
                            "${episode.num}",
                            color = if (episode.num == ep) Color.Black else Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (episode.num == ep) Amber else Color(0xFF222222))
                                .clickable {
                                    ep = episode.num
                                    poke()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (tempSpeed != null) {
            Text(
                "2.0x 倍速中",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (dragging) {
            Text(
                "${formatTime(dragCurrent)} / ${formatTime(duration)}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
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
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(stripHtml(p.content), fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is Activity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}

private fun hideSystemBars(activity: Activity) {
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

private fun showSystemBars(activity: Activity) {
    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}
