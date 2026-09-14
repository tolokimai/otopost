package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Hasil sintesis suara ElevenLabs, tersimpan sebagai file audio lokal (cache app). */
data class SynthesizedVoice(
    val file: File,
    val mimeType: String,
    val approxDurationSec: Int
)

/**
 * Klien Text-to-Speech ElevenLabs.
 *
 * Mengubah teks (naskah hasil generate dari halaman Plan) menjadi file audio mp3 yang dipakai
 * untuk remake/lipsync video. API key diambil dari Settings (getApiKey).
 *
 * Endpoint: POST https://api.elevenlabs.io/v1/text-to-speech/{voiceId}
 * Header  : xi-api-key: <API_KEY>
 */
class ElevenLabsService(
    private val getApiKey: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Pesan error terakhir (status HTTP / body) agar UI bisa menampilkan sebab sebenarnya. */
    var lastError: String? = null
        private set

    fun isConfigured(): Boolean = getApiKey().trim().isNotBlank()

    /**
     * Sintesis teks menjadi audio mp3. Return null bila gagal (key kosong / jaringan / API error).
     * voiceId default Rachel (multilingual). modelId multilingual agar mendukung Bahasa Indonesia.
     */
    suspend fun synthesizeToFile(
        context: Context,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID,
        modelId: String = DEFAULT_MODEL_ID,
        stability: Double = 0.5,
        similarityBoost: Double = 0.75
    ): SynthesizedVoice? = withContext(Dispatchers.IO) {
        lastError = null
        val key = getApiKey().trim()
        if (key.isBlank()) { lastError = "API key kosong"; return@withContext null }
        val cleanText = text.trim()
        if (cleanText.isBlank()) { lastError = "Naskah kosong"; return@withContext null }
        try {
            val voice = voiceId.trim().ifBlank { DEFAULT_VOICE_ID }
            val model = modelId.trim().ifBlank { DEFAULT_MODEL_ID }
            val payload = JSONObject()
                .put("text", cleanText)
                .put("model_id", model)
                .put(
                    "voice_settings",
                    JSONObject()
                        .put("stability", stability)
                        .put("similarity_boost", similarityBoost)
                )
            val req = Request.Builder()
                .url(BASE_URL + "/text-to-speech/" + voice)
                .header("xi-api-key", key)
                .header("Accept", "audio/mpeg")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    lastError = "HTTP " + resp.code + ": " + (resp.body?.string()?.take(300) ?: resp.message)
                    return@withContext null
                }
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.isEmpty()) return@withContext null
                val outDir = File(context.cacheDir, "remake_audio")
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, "tts_" + System.currentTimeMillis() + ".mp3")
                outFile.outputStream().use { it.write(bytes) }
                SynthesizedVoice(
                    file = outFile,
                    mimeType = "audio/mpeg",
                    approxDurationSec = estimateDurationSec(cleanText)
                )
            }
        } catch (e: Exception) {
            lastError = "Error: " + (e.message ?: "tidak diketahui")
            null
        }
    }

    /** Estimasi kasar durasi narasi (~2.5 kata/detik = 150 wpm) untuk menentukan panjang video. */
    private fun estimateDurationSec(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (words == 0) return 0
        val sec = Math.ceil(words / 2.5).toInt()
        return sec.coerceAtLeast(1)
    }

    /** Ambil daftar voice yang tersedia di akun (opsional, untuk pemilihan suara di UI). */
    suspend fun listVoices(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        lastError = null
        val key = getApiKey().trim()
        if (key.isBlank()) { lastError = "API key kosong"; return@withContext emptyList() }
        try {
            val req = Request.Builder()
                .url(BASE_URL + "/voices")
                .header("xi-api-key", key)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext emptyList()
                if (!resp.isSuccessful) {
                    lastError = "HTTP " + resp.code + ": " + body.take(300)
                    return@withContext emptyList()
                }
                val arr = JSONObject(body).optJSONArray("voices") ?: return@withContext emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("voice_id")
                    val name = o.optString("name")
                    if (id.isBlank()) null else Pair(id, name.ifBlank { id })
                }
            }
        } catch (e: Exception) {
            lastError = "Error: " + (e.message ?: "tidak diketahui")
            emptyList()
        }
    }

    companion object {
        const val BASE_URL = "https://api.elevenlabs.io/v1"
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel (multilingual)
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
    }
}
