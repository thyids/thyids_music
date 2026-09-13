package com.thyids.free_music.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal class KuwoSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "Kuwo"
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiresAt = 0L

    override fun search(query: String): List<Song> {
        val encoded = encode(query)
        val url = "https://www.kuwo.cn/search/searchMusicBykeyWord" +
            "?vipver=1&client=kt&ft=music&cluster=0&strategy=2012&encoding=utf8" +
            "&rformat=json&mobi=1&issubtitle=1&show_copyright_off=1&pn=0&rn=20&all=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.kuwo.cn/")
            .build()
        val text = executeRequest(request, client) ?: return emptyList()
        val items = runCatching {
            JSONObject(text).optJSONArray("abslist") ?: JSONArray()
        }.getOrDefault(JSONArray())
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("DC_TARGETID").ifBlank {
                item.optString("MUSICRID").substringAfterLast('_')
            }
            val title = decodeHtml(item.optString("NAME").ifBlank { item.optString("SONGNAME") })
            if (id.isBlank() || title.isBlank()) continue
            val artist = decodeHtml(item.optString("ARTIST")).ifBlank { "Unknown artist" }
            songs.add(Song(id = "kuwo:$id", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val id = song.id.removePrefix("kuwo:").takeIf { it != song.id } ?: return null
        for (attempt in 0 until 2) {
            val token = (if (attempt == 0) getToken() else getToken(forceRefresh = true)) ?: return null
            val secret = createKuwoSecret(token) ?: return null
            val url = "https://www.kuwo.cn/api/v1/www/music/playUrl" +
                "?mid=$id&type=music&httpsStatus=1&reqId=&plat=web_www&from="
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "https://www.kuwo.cn/")
                .header("Cookie", "$KUWO_TOKEN_NAME=$token")
                .header("Secret", secret)
                .build()
            val body = executeRequest(request, client) ?: continue
            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val playUrl = json.optJSONObject("data")?.optString("url").orEmpty()
            if (playUrl.startsWith("http")) return playUrl
            if (json.optString("msg").contains("付费")) return null
            cachedToken = null
        }
        return null
    }

    private fun getToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedToken?.takeIf { now < tokenExpiresAt }?.let { return it }
        }
        val request = Request.Builder()
            .url("https://www.kuwo.cn/play_detail/228908")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.headers.values("Set-Cookie")
                    .firstNotNullOfOrNull { cookie ->
                        Regex("$KUWO_TOKEN_NAME=([^;]+)")
                            .find(cookie)
                            ?.groupValues
                            ?.getOrNull(1)
                    }
            }
        }.getOrNull()?.also {
            cachedToken = it
            tokenExpiresAt = now + 6 * 60 * 60 * 1000L
        }
    }

    private fun createKuwoSecret(token: String): String? {
        val digits = buildString {
            KUWO_TOKEN_NAME.forEach { append(it.code) }
        }
        val position = digits.length / 5
        val multiplier = buildString {
            append(digits[position])
            append(digits[2 * position])
            append(digits[3 * position])
            append(digits[4 * position])
            append(digits[5 * position])
        }.toLongOrNull() ?: return null
        if (multiplier < 2) return null

        val increment = (KUWO_TOKEN_NAME.length + 1) / 2L
        val limit = (1L shl 31) - 1L
        val randomPart = (Math.round(Math.random() * 1_000_000_000.0) % 100_000_000L)
        var stateText = digits + randomPart
        while (stateText.length > 10) {
            val head = parseJsIntPrefix(stateText.substring(0, 10))
            val tail = parseJsIntPrefix(stateText.substring(10))
            stateText = (head + tail).toString()
        }
        var state = (multiplier * parseJsIntPrefix(stateText).toLong() + increment) % limit
        val result = StringBuilder(token.length * 2 + 8)
        token.forEach { character ->
            val mixed = character.code xor ((state.toDouble() / limit * 255.0).toInt())
            result.append(mixed.toString(16).padStart(2, '0'))
            state = (multiplier * state + increment) % limit
        }
        return result.append(randomPart.toString(16).padStart(8, '0')).toString()
    }

    private fun parseJsIntPrefix(value: String): Double {
        val digitsOnly = Regex("^\\d+").find(value)?.value ?: return 0.0
        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

    private companion object {
        const val KUWO_TOKEN_NAME = "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324"
    }
}

internal class QqSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "QQ"

    override fun search(query: String): List<Song> {
        val payload = JSONObject()
            .put("comm", JSONObject().put("ct", "19").put("cv", "1859").put("uin", "0"))
            .put(
                "req",
                JSONObject()
                    .put("method", "DoSearchForQQMusicDesktop")
                    .put("module", "music.search.SearchCgiService")
                    .put(
                        "param",
                        JSONObject()
                            .put("grp", 1)
                            .put("num_per_page", 20)
                            .put("page_num", 1)
                            .put("query", query)
                            .put("search_type", 0)
                    )
            )
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()
        val text = executeRequest(request, client) ?: return emptyList()
        val items = runCatching {
            JSONObject(text)
                .getJSONObject("req")
                .getJSONObject("data")
                .getJSONObject("body")
                .getJSONObject("song")
                .optJSONArray("list")
                ?: JSONArray()
        }.getOrDefault(JSONArray())
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val mid = item.optString("mid").trim()
            val title = decodeHtml(item.optString("name").ifBlank { item.optString("title") })
            if (mid.isBlank() || title.isBlank()) continue
            val artist = item.optJSONArray("singer")
                ?.optJSONObject(0)
                ?.optString("name")
                .orEmpty()
                .let(::decodeHtml)
                .ifBlank { "Unknown artist" }
            songs.add(Song(id = "qq:$mid", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val mid = song.id.removePrefix("qq:").takeIf { it != song.id } ?: return null
        val filename = "M500$mid$mid.mp3"
        val payload = JSONObject()
            .put(
                "req_1",
                JSONObject()
                    .put("module", "vkey.GetVkeyServer")
                    .put("method", "CgiGetVkey")
                    .put(
                        "param",
                        JSONObject()
                            .put("filename", JSONArray().put(filename))
                            .put("guid", "10000")
                            .put("songmid", JSONArray().put(mid))
                            .put("songtype", JSONArray().put(0))
                            .put("uin", "0")
                            .put("loginflag", 1)
                            .put("platform", "20")
                    )
            )
            .put("loginUin", "0")
            .put("comm", JSONObject().put("uin", "0").put("format", "json").put("ct", 24).put("cv", 0))
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()
        val text = executeRequest(request, client) ?: return null
        return runCatching {
            val data = JSONObject(text).getJSONObject("req_1").getJSONObject("data")
            val purl = data.getJSONArray("midurlinfo").optJSONObject(0)?.optString("purl").orEmpty()
            if (purl.isBlank()) return@runCatching null
            val sip = data.optJSONArray("sip")?.optString(0).orEmpty()
                .ifBlank { "https://dl.stream.qqmusic.qq.com/" }
            val fullUrl = if (purl.startsWith("http")) purl else sip + purl
            fullUrl.replaceFirst("http://", "https://")
        }.getOrNull()
    }
}

internal class BilibiliSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "Bilibili"

    override fun search(query: String): List<Song> {
        val url = "https://api.bilibili.com/x/web-interface/search/type" +
            "?__refresh__=true&page=1&page_size=20&platform=pc&highlight=1" +
            "&search_type=video&keyword=${encode(query)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .header("Cookie", "buvid3=0")
            .build()
        val text = executeRequest(request, client) ?: return emptyList()
        val items = runCatching {
            JSONObject(text).getJSONObject("data").optJSONArray("result") ?: JSONArray()
        }.getOrDefault(JSONArray())
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val bvid = item.optString("bvid").trim()
            val title = decodeHtml(item.optString("title"))
            if (bvid.isBlank() || title.isBlank()) continue
            val artist = decodeHtml(item.optString("author")).ifBlank { "Unknown artist" }
            songs.add(Song(id = "bilibili:$bvid", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val bvid = song.id.removePrefix("bilibili:").takeIf { it != song.id } ?: return null
        val pageRequest = Request.Builder()
            .url("https://api.bilibili.com/x/player/pagelist?bvid=$bvid")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/video/$bvid")
            .header("Cookie", "buvid3=0")
            .build()
        val pageText = executeRequest(pageRequest, client) ?: return null
        val cid = runCatching {
            JSONObject(pageText).getJSONArray("data").optJSONObject(0)?.optString("cid")
        }.getOrNull().orEmpty()
        if (cid.isBlank()) return null

        val playRequest = Request.Builder()
            .url("https://api.bilibili.com/x/player/playurl?fnval=16&bvid=$bvid&cid=$cid")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/video/$bvid")
            .header("Cookie", "buvid3=0")
            .build()
        val playText = executeRequest(playRequest, client) ?: return null
        return runCatching {
            val data = JSONObject(playText).getJSONObject("data")
            val audio = data.optJSONObject("dash")?.optJSONArray("audio")
            var bestUrl = ""
            var bestBandwidth = -1
            if (audio != null) {
                for (i in 0 until audio.length()) {
                    val item = audio.optJSONObject(i) ?: continue
                    val bandwidth = item.optInt("bandwidth")
                    if (bandwidth > bestBandwidth) {
                        bestBandwidth = bandwidth
                        bestUrl = item.optString("baseUrl")
                    }
                }
            }
            if (bestUrl.isBlank()) {
                bestUrl = data.optJSONArray("durl")?.optJSONObject(0)?.optString("url").orEmpty()
            }
            if (bestUrl.startsWith("//")) "https:$bestUrl" else bestUrl
        }.getOrNull()?.takeIf { it.startsWith("http") }
    }
}

internal class TaiheSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "Qianqian"

    override fun search(query: String): List<Song> {
        val url = "https://music.taihe.com/v1/search?" + signedQuery(
            mapOf("word" to query, "pageNo" to "1", "type" to "1")
        )
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://music.taihe.com/")
            .build()
        val text = executeRequest(request, client) ?: return emptyList()
        val items = runCatching {
            JSONObject(text).getJSONObject("data").optJSONArray("typeTrack") ?: JSONArray()
        }.getOrDefault(JSONArray())
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("assetId")
                .ifBlank { item.optString("TSID") }
                .ifBlank { item.optString("id") }
                .trim()
            val title = item.optString("title").trim()
            if (id.isBlank() || title.isBlank()) continue
            val artist = item.optJSONArray("artist")
                ?.optJSONObject(0)
                ?.optString("name")
                .orEmpty()
                .ifBlank { "Unknown artist" }
            songs.add(Song(id = "taihe:$id", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val id = song.id.removePrefix("taihe:").takeIf { it != song.id } ?: return null
        val url = "https://music.taihe.com/v1/song/tracklink?" + signedQuery(mapOf("TSID" to id))
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://music.taihe.com/")
            .build()
        val text = executeRequest(request, client) ?: return null
        return runCatching {
            JSONObject(text)
                .getJSONObject("data")
                .getJSONObject("data")
                .optString("path")
        }.getOrNull()?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { it.startsWith("http") }
    }

    private fun signedQuery(extra: Map<String, String>): String {
        val params = TreeMap<String, String>()
        params.putAll(extra)
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        params["appid"] = TAIHE_APP_ID
        val base = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val decoded = URLDecoder.decode(base, Charsets.UTF_8.name())
        val sign = md5(decoded + TAIHE_SECRET)
        return "$base&sign=$sign"
    }

    private fun md5(value: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAIHE_APP_ID = "16073360"
        const val TAIHE_SECRET = "0b50b02fd0d73a9c4c8c3a781c30845f"
    }
}

internal fun executeRequest(request: Request, client: OkHttpClient): String? {
    return runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()
}

private fun encode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun decodeHtml(value: String): String =
    runCatching { Jsoup.parse(value).text() }.getOrDefault(value)
