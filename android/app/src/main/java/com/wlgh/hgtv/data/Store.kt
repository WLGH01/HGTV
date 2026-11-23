package com.wlgh.hgtv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地收藏 / 观看历史。纯 SharedPreferences，不依赖账号。
 */
object Store {
    private const val PREFS = "hgtv_store"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HISTORY = "history"
    private const val KEY_UI_SCALE = "ui_scale_v2"

    data class Favorite(
        val id: String,
        val title: String,
        val cover: String
    )

    data class History(
        val id: String,
        val title: String,
        val cover: String,
        val ep: Int,
        val time: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favorites(context: Context): List<Favorite> {
        val raw = prefs(context).getString(KEY_FAVORITES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Favorite(
                    id = id,
                    title = o.optString("title"),
                    cover = o.optString("cover")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isFavorite(context: Context, id: String): Boolean =
        favorites(context).any { it.id == id }

    fun toggleFavorite(context: Context, video: Video): Boolean {
        val list = favorites(context).toMutableList()
        val existing = list.indexOfFirst { it.id == video.id }
        val added = existing < 0
        if (added) {
            list.add(0, Favorite(video.id, video.title, video.cover))
        } else {
            list.removeAt(existing)
        }
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("cover", item.cover)
            )
        }
        prefs(context).edit().putString(KEY_FAVORITES, array.toString()).apply()
        return added
    }

    fun history(context: Context): List<History> {
        val raw = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                History(
                    id = id,
                    title = o.optString("title"),
                    cover = o.optString("cover"),
                    ep = o.optInt("ep", 1),
                    time = o.optLong("time", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addHistory(context: Context, video: Video, ep: Int) {
        val list = history(context).filter { it.id != video.id }.toMutableList()
        list.add(
            0,
            History(
                id = video.id,
                title = video.title,
                cover = video.cover,
                ep = ep,
                time = System.currentTimeMillis()
            )
        )
        while (list.size > 30) list.removeAt(list.lastIndex)
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("cover", item.cover)
                    .put("ep", item.ep)
                    .put("time", item.time)
            )
        }
        prefs(context).edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    fun uiScale(context: Context): Float = prefs(context).getFloat(KEY_UI_SCALE, 1f)

    fun setUiScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_UI_SCALE, scale.coerceIn(0.6f, 1.2f)).apply()
    }
}
