package com.example.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Satu kandidat video YouTube hasil pencarian dari tema/rencana. */
data class YouTubeCandidate(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationFormatted: String,
    val durationSec: Int,
    val thumbnailUrl: String,
    val viewCount: Long,
    val url: String
)

/**
 * Mencari video YouTube relevan dengan tema konten via YouTube Data API v3.
 *
 * Memerlukan API key (Settings -> YouTube Data API v3 Key). Bila key dibatasi ke
 * "Android apps" di Google Cloud Console, request WAJIB menyertakan header
 * X-Android-Package + X-Android-Cert; tanpa itu Google menolak dengan 401. Service ini
 * mengirim header tersebut otomatis (dihitung dari signature APK saat runtime).
 *
 * Jika gagal, `lastErrorMessage` diisi alasan sebenarnya (mis. keyInvalid,
 * accessNotConfigured, ipRefererBlocked, kuota) untuk diagnosis.
 */
class YouTubeSearchService(
    private val appContext: Context,
    private val getApiKey: () -> String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Pesan error terakhir dari YouTube Data API (untuk diagnosis 401/403/400). */
    @Volatile
    var lastErrorMessage: String? = null
        private set

    suspend fun searchRelevantVideos(query: String, maxResults: Int = 3): List<YouTubeCandidate> = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        val key = getApiKey().trim()
        val q = query.trim()
        if (key.isBlank()) {
            lastErrorMessage = "API key kosong. Isi 'YouTube Data API v3 Key' di Settings."
            return@withContext emptyList()
        }
        if (q.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val n = maxResults.coerceIn(1, 10)
            val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet" +
                "&type=video&videoEmbeddable=true&order=relevance&maxResults=" + n +
                "&q=" + encoded + "&key=" + key
            val searchReq = addAndroidKeyHeaders(Request.Builder().url(searchUrl).get()).build()
            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string()
            if (!searchResp.isSuccessful) {
                lastErrorMessage = parseApiError(searchResp.code, searchBody)
                Log.w("YouTubeSearch", "search.list " + lastErrorMessage)
                return@withContext emptyList()
            }
            val searchJson = JSONObject(searchBody ?: return@withContext emptyList())
            val items = searchJson.optJSONArray("items") ?: return@withContext emptyList()

            val ids = ArrayList<String>()
            val titleMap = HashMap<String, String>()
            val channelMap = HashMap<String, String>()
            val thumbMap = HashMap<String, String>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val idObj = it.optJSONObject("id") ?: continue
                val vid = idObj.optString("videoId")
                if (vid.isBlank()) continue
                val sn = it.optJSONObject("snippet")
                titleMap[vid] = sn?.optString("title") ?: vid
                channelMap[vid] = sn?.optString("channelTitle") ?: ""
                val thumbs = sn?.optJSONObject("thumbnails")
                val thumb = thumbs?.optJSONObject("high")?.optString("url")
                    ?: thumbs?.optJSONObject("medium")?.optString("url")
                    ?: thumbs?.optJSONObject("default")?.optString("url") ?: ""
                thumbMap[vid] = thumb
                ids.add(vid)
            }
            if (ids.isEmpty()) return@withContext emptyList()

            val durationMap = HashMap<String, Int>()
            val viewMap = HashMap<String, Long>()
            try {
                val detailsUrl = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails,statistics&id=" +
                    ids.joinToString(",") + "&key=" + key
                val detailsReq = addAndroidKeyHeaders(Request.Builder().url(detailsUrl).get()).build()
                val detailsResp = client.newCall(detailsReq).execute()
                if (detailsResp.isSuccessful) {
                    val detailsBody = detailsResp.body?.string() ?: ""
                    val detailsJson = JSONObject(detailsBody)
                    val detailItems = detailsJson.optJSONArray("items")
                    if (detailItems != null) {
                        for (i in 0 until detailItems.length()) {
                            val it = detailItems.optJSONObject(i) ?: continue
                            val vid = it.optString("id")
                            val iso = it.optJSONObject("contentDetails")?.optString("duration") ?: "PT0S"
                            durationMap[vid] = parseIsoDurationSec(iso)
                            val vc = it.optJSONObject("statistics")?.optString("viewCount") ?: "0"
                            viewMap[vid] = vc.toLongOrNull() ?: 0L
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("YouTubeSearch", "videos.list failed", e)
            }

            ids.mapNotNull { vid ->
                val title = titleMap[vid] ?: return@mapNotNull null
                val sec = durationMap[vid] ?: 0
                YouTubeCandidate(
                    videoId = vid,
                    title = decodeHtml(title),
                    channelName = decodeHtml(channelMap[vid] ?: ""),
                    durationFormatted = formatSec(sec),
                    durationSec = sec,
                    thumbnailUrl = thumbMap[vid] ?: "",
                    viewCount = viewMap[vid] ?: 0L,
                    url = "https://www.youtube.com/watch?v=" + vid
                )
            }
        } catch (e: Exception) {
            lastErrorMessage = "Gagal terhubung: " + (e.message ?: e.javaClass.simpleName)
            Log.w("YouTubeSearch", "search failed", e)
            emptyList()
        }
    }

    /**
     * Tambahkan header identitas Android app agar API key yang dibatasi ke "Android apps"
     * (rekomendasi Google) tetap diterima. Aman/harmless untuk key tanpa restriction.
     */
    private fun addAndroidKeyHeaders(builder: Request.Builder): Request.Builder {
        try {
            builder.addHeader("X-Android-Package", appContext.packageName)
            val cert = androidCertSha1()
            if (!cert.isNullOrBlank()) builder.addHeader("X-Android-Cert", cert)
        } catch (e: Exception) {
            Log.w("YouTubeSearch", "android key headers failed", e)
        }
        return builder
    }

    private fun androidCertSha1(): String? {
        return try {
            val pm = appContext.packageManager
            val pkg = appContext.packageName
            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            val sig = signatures?.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA1")
            val digest = md.digest(sig.toByteArray())
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseApiError(code: Int, body: String?): String {
        if (body.isNullOrBlank()) return "HTTP " + code
        return try {
            val err = JSONObject(body).optJSONObject("error")
            val msg = err?.optString("message") ?: ""
            val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason") ?: ""
            val hint = when {
                reason.contains("referer", true) || reason.contains("ipRefererBlocked", true) ->
                    " -> Key dibatasi (Application restriction). Set ke 'None', atau 'Android apps' dgn package '" + appContext.packageName + "' + SHA1 benar."
                reason.contains("accessNotConfigured", true) || msg.contains("has not been used", true) ->
                    " -> YouTube Data API v3 belum di-ENABLE di project ini. Aktifkan di Google Cloud Console."
                reason.contains("keyInvalid", true) || msg.contains("not valid", true) ->
                    " -> API key tidak valid. Cek ada spasi/typo."
                reason.contains("quota", true) || reason.contains("dailyLimit", true) ->
                    " -> Kuota harian habis (default 10.000 unit/hari)."
                else -> ""
            }
            "HTTP " + code + (if (reason.isNotBlank()) " [" + reason + "]" else "") + (if (msg.isNotBlank()) ": " + msg else "") + hint
        } catch (e: Exception) {
            "HTTP " + code
        }
    }

    private fun decodeHtml(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun parseIsoDurationSec(iso: String): Int {
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val m = regex.find(iso) ?: return 0
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val s = m.groupValues[3].toIntOrNull() ?: 0
        return h * 3600 + min * 60 + s
    }

    private fun formatSec(total: Int): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}
