package com.thyids.free_music.data

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val isFavorite: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENTIAL
)

enum class PlayMode {
    SEQUENTIAL
}
