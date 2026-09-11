package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
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
 * Mencari video YouTube yang relevan dengan tema konten menggunakan YouTube Data API v3.
 * Memerlukan API key (Settings -> YouTube API Key). Jika key kosong atau tidak ada hasil,
 * mengembalikan list kosong sehingga pemanggil bisa menyarankan user mengganti isi konten.
 */
class YouTubeSearchService(private val getApiKey: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun searchRelevantVideos(query: String, maxResults: Int = 3): List<YouTubeCandidate> = withContext(Dispatchers.IO) {
        val key = getApiKey().trim()
        val q = query.trim()
        if (key.isBlank() || q.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val n = maxResults.coerceIn(1, 10)
            val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet" +
                "&type=video&videoEmbeddable=true&order=relevance&maxResults=" + n +
                "&q=" + encoded + "&key=" + key
            val searchReq = Request.Builder().url(searchUrl).get().build()
            val searchResp = client.newCall(searchReq).execute()
            if (!searchResp.isSuccessful) {
                Log.w("YouTubeSearch", "search.list HTTP " + searchResp.code)
                return@withContext emptyList()
            }
            val searchBody = searchResp.body?.string() ?: return@withContext emptyList()
            val searchJson = JSONObject(searchBody)
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
                val detailsReq = Request.Builder().url(detailsUrl).get().build()
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
            Log.w("YouTubeSearch", "search failed", e)
            emptyList()
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
