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

/**
 * Hasil generate gambar AI.
 * - [base64]: data gambar (tanpa prefix data URI) bila berhasil, null bila gagal.
 * - [isAiGenerated]: true HANYA jika gambar benar-benar datang dari model AI.
 * - [errorReason]: alasan gagal yang jujur untuk ditampilkan ke user.
 * - [modelUsed]: model yang berhasil dipakai (buat transparansi ke user).
 */
data class AiImageResult(
    val base64: String? = null,
    val isAiGenerated: Boolean = false,
    val errorReason: String? = null,
    val modelUsed: String? = null
)

/**
 * Service khusus generate GAMBAR (bukan teks) via Google Generative Language API.
 *
 * Kenapa dipisah dari GeminiService: model image generation butuh endpoint,
 * request body, dan parsing yang berbeda. Sebelumnya kode memanggil model yang
 * tidak ada ("gemini-3.1-flash-image") sehingga selalu 404 lalu diam-diam jatuh
 * ke generator grafis tema lokal (isAiGenerated=false) -> user melihat pesan
 * "sukses mengganti tema", padahal AI tidak pernah menghasilkan gambar.
 *
 * Urutan percobaan model (yang benar-benar tersedia di Generative Language API):
 *   1. gemini-2.5-flash-image-preview  ("Nano Banana", :generateContent, inlineData)
 *   2. imagen-3.0-generate-002          (:predict, predictions[].bytesBase64Encoded)
 *
 * Jika semua gagal, mengembalikan AiImageResult(base64=null, isAiGenerated=false,
 * errorReason=...) sehingga pemanggil bisa menampilkan pesan JUJUR & tidak menimpa
 * background yang ada.
 */
class AiImageService(private val getApiKey: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val invalidKeys = setOf("", "MY_GEMINI_API_KEY")

    suspend fun generateBackground(
        headline: String,
        theme: String,
        aspectRatio: String = "4:5",
        customPrompt: String = ""
    ): AiImageResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey in invalidKeys) {
            return@withContext AiImageResult(
                errorReason = "API Key Gemini belum diisi. Buka Settings dan masukkan Gemini API Key yang punya akses model gambar."
            )
        }

        val prompt = buildPrompt(headline, theme, aspectRatio, customPrompt)
        val normalizedRatio = normalizeRatio(aspectRatio)
        val errors = StringBuilder()

        // --- Percobaan 1: Gemini 2.5 Flash Image (Nano Banana) ---
        try {
            val res = tryGeminiFlashImage(prompt, apiKey)
            if (res != null) return@withContext AiImageResult(
                base64 = res,
                isAiGenerated = true,
                modelUsed = "gemini-2.5-flash-image-preview"
            )
        } catch (e: Exception) {
            Log.w("AiImageService", "Flash image gagal", e)
            errors.append("flash-image: ${e.message}; ")
        }

        // --- Percobaan 2: Imagen 3 ---
        try {
            val res = tryImagen(prompt, normalizedRatio, apiKey)
            if (res != null) return@withContext AiImageResult(
                base64 = res,
                isAiGenerated = true,
                modelUsed = "imagen-3.0-generate-002"
            )
        } catch (e: Exception) {
            Log.w("AiImageService", "Imagen gagal", e)
            errors.append("imagen: ${e.message}; ")
        }

        val reason = if (errors.isEmpty())
            "Model gambar tidak mengembalikan data. Kemungkinan API Key belum punya akses ke model gambar (Imagen/Gemini Image)."
        else
            "Model gambar menolak permintaan. Detail: ${errors.toString().take(300)}"
        AiImageResult(errorReason = reason)
    }

    private fun tryGeminiFlashImage(prompt: String, apiKey: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image-preview:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} ${extractApiError(raw)}")
            }
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates") ?: return null
            for (i in 0 until candidates.length()) {
                val parts = candidates.getJSONObject(i)
                    .optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (j in 0 until parts.length()) {
                    val part = parts.getJSONObject(j)
                    val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    val data = inline?.optString("data")
                    if (!data.isNullOrBlank()) return data
                }
            }
            return null
        }
    }

    private fun tryImagen(prompt: String, aspectRatio: String, apiKey: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=$apiKey"
        val body = JSONObject().apply {
            put("instances", JSONArray().apply {
                put(JSONObject().put("prompt", prompt))
            })
            put("parameters", JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", aspectRatio)
            })
        }
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} ${extractApiError(raw)}")
            }
            val json = JSONObject(raw)
            val predictions = json.optJSONArray("predictions") ?: return null
            for (i in 0 until predictions.length()) {
                val b64 = predictions.getJSONObject(i).optString("bytesBase64Encoded")
                if (b64.isNotBlank()) return b64
            }
            return null
        }
    }

    private fun extractApiError(raw: String): String = try {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(160)
    } catch (e: Exception) {
        raw.take(160)
    }

    private fun normalizeRatio(aspectRatio: String): String = when (aspectRatio) {
        "4:5", "3:4" -> "3:4"
        "9:16" -> "9:16"
        "16:9" -> "16:9"
        else -> "1:1"
    }

    private fun buildPrompt(headline: String, theme: String, aspectRatio: String, customPrompt: String): String {
        val themeKeywords = when (theme.uppercase()) {
            "MINIMALIST TECH", "MINIMAL TECH" -> "clean geometric slate, deep navy and utility blue gradients, subtle high-tech mesh lighting, sleek minimal 3D atmosphere"
            "CYBER NEON", "NEON" -> "vibrant cyberpunk neon volumetric lighting, dark slate backdrop, luminous laser light rays, futuristic synthwave atmosphere"
            "AESTHETIC PASTEL", "PASTEL" -> "soft warm pastel dreamscape, soothing cream and peach aura gradients, organic fluid soft lighting"
            "DARK LUXURY", "LUXURY" -> "deep obsidian matte black, metallic gold light streaks, champagne amber radiance, ultra premium luxury texture"
            "VINTAGE RETRO", "RETRO" -> "1980s retro sunset palette, warm analog grain, teal and burnt orange wave gradients, nostalgic art"
            "3D ILLUSTRATION", "3D ABSTRACT", "3D" -> "glossy 3D abstract shapes, smooth clay studio render, vibrant studio illumination, soft drop shadows"
            else -> "modern social media card background, clean minimalist aesthetic texture, balanced atmospheric lighting"
        }
        val safeCustom = sanitize(customPrompt)
        val base = if (safeCustom.isNotBlank()) {
            "Ultra high quality aesthetic social media background wallpaper. User creative direction: '$safeCustom'. Base visual mood: $themeKeywords."
        } else {
            "Ultra high quality aesthetic background wallpaper for topic: '$headline'. Visual style: $themeKeywords."
        }
        return "$base Aspect ratio $aspectRatio, vertical composition with clear negative space for overlaid text. PURE BACKGROUND ART ONLY. STRICTLY NO TEXT, NO LETTERS, NO WORDS, NO NUMBERS, NO WATERMARKS, NO LOGOS, NO TYPOGRAPHY, NO UI ELEMENTS. Safe-for-work, non-violent, brand friendly, tasteful composition."
    }

    private fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.length > 400) s = s.substring(0, 400)
        val banned = listOf("nude", "naked", "nsfw", "sex", "porn", "gore", "blood", "violence", "weapon", "kill", "nazi", "terror")
        for (b in banned) s = s.replace(Regex("(?i)" + Regex.escape(b)), "")
        return s.trim()
    }
}
