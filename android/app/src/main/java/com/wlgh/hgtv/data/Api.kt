package com.wlgh.hgtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 源站 JSON 接口。网页版是浏览器直连 + CORS；原生端没有 CORS 限制，直接请求即可。
 * 注意：站点 2026-11 后只剩 ai-duanju / ai-manju 两个分类，排序只支持 latest / hot。
 */
object Api {
    const val SITE = "https://huangguoai.io"
    const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json,text/plain,*/*")
            .header("Referer", SITE + "/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private suspend fun getJson(path: String): JSONObject =
        JSONObject(getText(SITE + path))

    /** 首页精选推荐：站点 /recommend 页是服务端渲染，直接解析卡片。 */
    suspend fun featured(limit: Int = 20): List<Video> =
        parseHtmlVideoCards(getText("$SITE/recommend")).take(limit)

    /** 黄果原创：官方列表接口支持 is_original=1。 */
    suspend fun original(page: Int, size: Int = 50): VideoPage =
        parsePage(getJson("/api/videos?page=$page&size=$size&is_original=1"))

    /** 标签筛选：站点只有 /tag/{slug}/ 页面，解析服务端渲染卡片。 */
    suspend fun tagVideos(slug: String, page: Int, size: Int = 50): VideoPage {
        val url = if (page <= 1) "$SITE/tag/$slug/" else "$SITE/tag/$slug/page/$page/"
        val html = getText(url)
        val items = parseHtmlVideoCards(html).take(size)
        val pages = Regex("/tag/$slug/page/(\\d+)/").findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 1
        return VideoPage(items, page, pages)
    }

    /** 博客列表：/blog/ 与 /blog/page/N/ 服务端渲染。 */
    suspend fun blog(page: Int, size: Int = 50): PostPage {
        val url = if (page <= 1) "$SITE/blog/" else "$SITE/blog/page/$page/"
        val html = getText(url)
        val items = parseHtmlPosts(html).take(size)
        val pages = Regex("/blog/page/(\\d+)/").findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 1
        return PostPage(items, page, pages)
    }

    /** 热门标签分类。 */
    suspend fun tagCategories(): List<TagCategory> {
        val data = getJson("/api/tags").optJSONObject("data") ?: return emptyList()
        val categories = data.optJSONArray("categories") ?: return emptyList()
        val out = ArrayList<TagCategory>(categories.length())
        for (i in 0 until categories.length()) {
            val c = categories.optJSONObject(i) ?: continue
            val tagsArr = c.optJSONArray("tags") ?: JSONArray()
            val tags = ArrayList<Tag>(tagsArr.length())
            for (j in 0 until tagsArr.length()) {
                val t = tagsArr.optJSONObject(j) ?: continue
                val slug = str(t, "slug")
                val name = str(t, "name")
                if (slug.isNotBlank() && name.isNotBlank()) tags += Tag(name, slug)
            }
            if (tags.isNotEmpty()) {
                out += TagCategory(str(c, "name").ifBlank { "标签" }, tags)
            }
        }
        return out
    }

    /** 视频详情的“猜你喜欢”。 */
    suspend fun related(id: String, size: Int = 12): List<Video> {
        val data = getJson("/api/videos/${enc(id)}/related?page=1&size=$size")
            .optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Video>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out += parseVideo(it) }
        }
        return out
    }

    suspend fun category(
        tid: String,
        page: Int,
        size: Int = 50,
        sort: String = "latest"
    ): VideoPage = parsePage(
        getJson("/api/videos/category/$tid?page=$page&size=$size&sort=$sort")
    )

    suspend fun search(keyword: String, page: Int, size: Int = 50): VideoPage = parsePage(
        getJson("/api/search?q=${enc(keyword)}&page=$page&size=$size")
    )

    suspend fun ranks(type: String, page: Int, size: Int = 50): VideoPage {
        val data = getJson("/api/ranks/$type?page=$page&size=$size").optJSONObject("data")
            ?: return VideoPage(emptyList(), 1, 1)
        val arr = data.optJSONArray("items") ?: JSONArray()
        val pg = data.optJSONObject("pagination") ?: JSONObject()
        val items = ArrayList<Video>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { items += parseVideo(it, rank = true) }
        }
        return VideoPage(items, pg.optInt("page", 1), pg.optInt("pages", 1))
    }

    suspend fun detail(id: String): Video {
        val data = getJson("/api/videos/${enc(id)}").optJSONObject("data")
            ?: throw IOException("详情为空")
        val video = parseVideo(data)
        val episodes = parseEpisodes(data)
        return video.copy(
            episodes = episodes.ifEmpty {
                (1..video.episodeCount).map { Episode(it) }
            }
        )
    }

    suspend fun topics(): List<Topic> {
        val data = getJson("/api/topics").optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Topic>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += Topic(
                slug = str(o, "slug"),
                title = cleanTitle(str(o, "title")),
                cover = absUrl(str(o, "cover")),
                count = o.optInt("video_count", 0)
            )
        }
        return out
    }

    suspend fun topicVideos(slug: String, page: Int, size: Int = 50): VideoPage {
        val data = getJson("/api/topics/${enc(slug)}?page=$page&size=$size").optJSONObject("data")
            ?: return VideoPage(emptyList(), 1, 1)
        val videos = data.optJSONObject("videos") ?: return VideoPage(emptyList(), 1, 1)
        val arr = videos.optJSONArray("items") ?: JSONArray()
        val pg = videos.optJSONObject("pagination") ?: JSONObject()
        val items = ArrayList<Video>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { items += parseVideo(it) }
        }
        return VideoPage(items, pg.optInt("page", 1), pg.optInt("pages", 1))
    }

    suspend fun posts(page: Int, size: Int = 50): PostPage {
        val data = getJson("/api/posts?page=$page&size=$size").optJSONObject("data")
            ?: return PostPage(emptyList(), 1, 1)
        val arr = data.optJSONArray("items") ?: JSONArray()
        val pg = data.optJSONObject("pagination") ?: JSONObject()
        val items = ArrayList<Post>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { items += parsePost(it) }
        }
        return PostPage(items, pg.optInt("page", 1), pg.optInt("pages", 1))
    }

    suspend fun post(id: String): Post {
        val data = getJson("/api/posts/${enc(id)}").optJSONObject("data")
            ?: throw IOException("吃瓜详情为空")
        return parsePost(data)
    }

    private fun parsePage(root: JSONObject): VideoPage {
        val data = root.optJSONObject("data") ?: return VideoPage(emptyList(), 1, 1)
        val arr = data.optJSONArray("items") ?: JSONArray()
        val pg = data.optJSONObject("pagination") ?: JSONObject()
        val items = ArrayList<Video>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { items += parseVideo(it) }
        }
        return VideoPage(items, pg.optInt("page", 1), pg.optInt("pages", 1))
    }

    private fun parseVideo(o: JSONObject, rank: Boolean = false): Video {
        val episodeCount = o.optInt("episode_count", 0)
        val totalEpisodes = o.optInt("total_episodes", 0)
        val baseRemarks = remarks(o)
        val finalRemarks = if (rank) {
            val metric = listOf(str(o, "metric_label"), str(o, "metric_value"))
                .filter { it.isNotBlank() && it != "null" }
                .joinToString(" ")
            listOfNotNull(
                o.optInt("rank", 0).takeIf { it > 0 }?.let { "#$it" },
                metric.takeIf { it.isNotBlank() },
                baseRemarks.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
        } else {
            baseRemarks
        }
        val tagsArr = o.optJSONArray("tags")
        val tags = if (tagsArr == null) emptyList() else (0 until tagsArr.length())
            .map { tagsArr.optString(it) }
            .filter { it.isNotBlank() }
        val counts = o.optJSONObject("counts")
        val createdAt = str(o, "created_at")
        return Video(
            id = str(o, "video_id").ifBlank { str(o, "id") },
            title = cleanTitle(str(o, "title")),
            cover = absUrl(str(o, "cover")),
            remarks = finalRemarks,
            score = o.optDouble("score", 0.0),
            desc = str(o, "description"),
            tags = tags,
            episodeCount = maxOf(episodeCount, totalEpisodes),
            finished = o.optBoolean("is_finished", false),
            author = o.optJSONObject("author")?.let { str(it, "name") } ?: "",
            year = createdAt.take(4),
            hot = counts?.optLong("hot", 0L) ?: o.optLong("hot", 0L),
            playCount = counts?.optLong("play", 0L) ?: 0L,
            likeCount = counts?.optLong("like", 0L) ?: 0L,
            collectCount = counts?.optLong("collect", 0L) ?: 0L
        )
    }

    private fun parseEpisodes(o: JSONObject): List<Episode> {
        val arr = o.optJSONArray("episodes") ?: return emptyList()
        val out = ArrayList<Episode>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val num = e.optInt("ep_num", 0)
            if (num > 0) out += Episode(num, e.optInt("duration", 0))
        }
        return out.sortedBy { it.num }
    }

    private fun parsePost(o: JSONObject): Post = Post(
        id = str(o, "id"),
        title = cleanTitle(str(o, "title")),
        cover = absUrl(str(o, "cover")),
        created = str(o, "publish_time").ifBlank { str(o, "created_at") },
        author = o.optJSONObject("author")?.let { str(it, "name") } ?: "",
        content = str(o, "content")
    )

    private fun parseHtmlVideoCards(html: String): List<Video> {
        val starts = Regex("""data-track-item-id="\d+"""").findAll(html)
            .map { it.range.first }.toList()
        val out = ArrayList<Video>(starts.size)
        for (index in starts.indices) {
            val end = if (index + 1 < starts.size) starts[index + 1]
            else minOf(html.length, starts[index] + 9000)
            if (end <= starts[index]) continue
            val block = html.substring(starts[index], end)
            val id = Regex("""data-track-item-id="(\d+)"""").find(block)?.groupValues?.get(1)
                ?: Regex("""/video/(\d+)/""").find(block)?.groupValues?.get(1)
                ?: continue
            val title = cleanTitle(
                Regex("""hg-drama-card__title[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)
                    ?: Regex("""data-track-item-name="([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: ""
            )
            if (title.isBlank()) continue
            val cover = Regex("""data-src="([^"]+)"""").find(block)?.groupValues?.get(1)
                ?: Regex("""src="([^"]+)"""").find(block)?.groupValues?.get(1) ?: ""
            val episode = cleanTitle(
                Regex("""hg-drama-card__episode[^>]*>([^<]+)<""").find(block)?.groupValues?.get(1) ?: ""
            )
            val score = Regex("""hg-drama-card__score[^>]*>([\d.]+)""").find(block)
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val tags = Regex("""class="hg-tag"[^>]*>([^<]+)<""").findAll(block)
                .map { cleanTitle(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .take(3)
                .toList()
            val episodeCount = Regex("""(\d+)""").find(episode)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val finished = episode.contains("全") || episode.contains("完结")
            val remarks = listOf(episode, if (score > 0) "${score}分" else "")
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            out += Video(
                id = id,
                title = title,
                cover = absUrl(cover),
                remarks = remarks,
                score = score,
                tags = tags,
                episodeCount = episodeCount,
                finished = finished
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseHtmlPosts(html: String): List<Post> {
        val starts = Regex("""data-track-item-id="\d+"""").findAll(html)
            .map { it.range.first }.toList()
        val out = ArrayList<Post>(starts.size)
        for (index in starts.indices) {
            val end = if (index + 1 < starts.size) starts[index + 1]
            else minOf(html.length, starts[index] + 7000)
            if (end <= starts[index]) continue
            val block = html.substring(starts[index], end)
            val id = Regex("""data-track-item-id="(\d+)"""").find(block)?.groupValues?.get(1)
                ?: Regex("""/archives/(\d+)/""").find(block)?.groupValues?.get(1)
                ?: continue
            val title = cleanTitle(
                Regex("""hg-cate-feed__title[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1) ?: ""
            )
            if (title.isBlank()) continue
            val cover = Regex("""data-src="([^"]+)"""").find(block)?.groupValues?.get(1) ?: ""
            val author = cleanTitle(
                Regex("""hg-cate-feed__author[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1) ?: ""
            )
            val date = cleanTitle(
                Regex("""hg-cate-feed__date[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1) ?: ""
            )
            val intro = cleanTitle(
                Regex("""hg-cate-feed__intro[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1) ?: ""
            )
            out += Post(
                id = id,
                title = title,
                cover = absUrl(cover),
                created = date,
                author = author,
                content = intro
            )
        }
        return out.distinctBy { it.id }
    }

    private fun remarks(o: JSONObject): String {
        val parts = mutableListOf<String>()
        val episodeCount = o.optInt("episode_count", 0)
        val totalEpisodes = o.optInt("total_episodes", 0)
        if (o.optBoolean("is_finished", false)) {
            val n = if (totalEpisodes > 0) totalEpisodes else episodeCount
            parts += if (n > 0) "全${n}集" else "已完结"
        } else if (episodeCount > 0) {
            parts += "更新至${episodeCount}集"
        }
        val score = o.optDouble("score", 0.0)
        if (score > 0) parts += "${score}分"
        return parts.joinToString(" · ")
    }

    private fun cleanTitle(raw: String): String {
        var t = raw.replace(Regex("<[^>]+>"), " ")
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
        t = t.replace("全集在线观看", "")
        t = t.replace(Regex("""[-–—|]\s*黄果短剧(吃瓜社区|社区)?\s*$"""), "")
        t = t.replace(Regex("""黄果吃瓜侠\s*[·•]\s*\d{4}-\d{2}-\d{2}\s*[·•]\s*\S*"""), "")
        t = t.replace("吃瓜社区", "")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun str(o: JSONObject, key: String): String {
        if (!o.has(key) || o.isNull(key)) return ""
        return o.optString(key)
    }

    fun absUrl(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> SITE + url
        else -> url
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
