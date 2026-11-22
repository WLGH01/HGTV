package com.wlgh.hgtv.data

data class Episode(val num: Int, val duration: Int = 0)

data class Video(
    val id: String,
    val title: String,
    val cover: String,
    val remarks: String,
    val score: Double = 0.0,
    val desc: String = "",
    val tags: List<String> = emptyList(),
    val episodeCount: Int = 0,
    val finished: Boolean = false,
    val episodes: List<Episode> = emptyList()
)

data class VideoPage(val items: List<Video>, val page: Int, val pages: Int)

data class Topic(val slug: String, val title: String, val cover: String, val count: Int)

data class Post(
    val id: String,
    val title: String,
    val cover: String,
    val created: String,
    val author: String,
    val content: String
)

data class PostPage(val items: List<Post>, val page: Int, val pages: Int)

data class PlayInfo(
    val url: String,
    val proxy: String,
    val source: String,
    val declaredDuration: Int
)
