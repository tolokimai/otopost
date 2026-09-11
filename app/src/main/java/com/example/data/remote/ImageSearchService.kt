package com.example.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Satu hasil pencarian gambar dari internet.
 * - [fullUrl]: URL gambar resolusi penuh (dipakai sebagai background final).
 * - [thumbnailUrl]: URL thumbnail kecil (dipakai untuk grid pemilihan).
 */
data class WebImageResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val source: String,
    val creator: String,
    val license: String
)

/**
 * Pencarian gambar dari internet ala Google Images / Pinterest, memakai
 * Openverse API (https://api.openverse.org) yang GRATIS & tanpa API key, berisi
 * ratusan juta gambar berlisensi terbuka (Creative Commons / public domain).
 *
 * Alur di UI: user ketik keyword -> [search] -> tampilkan thumbnail -> user pilih
 * -> [downloadAsBase64] -> dipasang sebagai background slide.
 */
class ImageSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val userAgent = "AutoPostStudio/1.0 (Android; contact: app@autopost.studio)"

    suspend fun search(query: String, pageSize: Int = 24): List<WebImageResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url = "https://api.openverse.org/v1/images/?q=$encoded&page_size=$pageSize&mature=false"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w("ImageSearchService", "HTTP ${response.code}: ${raw.take(160)}")
                    return@withContext emptyList()
                }
                val json = JSONObject(raw)
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                val list = mutableListOf<WebImageResult>()
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    val full = o.optString("url")
                    if (full.isBlank()) continue
                    val thumb = o.optString("thumbnail").ifBlank { full }
                    list.add(
                        WebImageResult(
                            id = o.optString("id", i.toString()),
                            title = o.optString("title", "Tanpa judul"),
                            thumbnailUrl = thumb,
                            fullUrl = full,
                            source = o.optString("source", "openverse"),
                            creator = o.optString("creator", ""),
                            license = o.optString("license", "")
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e("ImageSearchService", "Pencarian gambar gagal", e)
            emptyList()
        }
    }

    /** Unduh gambar dari [url] dan kembalikan sebagai Base64 (NO_WRAP), atau null bila gagal. */
    suspend fun downloadAsBase64(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("ImageSearchService", "Download gagal HTTP ${response.code}")
                    return@withContext null
                }
                val bytes = response.body?.bytes() ?: return@withContext null
                if (bytes.isEmpty()) return@withContext null
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e("ImageSearchService", "Download gambar gagal", e)
            null
        }
    }
}
