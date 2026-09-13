package com.thyids.free_music.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigInteger
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ResolvedPlayback(
    val url: String,
    val sourceName: String,
    val usedFallback: Boolean
)

/**
 * The backup providers follow the same shape as Listen 1: each platform has
 * its own search and play-url implementation, while the rest of the app only
 * sees a Song and a resolved URL.
 */
internal interface BackupMusicSource {
    val name: String
    fun search(query: String): List<Song>
    fun getPlayUrl(song: Song): String?
}

internal class MiguSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "Migu"

    private fun execute(request: Request): String? = execute(request, client)

    override fun search(query: String): List<Song> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://app.u.nf.migu.cn/pc/resource/song/item/search/v1.0" +
            "?text=$encoded&pageNo=1&pageSize=20"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Referer", "https://music.migu.cn/")
            .build()

        val text = execute(request) ?: return emptyList()
        val root = JSONTokener(text).nextValue()
        val items = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val title = item.optString("songName").trim()
            if (title.isBlank()) continue

            val copyrightId = item.optString("copyrightId").trim()
            val contentId = item.optString("contentId").trim()
            val songId = item.optString("songId").trim()
            if (copyrightId.isBlank() && contentId.isBlank()) continue

            val artist = item.optJSONArray("singerList")
                ?.optJSONObject(0)
                ?.optString("name")
                .orEmpty()
                .ifBlank { item.optString("singer") }
                .ifBlank { "Unknown artist" }
            val reference = JSONObject()
                .put("copyrightId", copyrightId)
                .put("contentId", contentId)
                .put("songId", songId)
                .toString()
            val encodedId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(reference.toByteArray(Charsets.UTF_8))

            songs.add(Song(id = "migu:$encodedId", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val reference = decodeReference(song.id) ?: return null
        val copyrightId = reference.optString("copyrightId")
        val contentId = reference.optString("contentId")
        if (copyrightId.isBlank() || contentId.isBlank()) return null

        for (toneFlag in listOf("PQ", "HQ")) {
            val url = "https://app.c.nf.migu.cn/MIGUM3.0/strategy/pc/listen/v1.0" +
                "?scene=&netType=01&resourceType=2&copyrightId=$copyrightId" +
                "&contentId=$contentId&toneFlag=$toneFlag"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Referer", "https://music.migu.cn/")
                .header("channel", "0146951")
                .header("uid", "1234")
                .build()
            val text = execute(request) ?: continue
            val data = runCatching { JSONObject(text).optJSONObject("data") }.getOrNull() ?: continue
            val playUrl = data.optString("url")
                .ifBlank { data.optString("playUrl") }
                .takeIf { it.isNotBlank() }
                ?: continue
            return normalizeUrl(playUrl)
        }
        return null
    }

    private fun decodeReference(id: String): JSONObject? {
        if (!id.startsWith("migu:")) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(id.removePrefix("migu:"))
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun normalizeUrl(url: String): String {
        val withScheme = if (url.startsWith("//")) "https:$url" else url
        return withScheme.replace("+", "%2B")
    }
}

/**
 * Netease exposes a stable outer-url endpoint. Search uses the same weapi
 * envelope as Listen 1, which avoids the plain search endpoint's bot check.
 */
internal class NeteaseSource(private val client: OkHttpClient) : BackupMusicSource {
    override val name = "Netease"

    private fun execute(request: Request): String? = execute(request, client)

    override fun search(query: String): List<Song> {
        val payload = JSONObject()
            .put("s", query)
            .put("type", 1)
            .put("limit", 20)
            .put("offset", 0)
            .put("total", true)
            .put("csrf_token", "")
            .toString()
        val encrypted = encryptWeapi(payload)
        val body = FormBody.Builder()
            .add("params", encrypted.params)
            .add("encSecKey", encrypted.encSecKey)
            .build()
        val request = Request.Builder()
            .url("https://music.163.com/weapi/cloudsearch/get/web?csrf_token=")
            .post(body)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.9.7")
            .build()
        val text = execute(request) ?: return emptyList()
        val result = runCatching { JSONObject(text).optJSONObject("result") }.getOrNull()
            ?: return emptyList()
        val items = result.optJSONArray("songs") ?: return emptyList()
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val title = item.optString("name").trim()
            if (id.isBlank() || title.isBlank()) continue
            val artist = artistNames(item).ifBlank { "Unknown artist" }
            songs.add(Song(id = "netease:$id", title = title, artist = artist))
        }
        return songs
    }

    override fun getPlayUrl(song: Song): String? {
        val id = song.id.removePrefix("netease:").takeIf { it != song.id } ?: return null
        val request = Request.Builder()
            .url("https://music.163.com/song/media/outer/url?id=$id.mp3")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.9.7")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty()
                if (finalUrl.contains("/404") || contentType.contains("text/html", ignoreCase = true)) {
                    null
                } else if (contentType.startsWith("audio", ignoreCase = true) ||
                    finalUrl.contains(".mp3", ignoreCase = true) ||
                    finalUrl.contains(".m4a", ignoreCase = true)
                ) {
                    finalUrl
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun artistNames(item: JSONObject): String {
        val artists = item.optJSONArray("ar") ?: item.optJSONArray("artists") ?: return ""
        val names = mutableListOf<String>()
        for (i in 0 until artists.length()) {
            val name = artists.optJSONObject(i)?.optString("name").orEmpty().trim()
            if (name.isNotBlank()) names.add(name)
        }
        return names.joinToString(" / ")
    }

    private data class EncryptedWeapi(val params: String, val encSecKey: String)

    private fun encryptWeapi(text: String): EncryptedWeapi {
        val secretKey = buildString {
            repeat(16) { append(NET_EASE_KEY_CHARS[secureRandom.nextInt(NET_EASE_KEY_CHARS.length)]) }
        }
        val firstPass = aesEncrypt(text, NET_EASE_NONCE)
        val params = aesEncrypt(firstPass, secretKey)
        val reversed = secretKey.reversed().toByteArray(Charsets.UTF_8)
        val encSecKey = BigInteger(1, reversed)
            .modPow(NET_EASE_PUBLIC_EXPONENT, NET_EASE_MODULUS)
            .toString(16)
            .padStart(256, '0')
        return EncryptedWeapi(params = params, encSecKey = encSecKey)
    }

    private fun aesEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec("0102030405060708".toByteArray(Charsets.UTF_8))
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    private companion object {
        val secureRandom = SecureRandom()
        const val NET_EASE_KEY_CHARS = "012345679abcdef"
        const val NET_EASE_NONCE = "0CoJUm6Qyw8W8jud"
        val NET_EASE_MODULUS = BigInteger(
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7",
            16
        )
        val NET_EASE_PUBLIC_EXPONENT = BigInteger("010001", 16)
    }
}

private fun execute(request: Request, client: OkHttpClient): String? {
    return runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()
}

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
