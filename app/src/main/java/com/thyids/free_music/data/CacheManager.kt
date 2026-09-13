package com.thyids.free_music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class CacheManager(private val context: Context) {
    private val client = OkHttpClient.Builder().build()

    private fun getCacheDir(): File {
        val dir = File(context.cacheDir, "offline_music")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedFile(songId: String): File? {
        val file = File(getCacheDir(), "${songId}.mp3")
        return if (file.exists()) file else null
    }

    fun isCached(songId: String): Boolean {
        return getCachedFile(songId) != null
    }

    suspend fun cacheSong(songId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val file = File(getCacheDir(), "${songId}.mp3")
            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCache(songId: String) {
        getCachedFile(songId)?.delete()
    }

    fun getCacheSize(): Long {
        return getCacheDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearAllCache() {
        getCacheDir().deleteRecursively()
    }
}
