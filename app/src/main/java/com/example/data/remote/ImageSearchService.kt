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
 * Pencarian gambar dari internet ala Google Images / Pinterest.
 *
 * KENAPA DULU ERROR "Tidak ada gambar untuk 'night'": sumber lama (Openverse)
 * kini MEWAJIBKAN token otentikasi (Authorization: Bearer ...) untuk API-nya,
 * sehingga permintaan anonim dari app selalu ditolak -> hasil kosong.
 *
 * SOLUSI: sumber UTAMA sekarang Wikimedia Commons yang benar-benar tanpa API key
 * & stabil (ratusan juta gambar berlisensi bebas). Openverse tetap dicoba sebagai
 * cadangan best-effort kalau Commons kosong.
 */
class ImageSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    // Wikimedia mewajibkan User-Agent deskriptif dengan kontak.
    private val userAgent = "AutoPostStudio/1.0 (Android; https://autopost.studio; contact: app@autopost.studio)"

    suspend fun search(query: String, pageSize: Int = 24): List<WebImageResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val commons = try {
            searchWikimediaCommons(q, pageSize)
        } catch (e: Exception) {
            Log.w("ImageSearchService", "Commons gagal", e); emptyList()
        }
        if (commons.isNotEmpty()) return@withContext commons
        try {
            searchOpenverse(q, pageSize)
        } catch (e: Exception) {
            Log.w("ImageSearchService", "Openverse gagal", e); emptyList()
        }
    }

    private fun searchWikimediaCommons(query: String, pageSize: Int): List<WebImageResult> {
        val encoded = URLEncoder.encode("$query filetype:bitmap", "UTF-8")
        val limit = pageSize.coerceIn(1, 50)
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo" +
            "&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=$limit" +
            "&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=400"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.w("ImageSearchService", "Commons HTTP ${response.code}: ${raw.take(160)}")
                return emptyList()
            }
            val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val list = mutableListOf<WebImageResult>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                if (mime.isNotBlank() && !mime.startsWith("image/")) continue
                if (mime == "image/svg+xml") continue
                val full = info.optString("url")
                if (full.isBlank()) continue
                val thumb = info.optString("thumburl").ifBlank { full }
                val meta = info.optJSONObject("extmetadata")
                val license = meta?.optJSONObject("LicenseShortName")?.optString("value") ?: ""
                val artist = meta?.optJSONObject("Artist")?.optString("value") ?: ""
                list.add(
                    WebImageResult(
                        id = page.optString("pageid", page.optString("title")),
                        title = page.optString("title", "Commons image").removePrefix("File:"),
                        thumbnailUrl = thumb,
                        fullUrl = full,
                        source = "Wikimedia Commons",
                        creator = stripHtml(artist),
                        license = license
                    )
                )
            }
            return list
        }
    }

    private fun searchOpenverse(query: String, pageSize: Int): List<WebImageResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.openverse.org/v1/images/?q=$encoded&page_size=${pageSize.coerceIn(1, 40)}&mature=false"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.w("ImageSearchService", "Openverse HTTP ${response.code}: ${raw.take(160)}")
                return emptyList()
            }
            val results = JSONObject(raw).optJSONArray("results") ?: return emptyList()
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
            return list
        }
    }

    private fun stripHtml(s: String): String = s.replace(Regex("<[^>]*>"), "").trim()

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
