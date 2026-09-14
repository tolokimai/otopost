package com.example.data.remote

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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

/** Media (video/foto/audio) yang berhasil di-upload ke Clip Server untuk proses remake/lipsync. */
data class UploadedMedia(
    val mediaId: String,
    val url: String
)

/** Hasil akhir remake/lipsync dari server (video jadi dengan suara baru). */
data class RemakeResult(
    val downloadUrl: String,
    val durationSec: Int
)

/**
 * Klien untuk OtoPost Clip Server (yt-dlp + ffmpeg + OpenCV).
 *
 * baseUrl contoh: https://chat.agenthebat.com/handle
 * Semua pekerjaan berat (download HD, potong, reframe, transkrip, subtitle, lipsync) dilakukan di server;
 * app hanya memanggil endpoint & mengunduh hasil.
 */
class ClipServerService(
    private val getBaseUrl: () -> String,
    private val getToken: () -> String = { "" }
) {
    // Timeout longgar: proses server (download HD + potong + reframe + subtitle + lipsync) bisa lama.
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Pesan error terakhir (status HTTP / body / timeout) agar UI bisa menampilkan sebab sebenarnya. */
    var lastError: String? = null
        private set

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
                val segmentArray = o.optJSONArray("segments")
                val timestampedTranscript = buildString {
                    if (segmentArray != null) {
                        for (i in 0 until segmentArray.length()) {
                            val segment = segmentArray.optJSONObject(i) ?: continue
                            val text = segment.optString("text").trim()
                            if (text.isBlank()) continue
                            val startSec = segment.optDouble("startSec", -1.0)
                            if (startSec < 0) continue
                            append('[')
                            append(formatClock(startSec.toInt()))
                            append("] ")
                            append(text)
                            append('\n')
                        }
                    }
                }.trim()
                val originalTranscript = o.optString("transcriptText", "").trim()
                val transcript = timestampedTranscript.ifBlank { originalTranscript }
                ServerTranscript(
                    videoId = o.optString("videoId"),
                    title = o.optString("title"),
                    channelName = o.optString("channelName"),
                    durationSec = o.optInt("durationSec", 0),
                    hasTranscript = o.optBoolean("hasTranscript", false) && transcript.isNotBlank(),
                    transcriptText = transcript
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatClock(totalSec: Int): String {
        val safe = totalSec.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Minta server mengunduh video HD lalu memotong tiap segmen (reframe 9:16 ke wajah).
     * segments: list Triple(startSec, endSec, title).
     * subtitle: bila true, server membakar (burn-in) subtitle otomatis ke tiap clip.
     * subtitleStyle: gaya subtitle (clean/bold/box/yellow/karaoke/tiktok/minimal/highlight).
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

    /**
     * Upload sebuah file (video/foto/audio) ke Clip Server untuk dipakai proses remake/lipsync.
     * kind: "video" | "photo" | "audio".
     * Return UploadedMedia (mediaId + url) atau null bila gagal.
     *
     * Endpoint server (WAJIB diimplementasikan di sisi server, karena server bisa dimodifikasi):
     *   POST /upload  (multipart/form-data)
     *     field "file" = berkas biner, field "kind" = jenis media.
     *   Respon JSON: { "mediaId": "...", "url": "..." }
     */
    suspend fun uploadMedia(file: File, kind: String): UploadedMedia? = withContext(Dispatchers.IO) {
        lastError = null
        if (!file.exists()) { lastError = "File tidak ditemukan: " + file.name; return@withContext null }
        try {
            val mime = when (kind) {
                "audio" -> "audio/mpeg"
                "photo" -> "image/*"
                else -> "video/*"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("kind", kind)
                .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                .build()
            val req = authed(base() + "/upload").post(body).build()
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string()
                if (!resp.isSuccessful) {
                    lastError = "HTTP " + resp.code + ": " + (respBody?.take(300) ?: resp.message)
                    return@withContext null
                }
                if (respBody == null) { lastError = "Respon upload kosong dari server."; return@withContext null }
                val o = JSONObject(respBody)
                val id = o.optString("mediaId")
                val url = o.optString("url")
                if (id.isBlank() && url.isBlank()) { lastError = "Server tidak mengembalikan mediaId."; return@withContext null }
                UploadedMedia(id, url)
            }
        } catch (e: java.net.SocketTimeoutException) {
            lastError = "Timeout saat upload (jaringan lambat / file besar). Coba lagi."
            null
        } catch (e: Exception) {
            lastError = "Error upload: " + (e.message ?: "tidak diketahui")
            null
        }
    }

    /**
     * Minta server menempelkan SUARA BARU ke media (foto/video) — TRUE lipsync (gerak bibir mengikuti
     * kata baru) bila wajah terdeteksi, atau overlay suara bila tidak.
     *
     * Aturan durasi: SUARA = acuan utama (tidak ada syarat video harus lebih panjang dari suara).
     *   - Foto  -> dijadikan video sepanjang audio (efek gerak / Ken Burns).
     *   - Video lebih pendek dari audio -> di-loop sampai audio selesai.
     *   - Video lebih panjang dari audio -> dipotong mengikuti panjang audio.
     *
     * Endpoint server (WAJIB diimplementasikan di sisi server):
     *   POST /lipsync  (application/json)  -> MULAI job async: balas { "job": "...", "status": "processing" }
     *     { "mediaId", "mediaKind", "audioId", "mode", "aspectRatio", "subtitle", "subtitleStyle" }
     *   GET  /lipsync-result/{job}  -> { "status": "processing|done|error", "downloadUrl"?, "durationSec"?, "reason"? }
     *   (Kompat: bila server lama langsung balas downloadUrl pada POST /lipsync, tetap dipakai.)
     */
    suspend fun requestRemake(
        mediaId: String,
        mediaKind: String,
        audioId: String,
        mode: String = "lipsync",
        aspectRatio: String = "9:16",
        subtitle: Boolean = false,
        subtitleStyle: String = "clean"
    ): RemakeResult? = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val payload = JSONObject()
                .put("mediaId", mediaId)
                .put("mediaKind", mediaKind)
                .put("audioId", audioId)
                .put("mode", mode)
                .put("aspectRatio", aspectRatio)
                .put("subtitle", subtitle)
                .put("subtitleStyle", subtitleStyle)
            // 1) MULAI job (balas cepat -> tidak kena timeout proxy 524)
            val startReq = authed(base() + "/lipsync")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            var job = ""
            client.newCall(startReq).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) {
                    val hint = when (resp.code) {
                        502, 503, 504 -> " (server/proxy timeout — perpanjang timeout proxy / coba lagi)"
                        413 -> " (file terlalu besar untuk server)"
                        401, 403 -> " (token server salah / kurang izin)"
                        404 -> " (endpoint /lipsync tidak ditemukan — update server ke versi async)"
                        else -> ""
                    }
                    lastError = "HTTP " + resp.code + hint + ": " + (body?.take(300) ?: resp.message)
                    return@withContext null
                }
                if (body == null) { lastError = "Respon kosong dari server."; return@withContext null }
                val o = JSONObject(body)
                // Kompat: server lama langsung membalas downloadUrl (sinkron).
                val directDl = o.optString("downloadUrl")
                if (directDl.isNotBlank()) {
                    return@withContext RemakeResult(directDl, o.optInt("durationSec", 0))
                }
                job = o.optString("job")
            }
            if (job.isBlank()) {
                lastError = "Server tidak mengembalikan job id (update server ke versi async)."
                return@withContext null
            }

            // 2) POLLING status job: banyak request pendek -> aman dari 524/timeout.
            val deadlineMs = System.currentTimeMillis() + 30L * 60L * 1000L
            var result: RemakeResult? = null
            var stop = false
            while (!stop && System.currentTimeMillis() < deadlineMs) {
                kotlinx.coroutines.delay(3000L)
                val pollReq = authed(base() + "/lipsync-result/" + job).get().build()
                client.newCall(pollReq).execute().use { resp ->
                    val body = resp.body?.string()
                    when {
                        resp.code == 404 -> { lastError = "Job tidak ditemukan (server mungkin restart)."; stop = true }
                        !resp.isSuccessful -> { lastError = "HTTP " + resp.code + ": " + (body?.take(200) ?: resp.message); stop = true }
                        body == null -> { lastError = "Respon status kosong dari server."; stop = true }
                        else -> {
                            val o = JSONObject(body)
                            when (o.optString("status")) {
                                "done" -> {
                                    val dl = o.optString("downloadUrl")
                                    if (dl.isBlank()) {
                                        lastError = o.optString("reason").ifBlank { "Server tidak mengembalikan downloadUrl." }
                                    } else {
                                        result = RemakeResult(dl, o.optInt("durationSec", 0))
                                    }
                                    stop = true
                                }
                                "error" -> {
                                    lastError = o.optString("reason").ifBlank { o.optString("detail").ifBlank { "Server gagal memproses remake." } }
                                    stop = true
                                }
                                else -> { /* processing: lanjut polling */ }
                            }
                        }
                    }
                }
            }
            if (result == null && lastError == null) {
                lastError = "Timeout menunggu hasil (>30 menit)."
            }
            result
        } catch (e: java.net.SocketTimeoutException) {
            lastError = "Timeout: server masih memproses / koneksi putus sebelum selesai. Video panjang bisa perlu >30 menit."
            null
        } catch (e: java.io.InterruptedIOException) {
            lastError = "Koneksi terputus saat menunggu hasil (timeout)."
            null
        } catch (e: Exception) {
            lastError = "Error: " + (e.message ?: "tidak diketahui")
            null
        }
    }

    /** Bentuk URL absolut untuk streaming/putar clip langsung di aplikasi. */
    fun absoluteUrl(downloadUrl: String): String {
        return if (downloadUrl.startsWith("http")) downloadUrl else (base() + downloadUrl)
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
