package com.wlgh.hgtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 播放地址。源站把业务接口封装进 dx150-edge-v1 加密通道，
 * 网页端最终通过带 HMAC 签名的 Worker 取地址；原生端复用同一个 Worker。
 */
object Playback {
    private const val WORKER = "https://huangguo-parser.13681235735.workers.dev"
    private const val SECRET = "hg-dx150-2026-11-w7Qm4pLz"
    private const val REFERER = "https://drdrdrrddrdr.github.io/huangguo-web/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(id: String, ep: Int): PlayInfo = withContext(Dispatchers.IO) {
        val page = "${Api.SITE}/video/$id/"
        val path = "/play?url=${URLEncoder.encode(page, "UTF-8")}&ep=$ep"
        val window = System.currentTimeMillis() / 1000L / 300L
        val ts = window * 300L
        val signature = hmac("$window\n/api$path")
        val request = Request.Builder()
            .url("$WORKER/api$path")
            .header("User-Agent", Api.UA)
            .header("Accept", "*/*")
            .header("Origin", "https://drdrdrrddrdr.github.io")
            .header("Referer", "${REFERER}play.html?id=$id&ep=$ep")
            .header("Sec-Fetch-Site", "cross-site")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("X-HG-Ts", ts.toString())
            .header("X-HG-Sig", signature)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("播放接口 HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val url = json.optString("url")
            if (url.isBlank()) throw IOException("未取到播放地址")
            PlayInfo(
                url = url,
                proxy = json.optString("proxy"),
                source = json.optString("source"),
                declaredDuration = json.optInt("declaredDuration", 0)
            )
        }
    }

    private fun hmac(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
