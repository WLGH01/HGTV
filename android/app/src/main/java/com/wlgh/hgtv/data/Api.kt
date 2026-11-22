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

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SITE + path)
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", SITE + "/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
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
        return Video(
            id = str(o, "video_id").ifBlank { str(o, "id") },
            title = cleanTitle(str(o, "title")),
            cover = absUrl(str(o, "cover")),
            remarks = finalRemarks,
            score = o.optDouble("score", 0.0),
            desc = str(o, "description"),
            tags = tags,
            episodeCount = maxOf(episodeCount, totalEpisodes),
            finished = o.optBoolean("is_finished", false)
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
