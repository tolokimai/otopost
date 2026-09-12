package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Metadata + copywriting siap-posting untuk satu clip podcast. */
data class ClipContent(
    val hook: String,
    val caption: String,
    val hashtags: String,
    val thumbnailHook: String
)

/**
 * Generator konten per-clip (hook viral, caption, hashtag, teks hook thumbnail).
 * Dipisah dari GeminiService agar ringkas; memakai Gemini 2.5 Flash.
 */
class ClipContentService(private val getApiKey: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateClipContent(
        clipTitle: String,
        transcriptSnippet: String,
        podcastTopic: String,
        language: String = "id"
    ): ClipContent = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val safeSnippet = if (transcriptSnippet.length > 2000) transcriptSnippet.substring(0, 2000) else transcriptSnippet
        val prompt = buildString {
            append("Kamu Social Media Copywriter viral untuk TikTok, Reels, dan Shorts. ")
            append("Buat konten siap-posting untuk SATU clip podcast. ")
            append("Topik podcast: '").append(podcastTopic).append("'. ")
            append("Judul clip: '").append(clipTitle).append("'. ")
            append("Cuplikan transkrip clip: '").append(safeSnippet).append("'. ")
            append("Bahasa jawaban: ").append(language).append(". ")
            append("Balas HANYA sebagai JSON valid tanpa markdown, dengan field: hook, caption, hashtags, thumbnailHook. ")
            append("hook = kalimat 3 detik pertama yang sangat menghentak. ")
            append("caption = caption lengkap dengan storytelling dan call-to-action. ")
            append("hashtags = 5 hashtag relevan dipisah spasi, tiap hashtag diawali tanda pagar. ")
            append("thumbnailHook = 3 sampai 6 kata teks besar penuh energi untuk ditempel di thumbnail.")
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey
                val body = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply { put(JSONObject().put("text", prompt)) })
                        })
                    }
                    put("contents", contents)
                }
                val req = Request.Builder().url(url)
                    .post(body.toString().toRequestBody(jsonMediaType)).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val str = resp.body?.string() ?: ""
                        val root = JSONObject(str)
                        val cands = root.optJSONArray("candidates")
                        if (cands != null && cands.length() > 0) {
                            val text = cands.getJSONObject(0)
                                .optJSONObject("content")?.optJSONArray("parts")
                                ?.optJSONObject(0)?.optString("text") ?: ""
                            val json = extractJsonObject(text)
                            val hook = json.optString("hook", "")
                            val caption = json.optString("caption", "")
                            val hashtags = json.optString("hashtags", "")
                            val thumb = json.optString("thumbnailHook", "")
                            if (hook.isNotBlank() || caption.isNotBlank()) {
                                return@withContext ClipContent(
                                    hook = hook.ifBlank { clipTitle },
                                    caption = caption.ifBlank { clipTitle },
                                    hashtags = hashtags.ifBlank { "#podcast #clip #fyp #viral #shorts" },
                                    thumbnailHook = thumb.ifBlank { shortHook(clipTitle) }
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ClipContentService", "generateClipContent fallback", e)
            }
        }

        ClipContent(
            hook = clipTitle,
            caption = clipTitle + "\n\nTonton sampai habis, simpan, dan bagikan ke temanmu ya!",
            hashtags = "#podcast #clip #fyp #viral #shorts",
            thumbnailHook = shortHook(clipTitle)
        )
    }

    private fun shortHook(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= 5) title.trim().uppercase() else words.take(5).joinToString(" ").uppercase()
    }

    private fun extractJsonObject(text: String): JSONObject {
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val first = clean.indexOf('{')
        val last = clean.lastIndexOf('}')
        return if (first != -1 && last != -1 && last > first) JSONObject(clean.substring(first, last + 1))
        else JSONObject(clean)
    }
}
