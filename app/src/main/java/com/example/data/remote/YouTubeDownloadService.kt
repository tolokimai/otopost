package com.example.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Info file video yang berhasil diunduh ke penyimpanan lokal aplikasi. */
data class DownloadedVideo(
    val filePath: String,
    val itag: Int,
    val qualityLabel: String,
    val width: Int,
    val height: Int
)

/**
 * Pengunduh video YouTube on-device (best-effort).
 *
 * Memakai endpoint InnerTube (youtubei/v1/player) dengan konteks klien ANDROID untuk
 * mendapatkan URL stream progressive (audio+video tergabung) MP4: itag 22 (720p) atau
 * itag 18 (360p) yang biasanya tidak butuh signature deciphering.
 *
 * Sifatnya RAPUH: bisa berhenti bekerja saat YouTube mengubah API-nya. Bila gagal,
 * mengembalikan null dan pemanggil sebaiknya fallback ke unduh manual / server (yt-dlp).
 */
class YouTubeDownloadService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Kunci InnerTube publik yang dipakai klien Android YouTube (konstanta umum, bukan rahasia user).
    private val innertubeKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    private val androidUserAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip"

    /** Mengembalikan Triple(url, itag, [width,height]) untuk stream progressive, atau null. */
    suspend fun resolveProgressiveUrl(videoId: String): Triple<String, Int, IntArray>? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        try {
            val url = "https://www.youtube.com/youtubei/v1/player?key=" + innertubeKey
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.09.37")
                        put("androidSdkVersion", 34)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
            }
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", androidUserAgent)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w("YTDownload", "player HTTP " + resp.code)
                return@withContext null
            }
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val streaming = json.optJSONObject("streamingData") ?: return@withContext null
            val formats = streaming.optJSONArray("formats") ?: JSONArray()

            var chosen: JSONObject? = null
            var chosenItag = -1
            // Prioritas 720p (22), lalu 360p (18).
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                if (!f.has("url")) continue
                val itag = f.optInt("itag", -1)
                if (itag == 22) { chosen = f; chosenItag = 22; break }
                if (itag == 18) { chosen = f; chosenItag = 18 }
            }
            if (chosen == null) {
                for (i in 0 until formats.length()) {
                    val f = formats.optJSONObject(i) ?: continue
                    if (f.has("url")) { chosen = f; chosenItag = f.optInt("itag", 0); break }
                }
            }
            val picked = chosen ?: return@withContext null
            val streamUrl = picked.optString("url")
            if (streamUrl.isBlank()) return@withContext null
            Triple(streamUrl, chosenItag, intArrayOf(picked.optInt("width", 0), picked.optInt("height", 0)))
        } catch (e: Exception) {
            Log.w("YTDownload", "resolve failed", e)
            null
        }
    }

    /** Mengunduh stream progressive ke filesDir/podcast_src/yt_<id>.mp4. Null bila gagal. */
    suspend fun downloadVideo(
        context: Context,
        videoId: String,
        onProgress: (Float) -> Unit = {}
    ): DownloadedVideo? = withContext(Dispatchers.IO) {
        val resolved = resolveProgressiveUrl(videoId) ?: return@withContext null
        val streamUrl = resolved.first
        val itag = resolved.second
        val dim = resolved.third
        try {
            val req = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", androidUserAgent)
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w("YTDownload", "stream HTTP " + resp.code)
                return@withContext null
            }
            val respBody = resp.body ?: return@withContext null
            val total = respBody.contentLength()
            val dir = File(context.filesDir, "podcast_src").apply { mkdirs() }
            val outFile = File(dir, "yt_" + videoId + ".mp4")
            respBody.byteStream().use { input ->
                outFile.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        readTotal += r
                        if (total > 0) onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            if (!outFile.exists() || outFile.length() < 10_000L) return@withContext null
            val label = when (itag) {
                22 -> "720p"
                18 -> "360p"
                else -> "SD"
            }
            DownloadedVideo(outFile.absolutePath, itag, label, dim[0], dim[1])
        } catch (e: Exception) {
            Log.w("YTDownload", "download failed", e)
            null
        }
    }
}
