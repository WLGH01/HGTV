@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wlgh.hgtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wlgh.hgtv.data.Api
import com.wlgh.hgtv.data.Playback
import com.wlgh.hgtv.data.Store
import com.wlgh.hgtv.data.Tag
import com.wlgh.hgtv.data.Topic
import com.wlgh.hgtv.data.Video
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
import kotlinx.coroutines.delay

private sealed interface TvRoute {
    data object Home : TvRoute
    data class Detail(val id: String, val title: String) : TvRoute
    data class Player(val video: Video, val ep: Int) : TvRoute
    data class Topic(val slug: String, val title: String) : TvRoute
    data class Tag(val slug: String, val title: String) : TvRoute
}

private data class TvNavItem(val label: String, val icon: ImageVector)

/** 大屏基础缩放：TV 版原始设计偏大，统一缩到约 78%，用户比例设置在此基础上再乘。 */
private const val TV_BASE_SCALE = 0.78f

private val TV_NAV = listOf(
    TvNavItem("首页", Icons.Filled.Home),
    TvNavItem("短剧", Icons.Filled.PlayArrow),
    TvNavItem("漫剧", Icons.Filled.PlayArrow),
    TvNavItem("原创", Icons.Filled.Star),
    TvNavItem("排行榜", Icons.Filled.Star),
    TvNavItem("专题", Icons.Filled.List),
    TvNavItem("我的", Icons.Filled.Person),
    TvNavItem("设置", Icons.Filled.Settings)
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(Store.uiScale(context)) }
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * TV_BASE_SCALE * scale, base.fontScale)
    ) {
        TvRoot(
            scale = scale,
            onScaleChange = { value ->
                scale = value
                Store.setUiScale(context, value)
            }
        )
    }
}

@Composable
private fun TvRoot(scale: Float, onScaleChange: (Float) -> Unit) {
    var route by remember { mutableStateOf<TvRoute>(TvRoute.Home) }
    when (val current = route) {
        TvRoute.Home -> TvHome(
            scale = scale,
            onScaleChange = onScaleChange,
            onVideo = { route = TvRoute.Detail(it.id, it.title) },
            onTopic = { slug, title -> route = TvRoute.Topic(slug, title) },
            onTag = { slug, title -> route = TvRoute.Tag(slug, title) }
        )
        is TvRoute.Detail -> TvDetail(
            id = current.id,
            title = current.title,
            onBack = { route = TvRoute.Home },
            onPlay = { video, ep -> route = TvRoute.Player(video, ep) },
            onOpen = { route = TvRoute.Detail(it.id, it.title) }
        )
        is TvRoute.Player -> TvPlayer(
            video = current.video,
            initialEp = current.ep,
            onBack = { route = TvRoute.Detail(current.video.id, current.video.title) }
        )
        is TvRoute.Topic -> TvTopic(
            slug = current.slug,
            title = current.title,
            onBack = { route = TvRoute.Home },
            onVideo = { route = TvRoute.Detail(it.id, it.title) }
        )
        is TvRoute.Tag -> TvTopic(
            slug = current.slug,
            title = "# ${current.title}",
            onBack = { route = TvRoute.Home },
            onVideo = { route = TvRoute.Detail(it.id, it.title) },
            isTag = true
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
private fun TvHome(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onVideo: (Video) -> Unit,
    onTopic: (String, String) -> Unit,
    onTag: (String, String) -> Unit
) {
    var nav by remember { mutableIntStateOf(0) }
    var rankType by remember { mutableStateOf("hot") }
    var homeTrend by remember { mutableIntStateOf(0) }
    var catTrend by remember { mutableIntStateOf(0) }
    var featured by remember { mutableStateOf<List<Video>>(emptyList()) }
    var latest by remember { mutableStateOf<List<Video>>(emptyList()) }
    var hot by remember { mutableStateOf<List<Video>>(emptyList()) }
    var original by remember { mutableStateOf<List<Video>>(emptyList()) }
    var ranks by remember { mutableStateOf<List<Video>>(emptyList()) }
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var history by remember { mutableStateOf<List<Store.History>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Store.Favorite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(nav, rankType) {
        loading = true
        error = null
        runCatching {
            when (nav) {
                0 -> {
                    featured = Api.featured(20)
                    latest = Api.category("ai-duanju", 1, 20, "latest").items
                    original = Api.original(1, 20).items
                    hot = Api.category("ai-duanju", 1, 20, "hot").items
                    topics = Api.topics()
                    tags = Api.tagCategories().flatMap { it.tags }.take(24)
                }
                1 -> {
                    latest = Api.category("ai-duanju", 1, 40, "latest").items
                    hot = Api.category("ai-duanju", 1, 40, "hot").items
                }
                2 -> {
                    latest = Api.category("ai-manju", 1, 40, "latest").items
                    hot = Api.category("ai-manju", 1, 40, "hot").items
                }
                3 -> original = Api.original(1, 60).items
                4 -> ranks = Api.ranks(rankType, 1, 60).items
                5 -> topics = Api.topics()
                6 -> {
                    history = Store.history(context)
                    favorites = Store.favorites(context)
                }
            }
        }.onFailure { error = it.message }
        loading = false
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        TvRail(TV_NAV, nav) { nav = it }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TV_NAV[nav].label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(Modifier.width(12.dp))
                Text("黄果原生 TV", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber)
                    }
                    error != null -> Text("加载失败：$error", color = Danger, fontSize = 15.sp)
                    nav == 0 -> TvHomeFeed(
                        featured, latest, hot, original, tags, topics, homeTrend,
                        { homeTrend = it }, onVideo, onTopic, onTag
                    )
                    nav == 1 || nav == 2 -> TvCategoryPage(latest, hot, catTrend, { catTrend = it }, onVideo)
                    nav == 3 -> TvVideoGrid(original, onVideo)
                    nav == 4 -> TvRankPage(rankType, ranks, { rankType = it }, onVideo)
                    nav == 5 -> TvTopics(topics, onTopic)
                    nav == 6 -> TvMine(history, favorites, onVideo)
                    else -> TvSettings(scale, onScaleChange)
                }
            }
        }
    }
}

@Composable
private fun TvHomeFeed(
    featured: List<Video>,
    latest: List<Video>,
    hot: List<Video>,
    original: List<Video>,
    tags: List<Tag>,
    topics: List<Topic>,
    trend: Int,
    onTrend: (Int) -> Unit,
    onVideo: (Video) -> Unit,
    onTopic: (String, String) -> Unit,
    onTag: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 14.dp)
    ) {
        item { TvSection("精选推荐", featured, onVideo) }
        item {
            Column {
                TvTabRow(listOf("最近上新", "热播短剧"), trend, onTrend)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowItemsIndexed(if (trend == 0) latest else hot) { _, video ->
                        TvVideoCard(video, onVideo)
                    }
                }
            }
        }
        item { TvSection("黄果原创", original, onVideo) }
        if (tags.isNotEmpty()) item { TvTagRow(tags, onTag) }
        if (topics.isNotEmpty()) item { TvTopicRow(topics, onTopic) }
    }
}

@Composable
private fun TvTabRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { index, label ->
            TvSmallButton(label, selected = index == selected) { onSelect(index) }
        }
    }
}

@Composable
private fun TvCategoryPage(
    latest: List<Video>,
    hot: List<Video>,
    trend: Int,
    onTrend: (Int) -> Unit,
    onVideo: (Video) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TvTabRow(listOf("最近上新", "热播"), trend, onTrend)
        Spacer(Modifier.height(12.dp))
        TvVideoGrid(if (trend == 0) latest else hot, onVideo, Modifier.weight(1f))
    }
}

@Composable
private fun TvRankPage(
    rankType: String,
    ranks: List<Video>,
    onType: (String) -> Unit,
    onVideo: (Video) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("热播" to "hot", "推荐" to "recommend", "潜力" to "potential")
                .forEach { (label, value) ->
                    TvSmallButton(label, selected = rankType == value) { onType(value) }
                }
        }
        Spacer(Modifier.height(12.dp))
        TvVideoGrid(ranks, onVideo, Modifier.weight(1f))
    }
}

@Composable
private fun TvVideoGrid(
    videos: List<Video>,
    onVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Text("暂无内容", color = Muted, fontSize = 14.sp)
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = (maxWidth.value / 200f).toInt().coerceIn(3, 6)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            gridItems(videos) { video -> TvGridCard(video, onVideo) }
        }
    }
}

@Composable
private fun TvGridCard(video: Video, onVideo: (Video) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "grid-card-scale")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoView.bringIntoView() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey { onVideo(video) }
            .background(Color(0xFF151515), RoundedCornerShape(12.dp))
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Amber else Color(0xFF2A2A2A),
                RoundedCornerShape(12.dp)
            )
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(9.dp))) {
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
            if (video.episodeCount > 0) {
                Text(
                    "${video.episodeCount} 集",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            video.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (video.remarks.isNotBlank()) {
            Text(video.remarks, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvRail(items: List<TvNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val first = remember { FocusRequester() }
    Column(
        Modifier
            .width(168.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF151515), Color(0xFF0A0A0A))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("黄果", color = Amber, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("解析 · TV", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        items.forEachIndexed { index, item ->
            TvRailItem(item, index == selected, if (index == 0) first else null) { onSelect(index) }
        }
    }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun TvRailItem(
    item: TvNavItem,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "rail-scale")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoView.bringIntoView() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoView)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey(onClick)
            .background(
                if (selected || focused) Color(0xFF252525) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .border(if (focused) 2.dp else 0.dp, Amber, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) Amber else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            item.label,
            color = if (selected) Amber else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TvSection(title: String, videos: List<Video>, onVideo: (Video) -> Unit) {
    if (videos.isEmpty()) return
    Column {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rowItemsIndexed(videos) { _, video ->
                TvVideoCard(video, onVideo)
            }
        }
    }
}

@Composable
private fun TvVideoCard(video: Video, onVideo: (Video) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "card-scale")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoView.bringIntoView() }
    }
    Column(
        Modifier
            .width(158.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvKey { onVideo(video) }
            .background(Color(0xFF151515), RoundedCornerShape(12.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) Amber else Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(9.dp))) {
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
            if (video.episodeCount > 0) {
                Text(
                    "${video.episodeCount} 集",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            video.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(video.remarks, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun TvTagRow(tags: List<Tag>, onTag: (String, String) -> Unit) {
    Column {
        Text("热门标签", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowItemsIndexed(tags) { _, tag ->
                TvSmallButton(tag.name) { onTag(tag.slug, tag.name) }
            }
        }
    }
}

@Composable
private fun TvTopicRow(topics: List<Topic>, onTopic: (String, String) -> Unit) {
    Column {
        Text("专题推荐", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowItemsIndexed(topics) { _, topic ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .width(206.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .tvKey { onTopic(topic.slug, topic.title) }
                        .border(if (focused) 3.dp else 1.dp, if (focused) Amber else Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                        .padding(7.dp)
                ) {
                    CoverImage(
                        topic.cover,
                        Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Text(topic.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${topic.count} 部", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TvTopics(topics: List<Topic>, onTopic: (String, String) -> Unit) {
    if (topics.isEmpty()) {
        Text("暂无专题", color = Muted)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(topics.size) { index ->
            val topic = topics[index]
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .tvKey { onTopic(topic.slug, topic.title) }
                    .background(if (focused) Color(0xFF242424) else Color(0xFF161616), RoundedCornerShape(12.dp))
                    .border(if (focused) 3.dp else 0.dp, Amber, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    topic.cover,
                    Modifier.width(180.dp).height(100.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(topic.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${topic.count} 部作品", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TvMine(
    history: List<Store.History>,
    favorites: List<Store.Favorite>,
    onVideo: (Video) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("继续观看", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("还没有观看记录", color = Muted, fontSize = 14.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowItemsIndexed(history) { _, item ->
                        var focused by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .width(170.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .focusable()
                                .tvKey {
                                    onVideo(
                                        Video(
                                            id = item.id,
                                            title = item.title,
                                            cover = item.cover,
                                            remarks = "第${item.ep}集"
                                        )
                                    )
                                }
                                .border(if (focused) 3.dp else 1.dp, if (focused) Amber else Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                                .padding(6.dp)
                        ) {
                            CoverImage(
                                item.cover,
                                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(item.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("第${item.ep}集", color = Amber, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("我的收藏", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (favorites.isEmpty()) {
                Text("还没有收藏", color = Muted, fontSize = 14.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowItemsIndexed(favorites) { _, item ->
                        var focused by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .width(170.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .focusable()
                                .tvKey {
                                    onVideo(
                                        Video(
                                            id = item.id,
                                            title = item.title,
                                            cover = item.cover,
                                            remarks = ""
                                        )
                                    )
                                }
                                .border(if (focused) 3.dp else 1.dp, if (focused) Amber else Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                                .padding(6.dp)
                        ) {
                            CoverImage(
                                item.cover,
                                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(item.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettings(scale: Float, onScaleChange: (Float) -> Unit) {
    val options = listOf(0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f)
    Column(Modifier.fillMaxSize()) {
        Text("界面比例", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("调整整个 TV 界面的字号和控件大小，设置会保存在本机。", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                TvSmallButton(
                    "${(option * 100).toInt()}%",
                    selected = kotlin.math.abs(scale - option) < 0.01f
                ) { onScaleChange(option) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("当前：${(scale * 100).toInt()}%", color = Amber, fontSize = 15.sp)
    }
}

@Composable
private fun TvDetail(
    id: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Video, Int) -> Unit,
    onOpen: (Video) -> Unit
) {
    var video by remember { mutableStateOf<Video?>(null) }
    var related by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorite by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    LaunchedEffect(id) {
        runCatching { Api.detail(id) }
            .onSuccess { video = it; favorite = Store.isFavorite(context, id) }
            .onFailure { error = it.message }
        runCatching { Api.related(id) }
            .onSuccess { list -> related = list.filter { it.id != id }.take(12) }
        loading = false
    }
    LaunchedEffect(video) {
        if (video != null) {
            delay(200)
            runCatching { playFocus.requestFocus() }
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        return
    }
    val v = video
    if (v == null) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
            Text("详情读取失败：${error ?: "未知错误"}", color = Danger)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                CoverImage(
                    v.cover,
                    Modifier.width(240.dp).height(330.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
                    Text(v.title, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            v.remarks.takeIf { it.isNotBlank() },
                            v.year.takeIf { it.isNotBlank() },
                            v.author.takeIf { it.isNotBlank() }?.let { "作者 $it" },
                            "${v.episodeCount} 集",
                            if (v.finished) "已完结" else "连载中"
                        ).joinToString(" · "),
                        color = Muted,
                        fontSize = 15.sp
                    )
                    if (v.hot > 0 || v.playCount > 0) {
                        Text(
                            "热度 ${formatCount(v.hot)} · 播放 ${formatCount(v.playCount)} · 收藏 ${formatCount(v.collectCount)}",
                            color = Muted,
                            fontSize = 14.sp
                        )
                    }
                    if (v.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(v.tags.joinToString("  ") { "#$it" }, color = Amber, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (v.desc.isNotBlank()) {
                        Text(
                            v.desc,
                            color = Color(0xFFCCCCCC),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction(
                            "播放第 ${v.episodes.firstOrNull()?.num ?: 1} 集",
                            primary = true,
                            focusRequester = playFocus
                        ) { onPlay(v, v.episodes.firstOrNull()?.num ?: 1) }
                        TvAction(if (favorite) "已收藏" else "收藏") {
                            favorite = Store.toggleFavorite(context, v)
                        }
                    }
                }
            }
        }
        item {
            Text("选集 · ${v.episodes.size} 集", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItemsIndexed(v.episodes) { _, episode ->
                    TvSmallButton("${episode.num}") { onPlay(v, episode.num) }
                }
            }
        }
        if (related.isNotEmpty()) {
            item { TvSection("猜你喜欢", related, onOpen) }
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
    var menuVisible by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(true) }
    var seekOsd by remember { mutableStateOf(false) }
    var seekOsdAt by remember { mutableLongStateOf(0L) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val resumeFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val context = LocalContext.current

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
        seekPosition = target
        if (!menuVisible) {
            seekOsd = true
            seekOsdAt += 1
        }
    }

    fun openMenu() {
        menuVisible = true
        player.pause()
    }

    fun closeMenu(resume: Boolean) {
        menuVisible = false
        showSpeed = false
        showEpisodes = false
        if (resume) player.play()
    }

    BackHandler {
        if (menuVisible) closeMenu(true) else onBack()
    }

    LaunchedEffect(ep) {
        loading = true
        error = null
        menuVisible = false
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
        runCatching { rootFocus.requestFocus() }
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
    LaunchedEffect(player) {
        while (true) {
            seekPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(menuVisible) {
        delay(80)
        if (menuVisible) {
            runCatching { resumeFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(showSpeed) {
        if (showSpeed) {
            delay(60)
            runCatching { speedFocus.requestFocus() }
        }
    }
    LaunchedEffect(hintVisible) {
        if (hintVisible) {
            delay(4500)
            hintVisible = false
        }
    }
    LaunchedEffect(seekOsdAt) {
        if (seekOsd) {
            delay(1400)
            seekOsd = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val center = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Spacebar
                when {
                    !menuVisible && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                        seekBy(if (event.key == Key.DirectionLeft) -10_000 else 10_000)
                        true
                    }
                    !menuVisible && center -> {
                        openMenu()
                        true
                    }
                    else -> false
                }
            }
    ) {
        VideoSurface(player, showController = false, modifier = Modifier.fillMaxSize())

        if (!menuVisible) {
            if (seekOsd) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.14f))
                    ) {
                        val fraction = if (duration > 0) (seekPosition.toFloat() / duration) else 0f
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp)
                                .background(Amber.copy(alpha = 0.85f))
                        )
                    }
                    Text(
                        "${formatTime(seekPosition)} / ${formatTime(duration)}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 16.dp)
                    )
                }
            }
            if (hintVisible && !loading) {
                Text(
                    "左右键 快进/快退 · 中键 暂停/设置",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 18.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        if (menuVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    "${video.title} · 第${ep}集",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(seekPosition), color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.weight(1f).height(4.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    ) {
                        val fraction = if (duration > 0) (seekPosition.toFloat() / duration) else 0f
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp)
                                .background(Amber, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    TvSmallButton(
                        text = "继续播放",
                        focusRequester = resumeFocus
                    ) { closeMenu(true) }
                    TvSmallButton("快退 30s") { seekBy(-30_000) }
                    TvSmallButton("快进 30s") { seekBy(30_000) }
                    TvSmallButton("上一集", enabled = ep > 1) { ep -= 1 }
                    TvSmallButton(
                        "下一集",
                        enabled = ep < (video.episodes.maxOfOrNull { it.num } ?: ep)
                    ) { ep += 1 }
                    TvSmallButton("倍速 ${speedLabel(speed)}", selected = showSpeed) {
                        showSpeed = !showSpeed
                        showEpisodes = false
                    }
                    TvSmallButton("选集", selected = showEpisodes) {
                        showEpisodes = !showEpisodes
                        showSpeed = false
                    }
                }
                if (showSpeed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("倍速", color = Muted, fontSize = 12.sp)
                        SPEEDS.forEachIndexed { index, value ->
                            TvSmallButton(
                                speedLabel(value),
                                selected = speed == value,
                                focusRequester = if (index == 0) speedFocus else null
                            ) { speed = value }
                        }
                    }
                }
                if (showEpisodes) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItemsIndexed(video.episodes) { _, episode ->
                            TvSmallButton("${episode.num}", selected = episode.num == ep) {
                                ep = episode.num
                            }
                        }
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
private fun TvTopic(
    slug: String,
    title: String,
    onBack: () -> Unit,
    onVideo: (Video) -> Unit,
    isTag: Boolean = false
) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(page) {
        loading = true
        error = null
        runCatching {
            if (isTag) {
                val result = Api.tagVideos(slug, page)
                videos = if (page == 1) result.items else videos + result.items
                pages = result.pages
            } else {
                val result = Api.topicVideos(slug, page)
                videos = if (page == 1) result.items else videos + result.items
                pages = result.pages
            }
        }.onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回", color = Color.White) }
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && videos.isEmpty() -> CircularProgressIndicator(color = Amber)
                videos.isEmpty() -> Text("暂无内容", color = Muted, fontSize = 14.sp)
                else -> TvVideoGrid(videos, onVideo)
            }
        }
        if (page < pages) {
            Spacer(Modifier.height(8.dp))
            TvSmallButton("加载更多") { page += 1 }
        }
        error?.let { Text(it, color = Danger) }
    }
}

@Composable
private fun TvAction(
    text: String,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "action-scale")
    Text(
        text = text,
        color = if (primary) Color.Black else Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocus?.invoke(it.isFocused)
            }
            .focusable()
            .tvKey(onClick)
            .background(if (primary) Amber else Color(0xFF1C1C1C), RoundedCornerShape(10.dp))
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color(0xFF333333),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 22.dp, vertical = 11.dp)
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
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "small-scale")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoView.bringIntoView() }
    }
    Text(
        text = text,
        color = when {
            !enabled -> Color(0xFF666666)
            selected -> Color.Black
            else -> Color.White
        },
        fontSize = 14.sp,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoView)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .tvKey { if (enabled) onClick() }
            .background(if (selected) Amber else Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Amber else Color(0xFF333333),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
