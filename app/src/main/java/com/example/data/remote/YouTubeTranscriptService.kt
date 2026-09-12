package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationFormatted: String,
    val transcriptText: String,
    val isSample: Boolean = false
)

/**
 * Mengambil transkrip ASLI dari YouTube secara on-device.
 *
 * Metode: endpoint InnerTube (youtubei/v1/player) untuk membaca daftar caption track
 * (captions.playerCaptionsTracklistRenderer.captionTracks), lalu mengunduh isi caption
 * dalam format json3. Ini menggantikan endpoint lama video.google.com/timedtext yang
 * sudah tidak berfungsi.
 *
 * Bila video benar-benar tidak punya caption (atau diblokir YouTube), akan mengembalikan
 * transkrip CONTOH yang DIBERI PENANDA jelas (isSample = true) agar tidak menyesatkan.
 */
class YouTubeTranscriptService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val innertubeKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    private val androidUserAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip"
    private val webUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val markerSample =
        "[CONTOH - transkrip asli tidak tersedia untuk video ini (tidak ada caption / diblokir). Berikut transkrip contoh]"

    fun extractVideoId(input: String): String {
        if (input.isBlank()) return "demo_podcast_01"
        if (input.length == 11 && !input.contains("/") && !input.contains(".")) {
            return input
        }
        val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=).{11}"
        val compiledPattern = Pattern.compile(pattern)
        val matcher = compiledPattern.matcher(input)
        if (matcher.find()) {
            return matcher.group()
        }
        return "podcast_" + input.take(8).replace(" ", "_").lowercase()
    }

    private data class PlayerData(
        val title: String,
        val author: String,
        val lengthSec: Int,
        val transcript: String
    )

    suspend fun fetchTranscriptForVideoId(
        videoId: String,
        fallbackTitle: String,
        fallbackChannel: String,
        durationFormatted: String
    ): YouTubeVideoInfo = withContext(Dispatchers.IO) {
        val player = try {
            fetchInnerTubeCaptions(videoId)
        } catch (e: Exception) {
            Log.w("YTTranscript", "innertube captions failed", e)
            null
        }
        if (player != null && player.transcript.isNotBlank()) {
            return@withContext YouTubeVideoInfo(
                videoId = videoId,
                title = player.title.ifBlank { fallbackTitle }.ifBlank { "YouTube Podcast" },
                channelName = player.author.ifBlank { fallbackChannel },
                durationFormatted = if (player.lengthSec > 0) formatSec(player.lengthSec) else durationFormatted.ifBlank { "--:--" },
                transcriptText = player.transcript,
                isSample = false
            )
        }
        val fb = sampleTranscript(fallbackTitle)
        fb.copy(
            videoId = videoId,
            title = fallbackTitle.ifBlank { fb.title },
            channelName = fallbackChannel.ifBlank { fb.channelName },
            durationFormatted = durationFormatted.ifBlank { fb.durationFormatted }
        )
    }

    suspend fun fetchVideoInfoAndTranscript(urlOrTopic: String): YouTubeVideoInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrTopic)
        val looksLikeRealVideo = videoId.length == 11 &&
            !videoId.startsWith("podcast_") &&
            videoId != "demo_podcast_01"
        if (looksLikeRealVideo) {
            val player = try {
                fetchInnerTubeCaptions(videoId)
            } catch (e: Exception) {
                Log.w("YTTranscript", "innertube captions failed", e)
                null
            }
            if (player != null && player.transcript.isNotBlank()) {
                return@withContext YouTubeVideoInfo(
                    videoId = videoId,
                    title = player.title.ifBlank { "YouTube Podcast" },
                    channelName = player.author,
                    durationFormatted = if (player.lengthSec > 0) formatSec(player.lengthSec) else "--:--",
                    transcriptText = player.transcript,
                    isSample = false
                )
            }
        }
        sampleTranscript(urlOrTopic).copy(videoId = videoId)
    }

    /** Ambil caption asli via InnerTube player (coba klien ANDROID lalu WEB). */
    private fun fetchInnerTubeCaptions(videoId: String): PlayerData? {
        if (videoId.length != 11) return null
        val configs = listOf(
            Triple("ANDROID", "19.09.37", androidUserAgent),
            Triple("WEB", "2.20240101.00.00", webUserAgent)
        )
        for (cfg in configs) {
            try {
                val pd = requestPlayer(videoId, cfg.first, cfg.second, cfg.third)
                if (pd != null && pd.transcript.isNotBlank()) return pd
            } catch (e: Exception) {
                Log.w("YTTranscript", "player " + cfg.first + " failed", e)
            }
        }
        return null
    }

    private fun requestPlayer(videoId: String, clientName: String, clientVersion: String, userAgent: String): PlayerData? {
        val url = "https://www.youtube.com/youtubei/v1/player?key=" + innertubeKey
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    put("hl", "id")
                    put("gl", "ID")
                    if (clientName == "ANDROID") put("androidSdkVersion", 34)
                })
            })
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            Log.w("YTTranscript", "player HTTP " + resp.code)
            return null
        }
        val body = resp.body?.string() ?: return null
        val json = JSONObject(body)
        val details = json.optJSONObject("videoDetails")
        val title = details?.optString("title") ?: ""
        val author = details?.optString("author") ?: ""
        val lengthSec = details?.optString("lengthSeconds")?.toIntOrNull() ?: 0

        val tracks = json.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
        if (tracks == null || tracks.length() == 0) {
            return PlayerData(title, author, lengthSec, "")
        }
        var idUrl = ""
        var enUrl = ""
        var firstUrl = ""
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val u = t.optString("baseUrl")
            if (u.isBlank()) continue
            if (firstUrl.isBlank()) firstUrl = u
            val lang = t.optString("languageCode")
            if (lang == "id" && idUrl.isBlank()) idUrl = u
            if (lang == "en" && enUrl.isBlank()) enUrl = u
        }
        val baseUrl = when {
            idUrl.isNotBlank() -> idUrl
            enUrl.isNotBlank() -> enUrl
            else -> firstUrl
        }
        if (baseUrl.isBlank()) return PlayerData(title, author, lengthSec, "")
        val transcript = fetchCaptionJson3(baseUrl, userAgent)
        return PlayerData(title, author, lengthSec, transcript)
    }

    private fun fetchCaptionJson3(baseUrl: String, userAgent: String): String {
        val url = if (baseUrl.contains("fmt=")) baseUrl else baseUrl + "&fmt=json3"
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return ""
        val body = resp.body?.string() ?: return ""
        return parseJson3(body)
    }

    private fun parseJson3(body: String): String {
        return try {
            val obj = JSONObject(body)
            val events = obj.optJSONArray("events") ?: return ""
            val sb = StringBuilder()
            var count = 0
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                val segs = ev.optJSONArray("segs") ?: continue
                val tMs = ev.optLong("tStartMs", 0L)
                val line = StringBuilder()
                for (j in 0 until segs.length()) {
                    line.append(segs.optJSONObject(j)?.optString("utf8") ?: "")
                }
                val text = line.toString().replace("\n", " ").trim()
                if (text.isNotBlank()) {
                    sb.append("[").append(formatSec((tMs / 1000L).toInt())).append("] ").append(text).append("\n")
                    count++
                }
            }
            if (count >= 3) sb.toString().trim() else ""
        } catch (e: Exception) {
            Log.w("YTTranscript", "json3 parse failed", e)
            ""
        }
    }

    private fun formatSec(total: Int): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    /** Transkrip CONTOH (ditandai jelas) untuk fallback bila caption asli tidak ada. */
    private fun sampleTranscript(urlOrTopic: String): YouTubeVideoInfo {
        val videoId = extractVideoId(urlOrTopic)
        if (urlOrTopic.contains("deddy", true) || urlOrTopic.contains("corbuzier", true)) {
            return YouTubeVideoInfo(
                videoId = videoId,
                title = "RAHASIA BISNIS 100 MILIAR TANPA MODAL INVESTOR! - Close The Door",
                channelName = "Deddy Corbuzier",
                durationFormatted = "48:20",
                transcriptText = markerSample + "\n" + """
                    [00:15] Deddy: Lu mulai dari umur berapa sebenarnya pas pertama kali jualan?
                    [00:30] Guest: Umur 19 tahun Om Ded. Waktu itu gue gak punya uang sepeserpun, modal cuma HP second.
                    [00:45] Deddy: Banyak anak muda sekarang alibi gak ada modal, gak ada privilege.
                    [01:05] Guest: Betul, padahal yang paling mahal itu bukan modal uang, tapi 'speed of execution' dan 'reputasi'.
                    [02:15] Deddy: Setuju banget. Kenapa orang susah dapet kepercayaan? Karena mereka pengen instan.
                    [02:40] Guest: Dalam 3 tahun pertama, gue gak ngambil gaji. Semua profit gue putar balik.
                """.trimIndent(),
                isSample = true
            )
        } else if (urlOrTopic.contains("hormozi", true) || urlOrTopic.contains("business", true)) {
            return YouTubeVideoInfo(
                videoId = videoId,
                title = "How To Get Rich In Your 20s (The Real Blueprint)",
                channelName = "Alex Hormozi",
                durationFormatted = "32:10",
                transcriptText = markerSample + "\n" + """
                    [00:20] Alex: If you want to make a million dollars, you have to acquire skills that are worth a million dollars.
                    [00:50] Alex: College teaches you theory, the market pays for outcome.
                    [01:30] Alex: Step 1: Learn one high-income skill like copywriting, media buying, or closing sales.
                    [02:10] Alex: Step 2: Work for free if you must to get 10 undeniable case studies.
                    [03:15] Alex: Step 3: Productize your service and raise your prices.
                """.trimIndent(),
                isSample = true
            )
        }
        val titleTopic = if (urlOrTopic.startsWith("http")) "Masterclass Strategi Konten Viral 2026" else urlOrTopic.replaceFirstChar { it.uppercase() }
        return YouTubeVideoInfo(
            videoId = videoId,
            title = titleTopic + " (Eksklusif Podcast)",
            channelName = "The Creators Podcast ID",
            durationFormatted = "25:40",
            transcriptText = markerSample + "\n" + """
                [00:10] Host: Kenapa algoritma media sosial sekarang lebih suka short-form content?
                [00:35] Speaker: Karena rentang perhatian audiens turun ke 3 detik. Kalau hook gagal, isi videomu sia-sia.
                [01:10] Host: Apa formula hook terbaik yang terbukti tembus 1 juta views?
                [01:40] Speaker: Formula 3E: Empathy, Element of Surprise, dan Easy Solution.
                [02:30] Host: Banyak kreator rajin upload tapi views tetap 200, kenapa?
                [02:55] Speaker: Karena fokus di kuantitas tanpa retensi.
            """.trimIndent(),
            isSample = true
        )
    }
}
