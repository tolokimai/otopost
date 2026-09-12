package com.example.data.remote

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

/** Hasil transkrip dari OtoPost Clip Server. */
data class ServerTranscript(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationSec: Int,
    val hasTranscript: Boolean,
    val transcriptText: String
)

/** Satu clip hasil potong di server, siap diunduh. */
data class ServerClip(
    val index: Int,
    val title: String,
    val startSec: Int,
    val endSec: Int,
    val reframed: Boolean,
    val downloadUrl: String
)

/** File video yang berhasil disimpan ke galeri (Movies/AutoPostStudio). */
data class SavedVideoFile(
    val uri: String,
    val displayName: String
)

/**
 * Klien untuk OtoPost Clip Server (yt-dlp + ffmpeg + OpenCV).
 *
 * baseUrl contoh: https://chat.agenthebat.com/handle
 * Semua pekerjaan berat (download HD, potong, reframe, transkrip, subtitle) dilakukan di server;
 * app hanya memanggil endpoint & mengunduh hasil clip.
 */
class ClipServerService(
    private val getBaseUrl: () -> String,
    private val getToken: () -> String = { "" }
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = getBaseUrl().trim().isNotBlank()

    private fun base(): String = getBaseUrl().trim().trimEnd('/')

    private fun authed(url: String): Request.Builder {
        val b = Request.Builder().url(url)
        val token = getToken().trim()
        if (token.isNotEmpty()) b.header("Authorization", "Bearer " + token)
        return b
    }

    /** Ambil transkrip ASLI dari server. Return null bila gagal koneksi/parsing. */
    suspend fun fetchTranscript(videoUrl: String): ServerTranscript? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("url", videoUrl)
            val req = authed(base() + "/transcript")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                if (!resp.isSuccessful) return@withContext null
                val o = JSONObject(body)
                ServerTranscript(
                    videoId = o.optString("videoId"),
                    title = o.optString("title"),
                    channelName = o.optString("channelName"),
                    durationSec = o.optInt("durationSec", 0),
                    hasTranscript = o.optBoolean("hasTranscript", false),
                    transcriptText = o.optString("transcriptText", "")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Minta server mengunduh video HD lalu memotong tiap segmen (reframe 9:16 ke wajah).
     * segments: list Triple(startSec, endSec, title).
     * subtitle: bila true, server membakar (burn-in) subtitle otomatis ke tiap clip.
     * subtitleStyle: gaya subtitle (clean/bold/box/yellow).
     */
    suspend fun requestClips(
        videoUrl: String,
        segments: List<Triple<Int, Int, String>>,
        aspectRatio: String,
        reframe: Boolean,
        subtitle: Boolean = false,
        subtitleStyle: String = "clean"
    ): List<ServerClip> = withContext(Dispatchers.IO) {
        try {
            val segArr = JSONArray()
            segments.forEach { seg ->
                segArr.put(
                    JSONObject()
                        .put("startSec", seg.first)
                        .put("endSec", seg.second)
                        .put("title", seg.third)
                )
            }
            val payload = JSONObject()
                .put("url", videoUrl)
                .put("aspectRatio", aspectRatio)
                .put("reframe", reframe)
                .put("subtitle", subtitle)
                .put("subtitleStyle", subtitleStyle)
                .put("segments", segArr)
            val req = authed(base() + "/clips")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext emptyList()
                if (!resp.isSuccessful) return@withContext emptyList()
                val o = JSONObject(body)
                val arr = o.optJSONArray("clips") ?: return@withContext emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val c = arr.optJSONObject(i) ?: return@mapNotNull null
                    ServerClip(
                        index = c.optInt("index", i + 1),
                        title = c.optString("title", "Clip " + (i + 1)),
                        startSec = c.optInt("startSec", 0),
                        endSec = c.optInt("endSec", 0),
                        reframed = c.optBoolean("reframed", false),
                        downloadUrl = c.optString("downloadUrl", "")
                    )
                }.filter { it.downloadUrl.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Unduh satu clip dari server & simpan ke galeri (Movies/AutoPostStudio). */
    suspend fun downloadClipToGallery(
        context: Context,
        downloadUrl: String,
        title: String
    ): SavedVideoFile? = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (downloadUrl.startsWith("http")) downloadUrl else (base() + downloadUrl)
            val req = authed(fullUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                saveVideoBytes(context, bytes, sanitize(title))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim().replace(Regex("[^a-zA-Z0-9-_ ]"), "").replace(" ", "_")
        val safe = if (cleaned.isBlank()) "otopost_clip" else cleaned.take(40)
        return safe + "_" + System.currentTimeMillis()
    }

    private fun saveVideoBytes(context: Context, bytes: ByteArray, baseName: String): SavedVideoFile? {
        val displayName = baseName + ".mp4"
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoPostStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SavedVideoFile(uri.toString(), displayName)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AutoPostStudio")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            file.outputStream().use { it.write(bytes) }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            SavedVideoFile(uri?.toString() ?: file.absolutePath, displayName)
        }
    }
}
