package com.wlgh.hgtv.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 封面不是普通图片：源站下发 AES-CBC 密文，需要按二进制解密后再判断图片魔数。
 * 少数封面本身是明文，直接解码。
 */
object CoverLoader {
    private const val KEY = "f5d965df75336270"
    private const val IV = "97b60394abc2fbe1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(96) {}

    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Referer", Api.SITE + "/")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val raw = response.body?.bytes() ?: return@withContext null
                    val bytes = if (isImage(raw)) raw else decrypt(raw) ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext null
                    cache.put(url, bitmap)
                    bitmap
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isImage(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val jpeg = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
        val png = b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte()
        val webp = b.size >= 12 && b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[8] == 0x57.toByte()
        val gif = b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte()
        return jpeg || png || webp || gif
    }

    private fun decrypt(b: ByteArray): ByteArray? {
        if (b.isEmpty() || b.size % 16 != 0) return null
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(KEY.toByteArray(Charsets.US_ASCII), "AES"),
                IvParameterSpec(IV.toByteArray(Charsets.US_ASCII))
            )
            cipher.doFinal(b).takeIf { isImage(it) }
        } catch (_: Exception) {
            null
        }
    }
}
