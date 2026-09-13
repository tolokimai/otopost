package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Membuat naskah narasi (voiceover script) siap-baca dari item di halaman Content Plan.
 * Naskah inilah yang diubah menjadi suara (ElevenLabs / rekam / upload) lalu ditempel ke
 * video/foto (lipsync/remake).
 *
 * Self-contained: memanggil Gemini Generative Language API langsung dengan API key dari Settings,
 * sehingga tidak bergantung pada internal GeminiService.
 */
class RemakeVoiceScriptService(
    private val getApiKey: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Hasilkan naskah narasi ~targetSeconds detik (~2.5 kata/detik).
     * Bila API gagal/ kosong, kembalikan naskah fallback dari hook + caption agar alur tetap jalan
     * (tidak mengarang topik di luar data Plan).
     */
    suspend fun generateScript(
        title: String,
        hook: String,
        captionDraft: String,
        language: String = "ID",
        targetSeconds: Int = 30
    ): String = withContext(Dispatchers.IO) {
        val fallback = buildFallback(title, hook, captionDraft)
        val key = getApiKey().trim()
        if (key.isBlank()) return@withContext fallback
        try {
            val targetWords = (targetSeconds.coerceIn(10, 120) * 2.5).toInt()
            val langName = if (language.equals("EN", true)) "English" else "Bahasa Indonesia"
            val prompt = buildString {
                append("Kamu penulis naskah video pendek (Reels/Shorts/TikTok).\n")
                append("Tulis SATU naskah narasi untuk dibacakan (voiceover), bukan caption.\n")
                append("Bahasa: ").append(langName).append(".\n")
                append("Panjang target: sekitar ").append(targetWords).append(" kata (~")
                append(targetSeconds).append(" detik bila dibacakan).\n")
                append("Gaya: hook kuat di 3 detik pertama, mengalir, mudah diucapkan, tanpa emoji, ")
                append("tanpa tanda kurung, tanpa petunjuk panggung.\n")
                append("Keluarkan HANYA teks naskahnya saja.\n\n")
                append("Judul konten: ").append(title).append("\n")
                if (hook.isNotBlank()) append("Hook: ").append(hook).append("\n")
                if (captionDraft.isNotBlank()) append("Draft caption/inti pesan: ").append(captionDraft).append("\n")
            }
            val payload = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            val url = BASE_URL + "/models/" + MODEL + ":generateContent?key=" + key
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext fallback
                if (!resp.isSuccessful) return@withContext fallback
                val text = extractText(body)
                if (text.isBlank()) fallback else text
            }
        } catch (e: Exception) {
            fallback
        }
    }

    private fun extractText(body: String): String {
        return try {
            val o = JSONObject(body)
            val candidates = o.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val t = parts.optJSONObject(i)?.optString("text") ?: ""
                if (t.isNotBlank()) sb.append(t)
            }
            sb.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildFallback(title: String, hook: String, caption: String): String {
        val parts = mutableListOf<String>()
        if (hook.isNotBlank()) parts.add(hook.trim())
        if (title.isNotBlank()) parts.add(title.trim())
        if (caption.isNotBlank()) parts.add(caption.trim())
        return parts.joinToString(" ").trim()
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MODEL = "gemini-2.5-flash"
    }
}
