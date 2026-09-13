package com.thyids.free_music.data

import android.content.ContentValues.TAG
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.InetSocketAddress
import java.io.IOException
import java.net.URI

class MusicRepository {
    private val TAG = "MusicRepository"

    @Volatile
    private var currentProxy: Proxy = Proxy.NO_PROXY
    private val proxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(currentProxy)
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            Log.e(TAG, "Proxy connection failed: $sa", ioe)
            // Optional: fallback to no proxy on failure
            // currentProxy = Proxy.NO_PROXY
        }
    }

    private val cookieStore = mutableMapOf<String, List<okhttp3.Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .proxySelector(proxySelector)
        .cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private val miguSource = MiguSource(client)
    private val neteaseSource = NeteaseSource(client)
    private val kuwoSource = KuwoSource(client)
    private val qqSource = QqSource(client)
    private val bilibiliSource = BilibiliSource(client)
    private val taiheSource = TaiheSource(client)

    fun clearCookies() {
        cookieStore.clear()
    }

    private fun generateRandomIp(): String {
        return "${(1..254).random()}.${(1..254).random()}.${(1..254).random()}.${(1..254).random()}"
    }

    private fun addIpHeaders(builder: Request.Builder): Request.Builder {
        val randomIp = generateRandomIp()
        return builder
            .header("X-Forwarded-For", randomIp)
            .header("X-Real-IP", randomIp)
            .header("Client-IP", randomIp)
            .header("Via", randomIp)
    }

    /**
     * Fetches the sing-box configuration from the provided URL and attempts to extract
     * a working proxy server. Note: This assumes the server supports standard HTTP/SOCKS 
     * tunneling or that the extracted server:port can be used as a proxy.
     */
    suspend fun syncProxyFromSingBox(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching proxy config from: $url")
            val request = Request.Builder().url(url).build()
            // Use a temporary client without a proxy to fetch the config
            val tempClient = OkHttpClient.Builder().build()
            val response = tempClient.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return@withContext false
            
            val json = JSONObject(jsonStr)
            val outbounds = json.optJSONArray("outbounds") ?: return@withContext false
            
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.getJSONObject(i)
                val server = outbound.optString("server")
                val port = outbound.optInt("port", -1)
                val type = outbound.optString("type") // e.g., vless, socks, http
                
                if (server.isNotEmpty() && port != -1) {
                    Log.d(TAG, "Extracted proxy: $server:$port ($type)")
                    
                    // We attempt to use it as an HTTP proxy. 
                    // Note: VLESS/Trojan nodes usually require a local client, 
                    // but if the server supports standard HTTP CONNECT, this will work.
                    currentProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(server, port))
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync proxy from sing-box", e)
        }
        false
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        try {
            val homeRequest = Request.Builder().url("https://www.gequbao.com/")
                .let { addIpHeaders(it) }
                .build()
            client.newCall(homeRequest).execute().use { it.close() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to hit homepage for cookies", e)
        }

        val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
        val url = "https://www.gequbao.com/s/$encodedQuery"
        Log.d(TAG, "Searching songs with url: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .let { addIpHeaders(it) }
            .build()
        val songs = mutableListOf<Song>()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search request failed: ${response.code} ${response.message}")
                } else {
                    val html = response.body?.string().orEmpty()
                    val doc = Jsoup.parse(html)

                    val rows = doc.select(".row.no-gutters, .row.music-list")
                    Log.d(TAG, "Found ${rows.size} potential rows")

                    for (row in rows) {
                        val titleElement = row.selectFirst("a[href^=/music/].hover-zoom")
                            ?: row.selectFirst("a[href^=/music/]")

                        if (titleElement != null) {
                            val title = titleElement.select(".text-primary").text().trim().ifEmpty {
                                titleElement.attr("title").substringBefore("-").trim()
                            }
                            val artist = titleElement.select(".text-jade").text().trim().ifEmpty {
                                titleElement.attr("title").substringAfter("-").trim()
                            }

                            if (title.isEmpty() || title == "歌名") continue

                            val href = titleElement.attr("href")
                            val id = href.removeSuffix("/").substringAfterLast("/")

                            if (id.isNotEmpty()) {
                                songs.add(Song(id, title, artist))
                                Log.d(TAG, "Parsed song: $title by $artist (id: $id)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs", e)
        }
        if (songs.isNotEmpty()) {
            Log.d(TAG, "Primary source returned ${songs.size} songs")
            return@withContext songs
        }

        val kugouSongs = runCatching { searchKugou(trimmedQuery) }.getOrDefault(emptyList())
        if (kugouSongs.isNotEmpty()) {
            Log.d(TAG, "Kugou fallback returned ${kugouSongs.size} songs")
            return@withContext kugouSongs
        }

        val backupSources = listOf(
            kuwoSource,
            miguSource,
            bilibiliSource,
            taiheSource,
            qqSource,
            neteaseSource
        )
        for (source in backupSources) {
            val results = runCatching { source.search(trimmedQuery) }.getOrDefault(emptyList())
            if (results.isNotEmpty()) {
                Log.d(TAG, "${source.name} fallback returned ${results.size} songs")
                return@withContext results
            }
        }
        emptyList()
    }

    suspend fun searchKugou(query: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=$encodedQuery&page=1&pagesize=20&showtype=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                .build()
            
            val jsonStr = client.newCall(request).execute().use { it.body?.string() ?: "" }
            val json = JSONObject(jsonStr)
            val data = json.optJSONObject("data") ?: return@withContext emptyList()
            val infoArr = data.optJSONArray("info") ?: return@withContext emptyList()

            for (i in 0 until infoArr.length()) {
                val item = infoArr.getJSONObject(i)
                val hash = item.optString("hash")
                val songName = item.optString("songname")
                val singerName = item.optString("singername")
                if (hash.isNotEmpty()) {
                    songs.add(Song(id = "kugou:$hash", title = songName, artist = singerName))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kugou search failed", e)
        }
        songs
    }

    suspend fun getPlayUrl(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.startsWith("kugou:")) {
            return@withContext getKugouPlayUrl(songId.removePrefix("kugou:"))
        }
        if (songId.startsWith("migu:")) {
            return@withContext miguSource.getPlayUrl(Song(songId, "", ""))
        }
        if (songId.startsWith("netease:")) {
            return@withContext neteaseSource.getPlayUrl(Song(songId, "", ""))
        }
        if (songId.startsWith("kuwo:")) {
            return@withContext kuwoSource.getPlayUrl(Song(songId, "", ""))
        }
        if (songId.startsWith("qq:")) {
            return@withContext qqSource.getPlayUrl(Song(songId, "", ""))
        }
        if (songId.startsWith("bilibili:")) {
            return@withContext bilibiliSource.getPlayUrl(Song(songId, "", ""))
        }
        if (songId.startsWith("taihe:")) {
            return@withContext taiheSource.getPlayUrl(Song(songId, "", ""))
        }

        // Always clear cookies before a new play request to avoid "quota exhausted" from old session
        clearCookies()

        val musicPageUrl = "https://www.gequbao.com/music/$songId"
        try {
            val request = Request.Builder()
                .url(musicPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .let { addIpHeaders(it) }
                .build()

            val html = client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
            Log.d(TAG, "Music page HTML length: ${html.length}")

            // 0. Try direct HTML extraction (newest fallback)
            val htmlUrlRegex = Regex("""url:\s*['"](https?://[^'"]+)['"]""")
            htmlUrlRegex.find(html)?.groupValues?.get(1)?.let {
                if (it.contains(".mp3") || it.contains(".m4a")) {
                    Log.d(TAG, "Extracted URL directly from HTML: $it")
                    return@withContext it
                }
            }

            var playId: String? = null
            val appDataRegex = Regex("""window\.appData\s*=\s*JSON\.parse\('([^']+)'\)""")
            val match = appDataRegex.find(html)

            if (match != null) {
                var jsonString = match.groupValues[1]
                jsonString = jsonString.replace("\\u0022", "\"").replace("\\/", "/")
                try {
                    val json = JSONObject(jsonString)
                    playId = json.optString("play_id")
                } catch (e: Exception) {}
            }

            if (playId.isNullOrEmpty()) {
                val playIdVarRegex = Regex("""var\s+play_id\s*=\s*['"]([^'"]+)['"]""")
                playId = playIdVarRegex.find(html)?.groupValues?.get(1)
            }

            val idsToTry = mutableListOf<String>()
            playId?.let { idsToTry.add(it) }
            if (songId != playId) idsToTry.add(songId)

            for (id in idsToTry) {
                Log.d(TAG, "Attempting API call with ID: $id")
                
                // 1. common-play-url (POST)
                try {
                    val body = FormBody.Builder().add("id", id).build()
                    val apiRequest = Request.Builder()
                        .url("https://www.gequbao.com/member/common-play-url")
                        .post(body)
                        .header("Referer", musicPageUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .let { addIpHeaders(it) }
                        .build()

                    val jsonStr = client.newCall(apiRequest).execute().use { it.body?.string() ?: "" }
                    Log.d(TAG, "common-play-url: $jsonStr")

                    if (jsonStr.contains("\"url\"")) {
                        val url = JSONObject(jsonStr).optJSONObject("data")?.optString("url")
                        if (!url.isNullOrEmpty()) return@withContext url
                    }
                } catch (e: Exception) {}

                // 2. api/play-url (GET) - Fixed method
                try {
                    val apiUrl = "https://www.gequbao.com/api/play-url?id=$id"
                    val apiRequest = Request.Builder()
                        .url(apiUrl)
                        .get()
                        .header("Referer", musicPageUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .let { addIpHeaders(it) }
                        .build()

                    val jsonStr = client.newCall(apiRequest).execute().use { it.body?.string() ?: "" }
                    Log.d(TAG, "api/play-url (GET): $jsonStr")

                    if (jsonStr.contains("\"url\"")) {
                        val url = JSONObject(jsonStr).optJSONObject("data")?.optString("url")
                        if (!url.isNullOrEmpty()) return@withContext url
                    }
                } catch (e: Exception) {}

                // 3. playdata/index (GET)
                try {
                    val apiUrl = "https://www.gequbao.com/playdata/index?id=$id"
                    val apiRequest = Request.Builder().url(apiUrl).get()
                        .header("Referer", musicPageUrl)
                        .let { addIpHeaders(it) }
                        .build()
                    val jsonStr = client.newCall(apiRequest).execute().use { it.body?.string() ?: "" }
                    Log.d(TAG, "playdata/index: $jsonStr")
                    if (jsonStr.contains("\"url\"")) {
                        val url = JSONObject(jsonStr).optJSONObject("data")?.optString("url")
                        if (!url.isNullOrEmpty()) return@withContext url
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPlayUrl", e)
        }
        null
    }

    /**
     * Resolves a song through the primary site first and then through the
     * backup providers. Matching by title and artist avoids playing an
     * unrelated replacement from a fallback provider.
     */
    suspend fun resolvePlayUrl(song: Song): ResolvedPlayback? = withContext(Dispatchers.IO) {
        val directUrl = getPlayUrl(song.id)
        if (!directUrl.isNullOrBlank()) {
            return@withContext ResolvedPlayback(
                url = directUrl,
                sourceName = sourceLabel(song.id),
                usedFallback = false
            )
        }

        val query = listOf(song.title, song.artist)
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) }
            .joinToString(" ")
        if (query.isBlank()) return@withContext null

        val directSource = sourceLabel(song.id)
        val searches: List<Pair<String, suspend () -> List<Song>>> = listOf(
            "Kugou" to { searchKugou(query) },
            kuwoSource.name to { kuwoSource.search(query) },
            miguSource.name to { miguSource.search(query) },
            bilibiliSource.name to { bilibiliSource.search(query) },
            taiheSource.name to { taiheSource.search(query) },
            qqSource.name to { qqSource.search(query) },
            neteaseSource.name to { neteaseSource.search(query) }
        )
        val resolved = coroutineScope {
            val jobs = searches
                .filterNot { it.first.equals(directSource, ignoreCase = true) }
                .map { (name, search) ->
                    async {
                        val results = runCatching { search() }.getOrDefault(emptyList())
                        resolveFromFallback(song, name, results)
                    }
                }
            for (job in jobs) {
                job.await()?.let { return@coroutineScope it }
            }
            null
        }
        resolved
    }

    private suspend fun resolveFromFallback(
        requested: Song,
        sourceName: String,
        results: List<Song>
    ): ResolvedPlayback? {
        val match = bestMatch(requested, results) ?: return null
        val url = runCatching { getPlayUrl(match.id) }.getOrNull() ?: return null
        Log.d(TAG, "Resolved ${requested.title} through $sourceName: ${match.id}")
        return ResolvedPlayback(url = url, sourceName = sourceName, usedFallback = true)
    }

    private fun sourceLabel(songId: String): String = when {
        songId.startsWith("kugou:") -> "Kugou"
        songId.startsWith("migu:") -> "Migu"
        songId.startsWith("netease:") -> "Netease"
        songId.startsWith("kuwo:") -> "Kuwo"
        songId.startsWith("qq:") -> "QQ"
        songId.startsWith("bilibili:") -> "Bilibili"
        songId.startsWith("taihe:") -> "Qianqian"
        else -> "Gequbao"
    }

    private fun bestMatch(requested: Song, candidates: List<Song>): Song? {
        if (candidates.isEmpty()) return null
        val wantedTitle = normalizeForMatch(requested.title)
        val wantedArtist = normalizeForMatch(requested.artist)

        val ranked = candidates.map { candidate ->
            val candidateTitle = normalizeForMatch(candidate.title)
            val candidateArtist = normalizeForMatch(candidate.artist)
            var score = 0
            if (candidateTitle == wantedTitle) score += 100
            else if (candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle)) score += 60
            if (candidateArtist == wantedArtist) score += 40
            else if (candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist)) score += 20
            candidate to score
        }.maxByOrNull { it.second }
        return ranked?.takeIf { it.second > 0 }?.first
    }

    private fun normalizeForMatch(value: String): String = value
        .replace(Regex("[\\(\\[（【].*?[\\)\\]）】]"), " ")
        .replace(Regex("(?i)\\b(feat|ft|live|remix|version)\\b.*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
        .lowercase()

    private suspend fun getKugouPlayUrl(hash: String): String? = withContext(Dispatchers.IO) {
        val kugouClient = client
        val uaMobile = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
        val url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
        try {
            val req1 = Request.Builder().url(url).header("User-Agent", uaMobile).build()
            val jsonStr1 = kugouClient.newCall(req1).execute().use { it.body?.string() ?: "" }
            val json1 = JSONObject(jsonStr1)
            var playUrl = json1.optString("url").takeIf { it.isNotEmpty() }

            if (playUrl.isNullOrBlank()) {
                val backupUrl = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=$hash"
                val req2 = Request.Builder().url(backupUrl).header("User-Agent", uaMobile).build()
                val jsonStr2 = kugouClient.newCall(req2).execute().use { it.body?.string() ?: "" }
                val json2 = JSONObject(jsonStr2)
                playUrl = json2.optJSONObject("data")?.optString("play_url")
            }
            return@withContext playUrl?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Kugou play url", e)
            null
        }
    }

    suspend fun parseKugouPlaylist(shareText: String): List<Song> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Parsing Kugou playlist from text: $shareText")
        val urlRegex = Regex("""https?://t\d*\.kugou\.com/\w+""")
        val match = urlRegex.find(shareText) ?: run {
            Log.d(TAG, "No Kugou URL found in text")
            return@withContext emptyList()
        }
        val shortUrl = match.value
        Log.d(TAG, "Found short URL: $shortUrl")

        try {
            // 1. HEAD请求拿重定向，复用全局client仅临时关重定向
            val headRequest = Request.Builder()
                .url(shortUrl)
                .method("HEAD", null)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                .build()
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val headResponse = noRedirectClient.newCall(headRequest).execute()
            val longUrl = headResponse.header("Location") ?: ""
            headResponse.close()
            Log.d(TAG, "Redirected to long URL: $longUrl")

            if (longUrl.isBlank()) {
                Log.e(TAG, "kugou redirect empty")
                // Try a GET request as fallback
                val getRequest = Request.Builder().url(shortUrl).build()
                val getResponse = client.newCall(getRequest).execute()
                val finalUrl = getResponse.request.url.toString()
                Log.d(TAG, "Fallback GET redirect to: $finalUrl")
                // We'll use finalUrl if longUrl was empty
                if (finalUrl.contains("specialid") || finalUrl.contains("global_specialid")) {
                    // continue with finalUrl
                } else {
                    return@withContext emptyList()
                }
            }

            // 2. 优先匹配global_specialid
            val globalIdRegex = Regex("""global_special[_]?id=([0-9a-zA-Z_\-]+)""")
            val normalIdRegex = Regex("""specialid=([0-9a-zA-Z_\-]+)""")
            var specialId: String? = null
            globalIdRegex.find(longUrl)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { specialId = it }
            if (specialId.isNullOrBlank()) {
                normalIdRegex.find(longUrl)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { specialId = it }
            }
            if (specialId.isNullOrBlank() || specialId == "-2147483648") {
                Log.d(TAG, "No valid ID found in long URL: $longUrl")
                return@withContext emptyList()
            }
            Log.d(TAG, "Extracted ID: $specialId")

            // 3. 酷狗单独用干净client，隔绝曲库宝Cookie污染
            val kugouClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                
            val songs = mutableListOf<Song>()
            
            // 尝试多个可能的 API 接口
            val urlsToTry = mutableListOf<String>()
            val isGlobal = specialId!!.contains("_")
            
            if (isGlobal) {
                urlsToTry.add("https://gateway.kugou.com/v3/special/get_song_list?special_id=$specialId&page=1&page_size=999&plat=android")
                urlsToTry.add("https://youth-mobileservice.kugou.com/api/v1/collection/get_collection_song_list?global_specialid=$specialId&page=1&pagesize=999&plat=2&appid=1005")
                urlsToTry.add("http://mobileservice.kugou.com/api/v3/special/song?global_specialid=$specialId&page=1&pagesize=999&plat=2")
                urlsToTry.add("http://mobilecdnbj.kugou.com/api/v3/special/song?global_specialid=$specialId&page=1&pagesize=999&plat=0&specialid=-1")
            } else {
                urlsToTry.add("https://gateway.kugou.com/v3/special/get_song_list?special_id=$specialId&page=1&page_size=999&plat=android")
                urlsToTry.add("http://mobilecdnbj.kugou.com/api/v3/special/song?specialid=$specialId&page=1&pagesize=999&plat=0")
            }

            for (fullApiUrl in urlsToTry) {
                try {
                    Log.d(TAG, "Trying Kugou API: $fullApiUrl")
                    val apiRequest = Request.Builder()
                        .url(fullApiUrl)
                        .header("User-Agent", "KuGou/12.1.0 (Android; Mobile)")
                        .header("Referer", "https://www.kugou.com/")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val jsonStr = kugouClient.newCall(apiRequest).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.e(TAG, "kugou api code:${resp.code} for url: $fullApiUrl")
                            return@use ""
                        }
                        resp.body?.string() ?: ""
                    }
                    
                    if (jsonStr.isBlank()) continue

                    val json = JSONObject(jsonStr)
                    val data = json.optJSONObject("data") ?: continue
                    val infoArr = data.optJSONArray("list") ?: data.optJSONArray("info") ?: data.optJSONArray("audio_info") ?: continue

                    for (i in 0 until infoArr.length()) {
                        val item = infoArr.getJSONObject(i)
                        val hash = item.optString("hash").trim().ifEmpty { item.optString("320hash") }.ifEmpty { item.optString("sqhash") }
                        if (hash.isBlank()) continue

                        var songName = item.optString("song_name").ifEmpty { item.optString("songname") }.ifEmpty { item.optString("audio_name") }.trim()
                        var singerName = item.optString("singer_name").ifEmpty { item.optString("singername") }.ifEmpty { item.optString("author_name") }.trim()
                        val fileName = item.optString("filename").trim()

                        if (songName.isBlank() && fileName.contains(" - ")) {
                            val arr = fileName.split(" - ", limit = 2)
                            singerName = arr[0].trim()
                            songName = arr.getOrElse(1) { "" }.trim()
                        }
                        if (songName.isBlank()) songName = fileName
                        if (singerName.isBlank()) singerName = "未知歌手"

                        songs.add(Song(id = "kugou:$hash", title = songName, artist = singerName))
                    }
                    
                    if (songs.isNotEmpty()) {
                        Log.d(TAG, "Successfully parsed ${songs.size} songs from $fullApiUrl")
                        break // 成功获取到歌曲，停止尝试其他接口
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error trying API $fullApiUrl", e)
                }
            }
            return@withContext songs

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kugou playlist", e)
            emptyList()
        }
    }
}
