package com.thyids.free_music.presentation

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.thyids.free_music.data.CacheManager
import com.thyids.free_music.data.MusicRepository
import com.thyids.free_music.data.PlayMode
import com.thyids.free_music.data.Playlist
import com.thyids.free_music.data.PlaylistRepository
import com.thyids.free_music.data.RemoteSpeakerManager
import com.thyids.free_music.data.ResolvedPlayback
import com.thyids.free_music.data.Song
import com.thyids.free_music.service.PlaybackService
import com.thyids.free_music.service.RemoteSpeakerService
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = MusicRepository()
    private val playlistRepo = PlaylistRepository(application)
    val cacheManager = CacheManager(application)
    
    private val speakerManager = RemoteSpeakerManager(
        context = application,
        onCommandReceived = { handleRemoteCommand(it) },
        onConnectionStatusChanged = { connected, ip ->
            isControllerMode = connected
            connectedSpeakerIp = ip
            if (connected) errorMessage = "已连接到音箱: $ip"
            else if (ip == null && !connected) errorMessage = "音箱连接已断开"
        }
    )

    private var mediaController: MediaController? = null
    private var playbackRequestToken = 0L
    private var consecutivePlaybackFailures = 0

    var songs by mutableStateOf<List<Song>>(emptyList())
    var isLoading by mutableStateOf(false)
    var searchPerformed by mutableStateOf(false)
    var currentSong by mutableStateOf<Song?>(null)
    var currentPlayUrl by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var errorMessage by mutableStateOf<String?>(null)

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var currentPlaylist by mutableStateOf<Playlist?>(null)
    var currentPlaylistIndex by mutableStateOf(-1)
    var isCaching by mutableStateOf(false)

    var showPlaylistDialog by mutableStateOf(false)
    var showCreatePlaylistDialog by mutableStateOf(false)
    var showPlaylistsPage by mutableStateOf(false)
    var pendingSongForPlaylist by mutableStateOf<Song?>(null)

    var importProgress by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var isImporting by mutableStateOf(false)
    var importedSongsBuffer = mutableListOf<Song>()
    var showImportNamingDialog by mutableStateOf(false)

    var isSpeakerMode by mutableStateOf(false)
    var isControllerMode by mutableStateOf(false)
    var connectedSpeakerIp by mutableStateOf<String?>(null)
    var discoveredSpeakers by mutableStateOf<List<String>>(emptyList())

    var captchaUrl by mutableStateOf<String?>(null)

    init {
        playlists = ensureFavoritePlaylist(playlistRepo.loadPlaylists())
        initializeController()
        syncVpnProxy()
    }

    fun startSpeakerDiscovery() {
        speakerManager.startDiscovery { ip ->
            if (!discoveredSpeakers.contains(ip)) {
                discoveredSpeakers = discoveredSpeakers + ip
            }
        }
    }

    private fun ensureFavoritePlaylist(playlists: List<Playlist>): List<Playlist> {
        if (playlists.any { it.isFavorite }) return playlists
        val fav = Playlist(
            id = UUID.randomUUID().toString(),
            name = "收藏",
            isFavorite = true
        )
        val result = playlists + fav
        playlistRepo.savePlaylists(result)
        return result
    }

    private fun saveWithFavoriteCheck(playlists: List<Playlist>): List<Playlist> {
        val result = ensureFavoritePlaylist(playlists)
        playlistRepo.savePlaylists(result)
        return result
    }

    private fun initializeController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@MusicViewModel.isPlaying = isPlaying
                    if (isPlaying) consecutivePlaybackFailures = 0
                }
                override fun onPlayerError(error: PlaybackException) {
                    this@MusicViewModel.isPlaying = false
                    handlePlaybackFailure("播放失败")
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = mediaController?.duration ?: 0L
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        playNextInPlaylist()
                    }
                }
            })
            startProgressUpdate()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                currentPosition = mediaController?.currentPosition ?: 0L
                delay(1000)
            }
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            searchPerformed = true
            songs = searchWithRetry(trimmedQuery)
            isLoading = false
        }
    }

    private suspend fun searchWithRetry(query: String): List<Song> {
        // 1. 原始搜索
        val firstResults = repository.searchSongs(query)
        if (firstResults.isNotEmpty()) return firstResults

        // 2. 移除括号及其内容后重试
        val noParentheses = query.replace(Regex("[\\(\\[（【].*?[\\)\\].】]"), " ").trim()
        if (noParentheses.isNotEmpty() && noParentheses != query) {
            val secondResults = repository.searchSongs(noParentheses)
            if (secondResults.isNotEmpty()) return secondResults
        }

        // 3. 尝试只取第一部分（针对 "歌名 作者" 或 "歌名 (版本) 其他"）
        val cleaned = if (noParentheses.isNotEmpty()) noParentheses else query
        val firstPart = cleaned.split(Regex("[\\s\\-\\–\\—_]+")).firstOrNull { it.isNotBlank() }
        if (firstPart != null && firstPart != cleaned && firstPart != query) {
            val thirdResults = repository.searchSongs(firstPart)
            if (thirdResults.isNotEmpty()) return thirdResults
        }

        return emptyList()
    }

    fun playSong(song: Song) {
        val currentSongs = songs
        val index = currentSongs.indexOf(song)
        
        // 创建一个临时的“搜索结果”歌单，以便支持切歌
        val searchPlaylist = Playlist(
            id = "search_results",
            name = "搜索结果",
            songs = currentSongs
        )
        
        playSongFromPlaylist(searchPlaylist, index)
    }

    fun playSongFromPlaylist(playlist: Playlist, index: Int, isAutoSkip: Boolean = false) {
        val song = playlist.songs.getOrNull(index) ?: return
        currentPlaylist = playlist
        currentPlaylistIndex = index
        if (!isAutoSkip) consecutivePlaybackFailures = 0
        val requestToken = ++playbackRequestToken

        viewModelScope.launch {
            val cachedFile = cacheManager.getCachedFile(song.id)
            val playback = if (cachedFile != null) {
                ResolvedPlayback(Uri.fromFile(cachedFile).toString(), "Cache", false)
            } else {
                repository.resolvePlayUrl(song)
            }
            if (requestToken != playbackRequestToken) return@launch
            val url = playback?.url
            if (playback?.usedFallback == true) {
                errorMessage = "主源不可用，已切换至 ${playback.sourceName} 备用源"
            }
            if (url != null) {
                if (isControllerMode) {
                    val cmd = JSONObject().apply {
                        put("type", "PLAY")
                        put("url", url)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("id", song.id)
                    }
                    speakerManager.sendCommand(cmd)
                    currentSong = song
                    currentPlayUrl = url
                    // Controller locally shows as playing for UI feedback
                    isPlaying = true
                } else {
                    currentSong = song
                    currentPlayUrl = url
                    val mediaItem = MediaItem.fromUri(url)
                    mediaController?.setMediaItem(mediaItem)
                    mediaController?.prepare()
                    mediaController?.play()
                }
            } else {
                handlePlaybackFailure("无法获取播放链接: ${song.title}")
            }
        }
    }

    private fun handlePlaybackFailure(reason: String) {
        val playlist = currentPlaylist
        if (playlist == null || playlist.songs.size <= 1) {
            errorMessage = reason
            consecutivePlaybackFailures = 0
            return
        }

        // Try every item once, then stop instead of looping forever when the
        // whole playlist is unavailable.
        if (consecutivePlaybackFailures >= playlist.songs.size - 1) {
            errorMessage = "歌单中的歌曲暂时都无法播放"
            consecutivePlaybackFailures = 0
            return
        }

        consecutivePlaybackFailures += 1
        val nextIndex = (currentPlaylistIndex + 1).mod(playlist.songs.size)
        errorMessage = "$reason，自动播放下一个"
        playSongFromPlaylist(playlist, nextIndex, isAutoSkip = true)
    }

    private fun playLocalFile(song: Song, file: File) {
        currentSong = song
        currentPlayUrl = file.toURI().toString()
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    fun playNext() {
        if (isControllerMode) {
            val playlist = currentPlaylist ?: return
            val nextIndex = currentPlaylistIndex + 1
            if (nextIndex < playlist.songs.size) {
                playSongFromPlaylist(playlist, nextIndex)
            } else {
                playSongFromPlaylist(playlist, 0)
            }
        } else {
            playNextInPlaylist()
        }
    }

    fun playPrevious() {
        val playlist = currentPlaylist ?: return
        val prevIndex = currentPlaylistIndex - 1
        if (prevIndex >= 0) {
            playSongFromPlaylist(playlist, prevIndex)
        } else {
            playSongFromPlaylist(playlist, playlist.songs.size - 1)
        }
    }

    fun playNextInPlaylist() {
        val playlist = currentPlaylist ?: return
        val nextIndex = currentPlaylistIndex + 1
        if (nextIndex < playlist.songs.size) {
            playSongFromPlaylist(playlist, nextIndex)
        } else {
            playSongFromPlaylist(playlist, 0)
        }
    }

    fun togglePlayPause() {
        if (isControllerMode) {
            val type = if (isPlaying) "PAUSE" else "RESUME"
            speakerManager.sendCommand(JSONObject().apply { put("type", type) })
            isPlaying = !isPlaying
        } else {
            if (mediaController?.isPlaying == true) {
                mediaController?.pause()
            } else {
                mediaController?.play()
            }
        }
    }

    fun seekTo(position: Long) {
        if (isControllerMode) {
            speakerManager.sendCommand(JSONObject().apply {
                put("type", "SEEK")
                put("position", position)
            })
            currentPosition = position
        } else {
            mediaController?.seekTo(position)
        }
    }

    // --- Remote Speaker Methods ---

    fun startSpeakerMode() {
        isSpeakerMode = true
        speakerManager.startServer()
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, RemoteSpeakerService::class.java)
                .setAction(RemoteSpeakerService.ACTION_START)
        )
    }

    fun stopSpeakerMode() {
        isSpeakerMode = false
        speakerManager.stopServer()
        mediaController?.stop()
        appContext.stopService(Intent(appContext, RemoteSpeakerService::class.java))
    }

    fun connectToSpeaker(ip: String) {
        speakerManager.connectToServer(ip)
    }

    fun disconnectFromSpeaker() {
        speakerManager.disconnect()
    }

    private fun handleRemoteCommand(json: JSONObject) {
        if (!isSpeakerMode) return
        
        val type = json.optString("type")
        when (type) {
            "PLAY" -> {
                val url = json.getString("url")
                val title = json.getString("title")
                val artist = json.getString("artist")
                val id = json.getString("id")
                
                currentSong = Song(id, title, artist)
                currentPlayUrl = url
                
                val mediaItem = MediaItem.fromUri(url)
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()
            }
            "PAUSE" -> {
                mediaController?.pause()
            }
            "RESUME" -> {
                mediaController?.play()
            }
            "SEEK" -> {
                val pos = json.getLong("position")
                mediaController?.seekTo(pos)
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun createPlaylist(name: String) {
        val newPlaylist = playlistRepo.createPlaylist(name)
        playlists = saveWithFavoriteCheck(playlists + newPlaylist)
    }

    fun deletePlaylist(playlistId: String) {
        if (playlistId == getFavoritePlaylistId()) return
        playlists = saveWithFavoriteCheck(playlists.filter { it.id != playlistId })
        if (currentPlaylist?.id == playlistId) {
            currentPlaylist = null
            currentPlaylistIndex = -1
        }
    }

    fun mergePlaylists(targetId: String, sourceId: String) {
        if (targetId == sourceId) return
        val sourcePlaylist = playlists.find { it.id == sourceId } ?: return
        
        playlists = saveWithFavoriteCheck(playlists.map { playlist ->
            if (playlist.id == targetId) {
                var updatedPlaylist = playlist
                sourcePlaylist.songs.forEach { song ->
                    updatedPlaylist = playlistRepo.addSongToPlaylist(updatedPlaylist, song)
                }
                updatedPlaylist
            } else playlist
        })
        errorMessage = "歌单已合并"
    }

    private fun getFavoritePlaylistId(): String? {
        return playlists.find { it.isFavorite }?.id
    }

    fun showAddToPlaylistDialog(song: Song) {
        pendingSongForPlaylist = song
        showPlaylistDialog = true
    }

    fun addSongToPlaylist(playlistId: String) {
        val song = pendingSongForPlaylist ?: return
        playlists = saveWithFavoriteCheck(playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlistRepo.addSongToPlaylist(playlist, song)
            } else playlist
        })
        pendingSongForPlaylist = null
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        playlists = saveWithFavoriteCheck(playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlistRepo.removeSongFromPlaylist(playlist, songId)
            } else playlist
        })
    }

    fun toggleFavorite(song: Song) {
        playlists = saveWithFavoriteCheck(playlistRepo.toggleFavorite(playlists, song))
    }

    fun isFavorite(songId: String): Boolean {
        return playlistRepo.isFavorite(playlists, songId)
    }

    fun setPlayMode(playlistId: String, mode: PlayMode) {
        playlists = saveWithFavoriteCheck(playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlistRepo.setPlayMode(playlist, mode)
            } else playlist
        })
    }

    fun importPlaylist(json: String) {
        try {
            val obj = JSONObject(json)
            val imported = playlistRepo.parsePlaylist(obj)
            val newPlaylist = imported.copy(
                id = UUID.randomUUID().toString(),
                isFavorite = false
            )
            playlists = saveWithFavoriteCheck(playlists + newPlaylist)
            errorMessage = "歌单导入成功"
        } catch (e: Exception) {
            errorMessage = "歌单导入失败: 格式错误"
        }
    }

    fun exportPlaylist(playlist: Playlist): String {
        return playlistRepo.toJson(playlist).toString()
    }

    fun importKugouPlaylist(shareText: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val importedSongs = repository.parseKugouPlaylist(shareText)
                if (importedSongs.isNotEmpty()) {
                    val newPlaylist = Playlist(
                        id = UUID.randomUUID().toString(),
                        name = "酷狗导入_${System.currentTimeMillis() / 1000}",
                        songs = importedSongs
                    )
                    playlists = saveWithFavoriteCheck(playlists + newPlaylist)
                    errorMessage = "成功导入 ${importedSongs.size} 首歌曲"
                } else {
                    errorMessage = "未找到歌单内容，请检查链接"
                }
            } catch (e: Exception) {
                errorMessage = "解析失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun startTextImport(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        
        viewModelScope.launch {
            isImporting = true
            importedSongsBuffer.clear()
            val progressList = lines.map { it to "等待中..." }.toMutableList()
            importProgress = progressList.toList()
            
            lines.forEachIndexed { index, query ->
                progressList[index] = query to "搜索中..."
                importProgress = progressList.toList()
                
                try {
                    val results = searchWithRetry(query)
                    if (results.isNotEmpty()) {
                        val foundSong = results[0]
                        importedSongsBuffer.add(foundSong)
                        progressList[index] = query to "成功: ${foundSong.title}"
                    } else {
                        progressList[index] = query to "未找到"
                    }
                } catch (e: Exception) {
                    progressList[index] = query to "失败"
                }
                importProgress = progressList.toList()
            }
            isImporting = false
            if (importedSongsBuffer.isNotEmpty()) {
                showImportNamingDialog = true
            } else {
                errorMessage = "导入失败：未找到任何歌曲"
            }
        }
    }

    fun finalizeImport(playlistName: String) {
        if (importedSongsBuffer.isEmpty()) return
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = playlistName.ifBlank { "未命名歌单" },
            songs = importedSongsBuffer.toList()
        )
        playlists = saveWithFavoriteCheck(playlists + newPlaylist)
        importedSongsBuffer.clear()
        importProgress = emptyList()
        showImportNamingDialog = false
        errorMessage = "歌单创建成功"
    }

    fun cacheCurrentSong() {
        val song = currentSong ?: return
        val url = currentPlayUrl ?: return
        if (cacheManager.isCached(song.id)) {
            errorMessage = "已缓存: ${song.title}"
            return
        }
        viewModelScope.launch {
            isCaching = true
            val success = cacheManager.cacheSong(song.id, url)
            isCaching = false
            if (success) {
                errorMessage = "缓存完成: ${song.title}"
            } else {
                errorMessage = "缓存失败: ${song.title}"
            }
        }
    }

    fun isSongCached(songId: String): Boolean {
        return cacheManager.isCached(songId)
    }

    fun getCachedUri(songId: String): Uri? {
        return cacheManager.getCachedFile(songId)?.let { Uri.fromFile(it) }
    }

    fun syncVpnProxy() {
        viewModelScope.launch {
            val vpnUrl = "https://thyidsvpn.dpdns.org/Thy201503200058?sb"
            val success = repository.syncProxyFromSingBox(vpnUrl)
            if (success) {
                errorMessage = "VPN伪装已启用，正在隐藏IP"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appContext.stopService(Intent(appContext, RemoteSpeakerService::class.java))
        speakerManager.shutdown()
        mediaController?.release()
    }
}
