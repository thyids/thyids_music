package com.thyids.free_music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun loadPlaylists(): List<Playlist> {
        val json = prefs.getString("playlists_data", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { parsePlaylist(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("playlists_data", arr.toString()).apply()
    }

    fun createPlaylist(name: String): Playlist {
        return Playlist(
            id = UUID.randomUUID().toString(),
            name = name
        )
    }

    fun deletePlaylist(playlists: List<Playlist>, playlistId: String): List<Playlist> {
        return playlists.filter { it.id != playlistId }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song): Playlist {
        val exists = playlist.songs.any { it.id == song.id }
        if (exists) return playlist
        return playlist.copy(songs = playlist.songs + song)
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: String): Playlist {
        return playlist.copy(songs = playlist.songs.filter { it.id != songId })
    }

    fun toggleFavorite(playlists: List<Playlist>, song: Song): List<Playlist> {
        val favPlaylist = playlists.find { it.isFavorite }
        val updated = if (favPlaylist != null) {
            val exists = favPlaylist.songs.any { it.id == song.id }
            if (exists) {
                playlists.map { if (it.id == favPlaylist.id) removeSongFromPlaylist(it, song.id) else it }
            } else {
                playlists.map { if (it.id == favPlaylist.id) addSongToPlaylist(it, song) else it }
            }
        } else {
            val newFav = Playlist(
                id = UUID.randomUUID().toString(),
                name = "收藏",
                isFavorite = true,
                songs = listOf(song)
            )
            playlists + newFav
        }
        return updated
    }

    fun isFavorite(playlists: List<Playlist>, songId: String): Boolean {
        val fav = playlists.find { it.isFavorite } ?: return false
        return fav.songs.any { it.id == songId }
    }

    fun setPlayMode(playlist: Playlist, mode: PlayMode): Playlist {
        return playlist.copy(playMode = mode)
    }

    fun parsePlaylist(obj: JSONObject): Playlist {
        val songsArr = obj.optJSONArray("songs") ?: JSONArray()
        val songs = (0 until songsArr.length()).map { i ->
            val s = songsArr.getJSONObject(i)
            Song(s.getString("id"), s.getString("title"), s.getString("artist"))
        }
        return Playlist(
            id = obj.getString("id"),
            name = obj.getString("name"),
            songs = songs,
            isFavorite = obj.optBoolean("isFavorite", false),
            playMode = try { PlayMode.valueOf(obj.optString("playMode", "SEQUENTIAL")) } catch (e: Exception) { PlayMode.SEQUENTIAL }
        )
    }

    fun toJson(playlist: Playlist): JSONObject {
        val songsArr = JSONArray()
        playlist.songs.forEach { s ->
            songsArr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("artist", s.artist)
            })
        }
        return JSONObject().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("songs", songsArr)
            put("isFavorite", playlist.isFavorite)
            put("playMode", playlist.playMode.name)
        }
    }
}
