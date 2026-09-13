package com.thyids.free_music.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val url: String? = null
)
