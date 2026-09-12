package com.example.data.remote

import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.PersonaEntity
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
 * Facade untuk GeminiService yang mengganti analisis podcast lama.
 *
 * Perbedaan penting:
 * - seluruh transkrip bertimestamp dibagi menjadi beberapa chunk berurutan;
 * - setiap chunk dianalisis dengan request AI terpisah;
 * - hasil wajib mempunyai timestamp dan kutipan yang benar-benar ada di transkrip;
 * - hasil semua chunk digabung, divalidasi, dan dideduplikasi;
 * - tidak pernah mengembalikan rekomendasi contoh/fiktif ketika AI gagal;
 * - jumlah hasil tidak dikunci menjadi tiga atau dibatasi secara global.
 */
class AccurateGeminiService(private val getApiKey: () -> String) {
    private val delegate = GeminiService(getApiKey)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(customKey: String? = null) = delegate.testConnection(customKey)
    suspend fun generatePersona(keywords: String, nicheHint: String, language: String) =
        delegate.generatePersona(keywords, nicheHint, language)
    suspend fun generateThemes(persona: PersonaEntity, count: Int = 4) =
        delegate.generateThemes(persona, count)
    suspend fun generateContentPlan(persona: PersonaEntity, theme: String, durationDays: Int) =
        delegate.generateContentPlan(persona, theme, durationDays)
    suspend fun generateCarouselSlides(topic: String, hook: String, slideCount: Int = 5, tone: String = "Informatif & Engaging") =
        delegate.generateCarouselSlides(topic, hook, slideCount, tone)
    suspend fun generateSlideImage(
        headline: String,
        body: String,
        theme: String,
        slideNumber: Int = 1,
        aspectRatio: String = "1:1",
        customPrompt: String = ""
    ) = delegate.generateSlideImage(headline, body, theme, slideNumber, aspectRatio, customPrompt)
    suspend fun generateVideoHooksAndCaptions(topic: String, style: String) =
        delegate.generateVideoHooksAndCaptions(topic, style)
    suspend fun generateVeoVideoFromText(
        prompt: String,
        aspectRatio: String = "9:16",
        stylePreset: String = "Cinematic 4K",
        durationSec: Int = 5
    ) = delegate.generateVeoVideoFromText(prompt, aspectRatio, stylePreset, durationSec)
    suspend fun animateImageWithVeo(
        imageDescription: String,
        motionPrompt: String,
        aspectRatio: String = "9:16"
    ) = delegate.animateImageWithVeo(imageDescription, motionPrompt, aspectRatio)

    private data class TimedLine(val sec: Int, val raw: String, val text: String)
    private data class TranscriptChunk(
        val startSec: Int,
        val endSec: Int,
        val lines: List<TimedLine>
    ) {
        val text: String = lines.joinToString("\n") { it.raw }
        val searchableText: String = lines.joinToString(" ") { it.text }
    }

    suspend fun analyzePodcastTranscript(
        topic: String,
        transcript: String,
        maxSegments: Int = 6
    ): List<PodcastSegmentHighlight> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        require(apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            "Gemini API Key belum tersedia. Rekomendasi contoh sengaja tidak digunakan."
        }
        require(transcript.isNotBlank()) { "Transkrip asli kosong." }
        require(!transcript.contains("[CONTOH -", ignoreCase = true)) {
            "Transkrip asli tidak tersedia. Rekomendasi AI dibatalkan agar tidak mengarang."
        }

        val timedLines = parseTimedLines(transcript)
        require(timedLines.size >= 3) {
            "Transkrip belum memiliki timestamp yang cukup untuk rekomendasi akurat."
        }

        val chunks = buildChunks(timedLines)
        val all = mutableListOf<PodcastSegmentHighlight>()
        val errors = mutableListOf<String>()
        val perChunkHint = maxSegments.coerceIn(4, 12)

        for ((index, chunk) in chunks.withIndex()) {
            try {
                all += analyzeChunk(
                    apiKey = apiKey,
                    topic = topic,
                    chunk = chunk,
                    chunkNumber = index + 1,
                    totalChunks = chunks.size,
                    perChunkHint = perChunkHint
                )
            } catch (e: Exception) {
                errors += "bagian ${index + 1}: ${e.message ?: "gagal"}"
            }
        }

        val valid = deduplicate(all)
            .filter { candidate ->
                candidate.startSec >= timedLines.first().sec &&
                    candidate.endSec <= timedLines.last().sec + 120 &&
                    candidate.endSec > candidate.startSec &&
                    candidate.transcriptSnippet.isNotBlank()
            }
            .sortedBy { it.startSec }

        check(valid.isNotEmpty()) {
            "AI tidak menghasilkan rekomendasi yang lolos validasi transkrip asli. ${errors.joinToString("; ")}"
        }
        valid
    }

    private fun parseTimedLines(transcript: String): List<TimedLine> {
        val regex = Regex("^\\s*\\[((?:\\d{1,2}:)?\\d{1,2}:\\d{2})]\\s*(.+?)\\s*$")
        return transcript.lineSequence().mapNotNull { raw ->
            val match = regex.find(raw) ?: return@mapNotNull null
            val sec = clockToSeconds(match.groupValues[1]) ?: return@mapNotNull null
            TimedLine(sec, "[${secondsToClock(sec)}] ${match.groupValues[2]}", match.groupValues[2])
        }.distinctBy { it.sec to it.text }.sortedBy { it.sec }.toList()
    }

    private fun buildChunks(lines: List<TimedLine>, maxChars: Int = 11_000): List<TranscriptChunk> {
        val chunks = mutableListOf<TranscriptChunk>()
        var cursor = 0
        while (cursor < lines.size) {
            val selected = mutableListOf<TimedLine>()
            var chars = 0
            var i = cursor
            while (i < lines.size) {
                val extra = lines[i].raw.length + 1
                if (selected.isNotEmpty() && chars + extra > maxChars) break
                selected += lines[i]
                chars += extra
                i++
            }
            if (selected.isNotEmpty()) {
                chunks += TranscriptChunk(selected.first().sec, selected.last().sec, selected)
            }
            if (i >= lines.size) break
            cursor = (i - 3).coerceAtLeast(cursor + 1)
        }
        return chunks
    }

    private fun analyzeChunk(
        apiKey: String,
        topic: String,
        chunk: TranscriptChunk,
        chunkNumber: Int,
        totalChunks: Int,
        perChunkHint: Int
    ): List<PodcastSegmentHighlight> {
        val prompt = """
            Kamu adalah editor senior short-form video. Analisis BAGIAN $chunkNumber DARI $totalChunks
            dari transkrip podcast asli bertimestamp tentang "$topic".

            Aturan keras:
            1. Temukan SEMUA bagian yang benar-benar layak menjadi klip mandiri, bukan tepat tiga.
            2. Boleh mengembalikan array kosong jika bagian ini tidak memiliki momen kuat.
            3. Biasanya 0-$perChunkHint kandidat per bagian, tetapi jangan memaksakan jumlah.
            4. Durasi ideal 20-90 detik; boleh 15-120 detik hanya jika konteks menuntut.
            5. startSec dan endSec wajib berasal dari timestamp transkrip di bawah.
            6. transcriptSnippet wajib berupa kutipan nyata dari transkrip, bukan parafrasa.
            7. Jangan membuat nama, angka, kejadian, kutipan, atau timestamp yang tidak ada.
            8. Hindari segmen berulang dan segmen tanpa konteks pembuka/penutup.

            Balas HANYA JSON array valid:
            [{"startSec":123,"endSec":178,"title":"judul spesifik sesuai isi","hook":"hook jujur berdasarkan isi","reasonWhyViral":"alasan konkret","transcriptSnippet":"kutipan asli"}]

            Rentang bagian: ${secondsToClock(chunk.startSec)}-${secondsToClock(chunk.endSec)}
            TRANSKRIP ASLI:
            ${chunk.text}
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", prompt))
            )))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("responseMimeType", "application/json")
            })
        }
        val request = Request.Builder().url(url)
            .post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Gemini HTTP ${response.code}: ${responseBody.take(300)}" }
            val root = JSONObject(responseBody)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            val array = extractJsonArray(text)
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                validateCandidate(item, chunk)
            }
        }
    }

    private fun validateCandidate(item: JSONObject, chunk: TranscriptChunk): PodcastSegmentHighlight? {
        val start = item.optInt("startSec", -1)
        val end = item.optInt("endSec", -1)
        val snippet = item.optString("transcriptSnippet").trim()
        if (start < chunk.startSec - 5 || end > chunk.endSec + 120 || end <= start) return null
        if ((end - start) !in 15..120 || snippet.length < 12) return null
        if (!isGrounded(snippet, chunk.searchableText)) return null
        return PodcastSegmentHighlight(
            startSec = start,
            endSec = end,
            durationFormatted = "${secondsToClock(start)} - ${secondsToClock(end)} (${end - start} detik)",
            title = item.optString("title").trim().ifBlank { return null },
            hook = item.optString("hook").trim(),
            reasonWhyViral = item.optString("reasonWhyViral").trim(),
            transcriptSnippet = snippet
        )
    }

    private fun isGrounded(snippet: String, source: String): Boolean {
        fun words(value: String) = value.lowercase()
            .replace(Regex("[^a-z0-9áéíóúàèìòùâêîôûäëïöüçñ ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 }
            .toSet()
        val quoteWords = words(snippet)
        if (quoteWords.size < 3) return false
        val sourceWords = words(source)
        return quoteWords.count { it in sourceWords }.toDouble() / quoteWords.size >= 0.65
    }

    private fun deduplicate(items: List<PodcastSegmentHighlight>): List<PodcastSegmentHighlight> {
        val sorted = items.sortedWith(compareBy<PodcastSegmentHighlight> { it.startSec }.thenByDescending { it.endSec - it.startSec })
        val kept = mutableListOf<PodcastSegmentHighlight>()
        for (candidate in sorted) {
            val duplicate = kept.any { existing ->
                val overlap = (minOf(existing.endSec, candidate.endSec) - maxOf(existing.startSec, candidate.startSec)).coerceAtLeast(0)
                val shorter = minOf(existing.endSec - existing.startSec, candidate.endSec - candidate.startSec).coerceAtLeast(1)
                overlap.toDouble() / shorter >= 0.65 || kotlin.math.abs(existing.startSec - candidate.startSec) <= 5
            }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    private fun extractJsonArray(text: String): JSONArray {
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val first = clean.indexOf('[')
        val last = clean.lastIndexOf(']')
        check(first >= 0 && last > first) { "Respons AI bukan JSON array." }
        return JSONArray(clean.substring(first, last + 1))
    }

    private fun clockToSeconds(clock: String): Int? {
        val parts = clock.split(':').mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun secondsToClock(total: Int): String {
        val safe = total.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}
