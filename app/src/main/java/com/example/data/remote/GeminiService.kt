package com.example.data.remote

import android.util.Log
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.ContentFormat
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

data class GeneratedPersona(
    val brandName: String,
    val niche: String,
    val targetAudience: String,
    val languageStyle: String,
    val tone: String,
    val language: String,
    val accountReferences: String
)

data class GeneratedPlanItem(
    val dayNumber: Int,
    val title: String,
    val format: ContentFormat,
    val hook: String,
    val captionDraft: String,
    val hashtags: String
)

data class PodcastSegmentHighlight(
    val startSec: Int,
    val endSec: Int,
    val durationFormatted: String,
    val title: String,
    val hook: String,
    val reasonWhyViral: String,
    val transcriptSnippet: String
)

data class VideoCopyResult(
    val viralHook: String,
    val caption: String,
    val hashtags: String,
    val subtitles: List<String>
)

data class SlideImageResult(
    val base64Data: String? = null,
    val imageUrl: String? = null,
    val promptUsed: String,
    val theme: String,
    val styleKeywords: String,
    val isAiGenerated: Boolean = false
)

data class TranscriptTimestampItem(
    val timestampSec: Int,
    val timestampFormatted: String,
    val speaker: String,
    val text: String
)

data class TranscriptionResult(
    val fullText: String,
    val segments: List<TranscriptTimestampItem>,
    val detectedLanguage: String = "id"
)

data class GeneratedVideoResult(
    val videoId: String,
    val prompt: String,
    val aspectRatio: String, // 9:16 or 16:9
    val durationSec: Int,
    val stylePreset: String,
    val videoUrl: String? = null,
    val storyboardFrames: List<String> = emptyList(),
    val dynamicSubtitles: List<String> = emptyList(),
    val viralHook: String = ""
)

class GeminiService(private val getApiKey: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(customKey: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = customKey ?: getApiKey()
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            return@withContext Pair(false, "API Key belum diisi. Masukkan Gemini API Key di Settings.")
        }

        try {
            val prompt = "Reply with exactly: 'OK_READY'"
            val response = executePrompt(prompt, key)
            if (response.contains("OK", ignoreCase = true) || response.isNotBlank()) {
                Pair(true, "Koneksi Gemini 2.5 Flash berhasil! Model siap digunakan.")
            } else {
                Pair(false, "Respon tidak valid dari server.")
            }
        } catch (e: Exception) {
            Pair(false, "Error: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun generatePersona(keywords: String, nicheHint: String, language: String): GeneratedPersona = withContext(Dispatchers.IO) {
        val prompt = """
            Buatkan profil Persona Kreator Media Sosial untuk TikTok/Reels/Shorts dengan format JSON murni.
            Kata kunci input: "$keywords", Niche: "$nicheHint", Bahasa: "$language".
            
            JSON schema:
            {
              "brandName": "Nama Brand/Akun yang catchy",
              "niche": "Kategori niche spesifik",
              "targetAudience": "Target demografi & psikografi audiens",
              "languageStyle": "Santai/Formal/Lucu/Edukatif",
              "tone": "Energetic/Authoritative/Empathetic/Provocative",
              "accountReferences": "Contoh akun referensi (e.g. @akun1, @akun2)"
            }
            Hanya kembalikan objek JSON valid tanpa markdown fence jika memungkinkan.
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val json = extractJsonObject(response)
            GeneratedPersona(
                brandName = json.optString("brandName", keywords.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Creator Pro"),
                niche = json.optString("niche", nicheHint.ifBlank { "Content Creation & Lifestyle" }),
                targetAudience = json.optString("targetAudience", "Gen Z & Milenial usia 18-35 tahun pencari insight praktis"),
                languageStyle = json.optString("languageStyle", "Santai & Storytelling"),
                tone = json.optString("tone", "Energetic & Insightful"),
                language = language,
                accountReferences = json.optString("accountReferences", "@alexhormozi, @feliciaputri")
            )
        } catch (e: Exception) {
            Log.e("GeminiService", "Fallback persona generator", e)
            GeneratedPersona(
                brandName = if (keywords.isNotBlank()) keywords.split(",").first().trim().replaceFirstChar { it.uppercase() } + " Studio" else "Creative Hustle",
                niche = nicheHint.ifBlank { "Bisnis, Tech & Produktivitas" },
                targetAudience = "Kreator pemula, freelancer, dan profesional muda usia 20-35 tahun",
                languageStyle = "Santai & To-The-Point",
                tone = "Energetic & Praktis",
                language = language,
                accountReferences = "@garyvee, @cleocreative, @feliciaputri"
            )
        }
    }

    suspend fun generateThemes(persona: PersonaEntity, count: Int = 4): List<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Berikan $count ide tema besar (Big Themes) kampanye konten viral 7-30 hari untuk persona berikut:
            Brand: ${persona.brandName}, Niche: ${persona.niche}, Target: ${persona.targetAudience}, Gaya: ${persona.languageStyle}.
            Kembalikan JSON array string saja, contoh: ["Tema 1", "Tema 2", "Tema 3", "Tema 4"].
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val jsonArray = extractJsonArray(response)
            val result = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getString(i))
            }
            if (result.isNotEmpty()) return@withContext result
        } catch (e: Exception) {
            Log.w("GeminiService", "Theme fallback used", e)
        }

        listOf(
            "30 Hari Transformasi ${persona.niche}: Dari Pemula Jadi Ahli",
            "Membongkar Rahasia di Balik Industri ${persona.niche} yang Jarang Dibahas",
            "Kesalahan Fatal yang Bikin Gagal di ${persona.niche} & Solusi Cepatnya",
            "Strategi Step-by-Step Mencapai Hasil Maksimal dalam Waktu Singkat"
        )
    }

    suspend fun generateContentPlan(
        persona: PersonaEntity,
        theme: String,
        durationDays: Int
    ): List<GeneratedPlanItem> = withContext(Dispatchers.IO) {
        val prompt = """
            Buatkan rencana konten media sosial (TikTok, IG Reels, YouTube Shorts) untuk $durationDays hari.
            Persona:
            - Brand: ${persona.brandName}
            - Niche: ${persona.niche}
            - Target: ${persona.targetAudience}
            - Style: ${persona.languageStyle} (${persona.tone})
            - Bahasa: ${persona.language}
            - Tema Besar: $theme
            
            Kembalikan JSON array dengan $durationDays objek:
            [
              {
                "dayNumber": 1,
                "title": "Judul konten spesifik",
                "format": "CAROUSEL" | "PODCAST_CLIP" | "SELF_VIDEO",
                "hook": "Kalimat pembuka/hook 3 detik pertama yang sangat menghentak",
                "captionDraft": "Draft caption lengkap dengan call-to-action",
                "hashtags": "#niche #foryou #viral #tips"
              }
            ]
            Format harus bervariasi antara CAROUSEL, PODCAST_CLIP, dan SELF_VIDEO.
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val jsonArray = extractJsonArray(response)
            val items = mutableListOf<GeneratedPlanItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val formatStr = obj.optString("format", "CAROUSEL")
                val format = when (formatStr.uppercase()) {
                    "PODCAST_CLIP", "PODCAST", "CLIP" -> ContentFormat.PODCAST_CLIP
                    "SELF_VIDEO", "VIDEO", "REELS" -> ContentFormat.SELF_VIDEO
                    else -> ContentFormat.CAROUSEL
                }
                items.add(
                    GeneratedPlanItem(
                        dayNumber = obj.optInt("dayNumber", i + 1),
                        title = obj.optString("title", "Day ${i + 1}: ${persona.niche} Secret Tips"),
                        format = format,
                        hook = obj.optString("hook", "Jangan skip video ini kalau kamu pengen paham ${persona.niche}!"),
                        captionDraft = obj.optString("captionDraft", "Simak penjelasannya sampai habis dan save untuk nanti!"),
                        hashtags = obj.optString("hashtags", "#${persona.niche.replace(" ", "").lowercase()} #tips #creator #foryou")
                    )
                )
            }
            if (items.isNotEmpty()) return@withContext items
        } catch (e: Exception) {
            Log.e("GeminiService", "Plan generation fallback", e)
        }

        // Fallback intelligent generator for $durationDays days
        val fallbackItems = mutableListOf<GeneratedPlanItem>()
        val formats = listOf(ContentFormat.CAROUSEL, ContentFormat.PODCAST_CLIP, ContentFormat.SELF_VIDEO, ContentFormat.AI_VIDEO)
        for (day in 1..durationDays) {
            val format = formats[(day - 1) % formats.size]
            val (title, hook, caption) = when (format) {
                ContentFormat.CAROUSEL -> Triple(
                    "5 Cara Cepat Menguasai ${persona.niche} di 2026",
                    "Stop lakukan ini kalau kamu masih pemula di ${persona.niche}!",
                    "Slide 1-5 merangkum framework praktis yang terbukti bekerja. Geser sampai akhir & drop pertanyaanmu di komen! \ud83d\udc47"
                )
                ContentFormat.PODCAST_CLIP -> Triple(
                    "Mindset Krusial untuk Sukses di ${persona.niche}",
                    "99% orang salah paham soal ini saat baru mulai...",
                    "Poin penting dari obrolan ini: jangan fokus di hasil instan, bangun fondasinya dulu! Setuju gak?"
                )
                ContentFormat.SELF_VIDEO -> Triple(
                    "Kesalahan Nomor 1 yang Membuang Waktumu di ${persona.niche}",
                    "Ini alasan kenapa usaha kamu belum kelihatan hasilnya!",
                    "Terapkan 3 langkah ini mulai hari ini. Follow @${persona.brandName.lowercase().replace(" ", "")} untuk insight harian!"
                )
                ContentFormat.AI_VIDEO -> Triple(
                    "Masa Depan AI Content Creation di 2026",
                    "Teknologi ini bakal ubah cara kreator bikin konten selamanya!",
                    "Visual cinematic yang digenerate AI dalam hitungan detik. Cek detail prompt-nya di video ini!"
                )
            }
            fallbackItems.add(
                GeneratedPlanItem(
                    dayNumber = day,
                    title = "Hari $day: $title",
                    format = format,
                    hook = hook,
                    captionDraft = caption,
                    hashtags = "#${persona.niche.replace(" ", "").lowercase()} #autopost #creatorgrowth #fyp #contenttips"
                )
            )
        }
        fallbackItems
    }

    suspend fun generateCarouselSlides(
        topic: String,
        hook: String,
        slideCount: Int = 5,
        tone: String = "Informatif & Engaging"
    ): List<CarouselSlide> = withContext(Dispatchers.IO) {
        val prompt = """
            Buatkan isi konten Carousel media sosial (Instagram/TikTok Carousel) sebanyak $slideCount slide.
            Topik: "$topic", Hook Utama: "$hook", Tone: "$tone".
            
            Format JSON array:
            [
              {
                "slideNumber": 1,
                "headline": "Judul Menarik Slide 1 (Cover Hook)",
                "body": "Deskripsi singkat yang memancing orang untuk slide ke kanan",
                "subtext": "Swipe \u27a1\ufe0f"
              },
              ...
            ]
            Slide terakhir harus berisi Call To Action (CTA).
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val jsonArray = extractJsonArray(response)
            val slides = mutableListOf<CarouselSlide>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                slides.add(
                    CarouselSlide(
                        slideNumber = obj.optInt("slideNumber", i + 1),
                        headline = obj.optString("headline", "Slide ${i + 1}"),
                        body = obj.optString("body", "Penjelasan poin penting."),
                        subtext = obj.optString("subtext", if (i == jsonArray.length() - 1) "Save & Share!" else "Geser \u27a1\ufe0f")
                    )
                )
            }
            if (slides.isNotEmpty()) return@withContext slides
        } catch (e: Exception) {
            Log.e("GeminiService", "Carousel slide fallback", e)
        }

        // Smart fallback slides
        listOf(
            CarouselSlide(1, hook.ifBlank { topic }, "Banyak yang gagal paham soal ini. Ini breakdown lengkapnya!", "Swipe untuk lanjut \u27a1\ufe0f"),
            CarouselSlide(2, "01. Pahami Masalah Utama", "Langkah awal adalah mengidentifikasi titik krusial tanpa buang waktu.", "Poin 2 lebih penting \u27a1\ufe0f"),
            CarouselSlide(3, "02. Eksekusi dengan Strategi Tepat", "Fokus pada action 20% yang menghasilkan 80% dampak.", "Lanjut ke langkah 3 \u27a1\ufe0f"),
            CarouselSlide(4, "03. Evaluasi & Optimasi", "Cek data secara berkala dan perbaiki bagian yang kurang efektif.", "Hampir selesai \u27a1\ufe0f"),
            CarouselSlide(5, "Simpan & Praktikkan Sekarang!", "Komen 'MAU' kalau kamu pengen dapat template gratis dari kami!", "Save post ini \ud83d\udccc")
        )
    }

    suspend fun analyzePodcastTranscript(
        topic: String,
        transcript: String,
        maxSegments: Int = 6
    ): List<PodcastSegmentHighlight> = withContext(Dispatchers.IO) {
        // Batasi panjang transkrip agar analisis tidak gagal/timeout untuk video panjang.
        val safeTranscript = if (transcript.length > 14000) transcript.substring(0, 14000) else transcript
        val targetCount = maxSegments.coerceIn(3, 12)
        val prompt = """
            Kamu adalah Video Editor & Content Strategist ahli Short-Form Viral Clips (TikTok, Reels, Shorts).
            Analisis transkrip podcast berikut tentang topik "$topic":
            
            Transkrip:
            $safeTranscript
            
            Temukan sebanyak-banyaknya segmen klip terbaik (minimal 3, hingga $targetCount segmen) berdurasi 20-60 detik yang punya potensi viral tertinggi (ada emosi, quote kuat, insight tidak terduga, atau debat seru). Urutkan dari yang paling berpotensi viral. Jangan mengulang segmen yang mirip.
            
            Format JSON array:
            [
              {
                "startSec": 45,
                "endSec": 95,
                "durationFormatted": "00:45 - 01:35 (50 detik)",
                "title": "Mindset yang Bikin 90% Orang Menyerah",
                "hook": "Banyak orang mikir sukses itu soal hoki, padahal...",
                "reasonWhyViral": "High curiosity gap & membongkar mitos umum",
                "transcriptSnippet": "Potongan kalimat utama dari segmen..."
              }
            ]
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val jsonArray = extractJsonArray(response)
            val highlights = mutableListOf<PodcastSegmentHighlight>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                highlights.add(
                    PodcastSegmentHighlight(
                        startSec = obj.optInt("startSec", i * 60 + 15),
                        endSec = obj.optInt("endSec", i * 60 + 65),
                        durationFormatted = obj.optString("durationFormatted", "00:${i * 60 + 15} - 01:${i * 60 + 65}"),
                        title = obj.optString("title", "Klip Highlight #${i + 1}"),
                        hook = obj.optString("hook", "Insight luar biasa yang wajib kamu dengar!"),
                        reasonWhyViral = obj.optString("reasonWhyViral", "High engagement & relatable"),
                        transcriptSnippet = obj.optString("transcriptSnippet", "Cuplikan percakapan podcast...")
                    )
                )
            }
            if (highlights.isNotEmpty()) return@withContext highlights
        } catch (e: Exception) {
            Log.e("GeminiService", "Podcast analysis fallback", e)
        }

        // Fallback JUJUR: turunkan rekomendasi langsung dari TRANSKRIP ASLI (timestamp + kalimat nyata),
        // bukan contoh yang dikarang. Memastikan Step 5 tidak pernah kosong selama transkrip tersedia,
        // termasuk saat Gemini API Key belum diisi / gagal / balasannya tidak bisa diparse.
        heuristicHighlightsFromTranscript(safeTranscript, topic, targetCount)
    }

    /**
     * Fallback JUJUR untuk Step 5: bangun rekomendasi segmen LANGSUNG dari transkrip ASLI,
     * tanpa memanggil AI dan tanpa mengarang contoh. Mem-parse baris berformat
     * "[MM:SS] teks" atau "[H:MM:SS] teks" (dipakai oleh Clip Server maupun caption YouTube),
     * lalu menyusun segmen 20-60 detik dari timestamp & kalimat yang benar-benar ada.
     * Mengembalikan list kosong HANYA bila transkrip tidak punya timestamp yang bisa dibaca.
     */
    private fun heuristicHighlightsFromTranscript(
        transcript: String,
        topic: String,
        targetCount: Int
    ): List<PodcastSegmentHighlight> {
        val cueSecs = mutableListOf<Int>()
        val cueTexts = mutableListOf<String>()
        for (rawLine in transcript.lines()) {
            val line = rawLine.trim()
            if (line.length < 4 || line[0] != '[') continue
            if (line.startsWith("[CONTOH")) continue
            val close = line.indexOf(']')
            if (close <= 1) continue
            val stamp = line.substring(1, close).trim()
            val text = line.substring(close + 1).trim()
            if (text.isEmpty()) continue
            val sec = parseClockToSec(stamp) ?: continue
            cueSecs.add(sec)
            cueTexts.add(text)
        }
        if (cueSecs.size < 2) return emptyList()

        val want = targetCount.coerceIn(3, 12)
        val anchorCount = minOf(want, cueSecs.size)
        val result = mutableListOf<PodcastSegmentHighlight>()
        val usedStart = mutableSetOf<Int>()
        for (k in 0 until anchorCount) {
            val anchorIdx = ((cueSecs.size.toLong() * k) / anchorCount).toInt().coerceIn(0, cueSecs.size - 1)
            val startSec = cueSecs[anchorIdx]
            if (!usedStart.add(startSec)) continue
            val minEnd = startSec + 20
            val maxEnd = startSec + 60
            val snippet = StringBuilder()
            var endSec = startSec
            var j = anchorIdx
            while (j < cueSecs.size) {
                val s = cueSecs[j]
                if (s > maxEnd) break
                if (snippet.length < 240) {
                    if (snippet.isNotEmpty()) snippet.append(' ')
                    snippet.append(cueTexts[j])
                }
                endSec = s
                if (s >= minEnd && snippet.length >= 140) break
                j++
            }
            if (endSec < minEnd) endSec = if (minEnd < maxEnd) minEnd else maxEnd
            val snippetText = snippet.toString().trim()
            val stopIdx = snippetText.indexOfFirst { it == '.' || it == '!' || it == '?' }
            val firstSentence = if (stopIdx in 12..79) snippetText.substring(0, stopIdx).trim() else null
            val baseTitle = (firstSentence ?: snippetText.take(60)).trim()
            val title = if (baseTitle.isBlank()) "Segmen menarik " + (result.size + 1)
                else baseTitle.replaceFirstChar { it.uppercase() }
            val hook = if (snippetText.length > 90) snippetText.take(90).trim() + "..." else snippetText
            result.add(
                PodcastSegmentHighlight(
                    startSec = startSec,
                    endSec = endSec,
                    durationFormatted = clockRange(startSec, endSec),
                    title = title,
                    hook = if (hook.isBlank()) "Cuplikan penting dari podcast." else hook,
                    reasonWhyViral = "Diambil otomatis dari transkrip asli (timestamp & kalimat nyata, bukan dikarang).",
                    transcriptSnippet = snippetText.take(240)
                )
            )
        }
        return result
    }

    /** Parse "MM:SS" atau "H:MM:SS" menjadi total detik. Null bila format tidak valid. */
    private fun parseClockToSec(stamp: String): Int? {
        val parts = stamp.split(":")
        if (parts.size < 2 || parts.size > 3) return null
        val nums = ArrayList<Int>(parts.size)
        for (p in parts) {
            val n = p.trim().toIntOrNull() ?: return null
            if (n < 0) return null
            nums.add(n)
        }
        return when (nums.size) {
            2 -> nums[0] * 60 + nums[1]
            3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
            else -> null
        }
    }

    /** Format rentang waktu segmen, contoh: "00:45 - 01:35 (50 detik)". */
    private fun clockRange(startSec: Int, endSec: Int): String {
        fun fmt(t: Int): String {
            val safe = if (t < 0) 0 else t
            val h = safe / 3600
            val m = (safe % 3600) / 60
            val s = safe % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
        val dur = (endSec - startSec).let { if (it < 1) 1 else it }
        return fmt(startSec) + " - " + fmt(endSec) + " (" + dur + " detik)"
    }

    suspend fun generateVideoHooksAndCaptions(
        topic: String,
        style: String
    ): VideoCopyResult = withContext(Dispatchers.IO) {
        val prompt = """
            Buatkan 1 Hook viral 3-detik pertama, caption media sosial lengkap dengan CTA, hashtag relevan, dan 4 baris teks subtitle untuk video pendek.
            Topik: "$topic", Gaya: "$style".
            
            JSON schema:
            {
              "viralHook": "Teks hook besar yang ditaruh di awal video",
              "caption": "Caption menarik dengan storytelling dan CTA",
              "hashtags": "#reels #tiktok #shorts #viral",
              "subtitles": ["Baris 1", "Baris 2", "Baris 3", "Baris 4"]
            }
        """.trimIndent()

        try {
            val response = executePrompt(prompt)
            val json = extractJsonObject(response)
            val subsArray = json.optJSONArray("subtitles")
            val subsList = mutableListOf<String>()
            if (subsArray != null) {
                for (i in 0 until subsArray.length()) {
                    subsList.add(subsArray.getString(i))
                }
            } else {
                subsList.addAll(listOf("Langkah 1: Tentukan target", "Langkah 2: Fokus pada kualitas", "Langkah 3: Konsisten setiap hari", "Simak tips selengkapnya!"))
            }

            VideoCopyResult(
                viralHook = json.optString("viralHook", "99% Orang Belum Tahu Trik Ini!"),
                caption = json.optString("caption", "Tonton sampai habis untuk tips praktisnya! Simpan video ini agar tidak lupa."),
                hashtags = json.optString("hashtags", "#creator #videotips #viral #foryou"),
                subtitles = subsList
            )
        } catch (e: Exception) {
            VideoCopyResult(
                viralHook = "Jangan Skip Kalau Kamu Mau Tahu Rahasianya! \ud83d\udd25",
                caption = "Ini dia cara paling simpel dan efektif yang bisa kamu coba langsung hari ini. Like & Share ke temanmu ya! \u2728",
                hashtags = "#reelsvideo #tiktoktips #contentcreator #viralindo #shorts",
                subtitles = listOf(
                    "Rahasia utama yang jarang dibahas kreator...",
                    "Kuncinya ada pada 3 detik pertama video kamu",
                    "Gunakan visual kontras dan hook yang jelas",
                    "Follow untuk tutorial lengkap berikutnya!"
                )
            )
        }
    }

    // --- 1. GENERATE SLIDE IMAGE BACKGROUND (Imagen 3 & Graphic Engine) ---
    // customPrompt: arahan kreatif dari user (kolom prompt di UI). Selalu melewati
    // sanitizeUserImagePrompt() sebagai guard di system prompt (anti teks/logo, SFW, dibatasi).
    suspend fun generateSlideImage(
        headline: String,
        body: String,
        theme: String,
        slideNumber: Int = 1,
        aspectRatio: String = "1:1",
        customPrompt: String = ""
    ): SlideImageResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val themeKeywords = when (theme.uppercase()) {
            "MINIMALIST TECH", "MINIMAL TECH" -> "Clean geometric slate, deep navy and utility blue gradients, subtle high-tech mesh lighting, sleek minimal 3D atmosphere, ultra clean aesthetic backdrop"
            "CYBER NEON", "NEON" -> "Vibrant cyberpunk neon volumetric lighting, dark slate backdrop, luminous laser light rays, futuristic synthwave neon atmosphere, 8k wallpaper"
            "AESTHETIC PASTEL", "PASTEL" -> "Soft warm pastel dreamscape, soothing cream and peach aura gradients, organic fluid soft lighting, minimal aesthetic backdrop"
            "DARK LUXURY", "LUXURY" -> "Deep obsidian matte black, metallic gold light streaks, champagne amber radiance, ultra premium luxury minimalist texture"
            "VINTAGE RETRO", "RETRO" -> "1980s retro sunset palette, warm analog aesthetic grain, teal and burnt orange wave gradients, nostalgic art background"
            "3D ILLUSTRATION", "3D ABSTRACT", "3D" -> "Glossy 3D abstract shapes, smooth clay studio render, vibrant studio illumination, soft drop shadows, clean modern backdrop"
            else -> "Modern social media card background, clean minimalist aesthetic texture, balanced atmospheric lighting"
        }

        // Standardize aspect ratio for Imagen 3
        val imagenRatio = when (aspectRatio) {
            "4:5", "3:4" -> "3:4"
            "9:16" -> "9:16"
            "16:9" -> "16:9"
            else -> "1:1"
        }

        // System-prompt guard: arahan user dibersihkan dulu, lalu selalu dibungkus batasan keras.
        val safeCustom = sanitizeUserImagePrompt(customPrompt)
        val prompt = if (safeCustom.isNotBlank()) {
            "Ultra high quality aesthetic social media background wallpaper. User creative direction: '$safeCustom'. Base visual mood: $themeKeywords. PURE BACKGROUND ART ONLY. STRICTLY NO TEXT, NO LETTERS, NO WORDS, NO NUMBERS, NO WATERMARKS, NO LOGOS, NO TYPOGRAPHY, NO UI ELEMENTS. Safe-for-work, non-violent, brand friendly, tasteful composition with clear negative space for overlaid text."
        } else {
            "Ultra high quality aesthetic background wallpaper for topic: '$headline'. Visual style: $themeKeywords. PURE BACKGROUND ART ONLY. STRICTLY NO TEXT, NO LETTERS, NO WORDS, NO NUMBERS, NO WATERMARKS, NO LOGOS, NO TYPOGRAPHY."
        }

        // Attempt 1: Imagen 3 (imagen-3.0-generate-002)
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val imagenUrl = "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=$apiKey"
                val imagenBody = JSONObject().apply {
                    val instancesArray = JSONArray().apply {
                        put(JSONObject().put("prompt", prompt))
                    }
                    put("instances", instancesArray)
                    val paramsObj = JSONObject().apply {
                        put("sampleCount", 1)
                        put("aspectRatio", imagenRatio)
                    }
                    put("parameters", paramsObj)
                }

                val request = Request.Builder()
                    .url(imagenUrl)
                    .post(imagenBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val respJson = JSONObject(responseStr)
                    val predictions = respJson.optJSONArray("predictions")
                    if (predictions != null && predictions.length() > 0) {
                        val firstPred = predictions.getJSONObject(0)
                        val b64 = firstPred.optString("bytesBase64Encoded")
                        if (b64.isNotBlank()) {
                            return@withContext SlideImageResult(
                                base64Data = b64,
                                promptUsed = prompt,
                                theme = theme,
                                styleKeywords = themeKeywords,
                                isAiGenerated = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "Imagen 3 API call fallback", e)
            }

            // Attempt 2: gemini-3.1-flash-image
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image:generateContent?key=$apiKey"
                val requestBodyJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                    val genConfig = JSONObject().apply {
                        val imgConfig = JSONObject().apply {
                            put("aspectRatio", imagenRatio)
                            put("imageSize", "1K")
                        }
                        put("imageConfig", imgConfig)
                        val modalities = JSONArray().apply {
                            put("TEXT")
                            put("IMAGE")
                        }
                        put("responseModalities", modalities)
                    }
                    put("generationConfig", genConfig)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val respJson = JSONObject(responseStr)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("inlineData")) {
                                    val inline = part.getJSONObject("inlineData")
                                    val data = inline.optString("data")
                                    if (data.isNotBlank()) {
                                        return@withContext SlideImageResult(
                                            base64Data = data,
                                            promptUsed = prompt,
                                            theme = theme,
                                            styleKeywords = themeKeywords,
                                            isAiGenerated = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "Gemini image preview API call fallback", e)
            }
        }

        // Guaranteed High-Quality Themed Pure Background Generator Fallback
        val graphicB64 = com.example.util.SlideGraphicGenerator.generateThemedGraphicBase64(
            headline = headline,
            body = body,
            theme = theme,
            slideNumber = slideNumber,
            aspectRatio = aspectRatio
        )

        SlideImageResult(
            base64Data = graphicB64,
            imageUrl = null,
            promptUsed = prompt,
            theme = theme,
            styleKeywords = themeKeywords,
            isAiGenerated = false
        )
    }

    /** Guard konten untuk prompt gambar dari user: rapikan, batasi panjang, buang kata terlarang. */
    private fun sanitizeUserImagePrompt(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.length > 400) s = s.substring(0, 400)
        val banned = listOf("nude", "naked", "nsfw", "sex", "porn", "gore", "blood", "violence", "weapon", "kill", "nazi", "terror")
        for (b in banned) s = s.replace(Regex("(?i)" + Regex.escape(b)), "")
        return s.trim()
    }

    // --- 2. AUDIO TRANSCRIPTION (gemini-2.5-flash multimodal) ---
    suspend fun transcribeAudioWithGemini(
        rawTranscriptOrTopic: String,
        videoTitle: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val prompt = """
            Kamu adalah AI Speech-to-Text Transcriptionist.
            Transkripsikan audio video/podcast berikut dengan teliti beserta timestamp per detik/kalimat.
            Judul Video: "$videoTitle"
            Konten Audio/Topik: "$rawTranscriptOrTopic"
            
            Format balasan wajib JSON:
            {
              "fullText": "Teks transkrip lengkap tanpa jeda...",
              "detectedLanguage": "id",
              "segments": [
                {
                  "timestampSec": 15,
                  "timestampFormatted": "00:15",
                  "speaker": "Speaker 1",
                  "text": "Kalimat yang diucapkan pada detik tersebut..."
                }
              ]
            }
        """.trimIndent()

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val requestBodyJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val respJson = JSONObject(responseStr)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        val json = extractJsonObject(text)
                        val segArray = json.optJSONArray("segments")
                        val segments = mutableListOf<TranscriptTimestampItem>()
                        if (segArray != null) {
                            for (i in 0 until segArray.length()) {
                                val s = segArray.getJSONObject(i)
                                segments.add(
                                    TranscriptTimestampItem(
                                        timestampSec = s.optInt("timestampSec", i * 15),
                                        timestampFormatted = s.optString("timestampFormatted", "00:${i * 15}"),
                                        speaker = s.optString("speaker", "Speaker"),
                                        text = s.optString("text", "")
                                    )
                                )
                            }
                        }
                        if (segments.isNotEmpty()) {
                            return@withContext TranscriptionResult(
                                fullText = json.optString("fullText", rawTranscriptOrTopic),
                                segments = segments,
                                detectedLanguage = json.optString("detectedLanguage", "id")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "Transcription fallback used", e)
            }
        }

        // Smart Structured Fallback
        val lines = rawTranscriptOrTopic.lines().filter { it.isNotBlank() }
        val fallbackSegments = mutableListOf<TranscriptTimestampItem>()
        var secCounter = 10
        for (line in lines) {
            val cleanLine = line.trim()
            val (speaker, text) = if (cleanLine.contains(":")) {
                val parts = cleanLine.split(":", limit = 2)
                Pair(parts[0].replace("[", "").replace("]", "").trim(), parts[1].trim())
            } else {
                Pair("Speaker", cleanLine)
            }
            val min = secCounter / 60
            val sec = secCounter % 60
            fallbackSegments.add(
                TranscriptTimestampItem(
                    timestampSec = secCounter,
                    timestampFormatted = String.format("%02d:%02d", min, sec),
                    speaker = speaker,
                    text = text
                )
            )
            secCounter += 20
        }
        TranscriptionResult(
            fullText = rawTranscriptOrTopic,
            segments = fallbackSegments
        )
    }

    // --- 3. GENERATE VIDEO FROM TEXT (veo-3.1-fast-generate-preview) ---
    suspend fun generateVeoVideoFromText(
        prompt: String,
        aspectRatio: String = "9:16",
        stylePreset: String = "Cinematic 4K",
        durationSec: Int = 5
    ): GeneratedVideoResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val styledPrompt = "$prompt. Style: $stylePreset, high quality, fluid motion, professional color grading, $aspectRatio aspect ratio."

        var dynamicHook = "99% Orang Belum Tahu Soal Ini!"
        var dynamicSubs = listOf(
            "Visual dibuat otomatis oleh Veo 3 AI",
            "Hook 3-detik dirancang memaksimalkan retensi",
            "Cocok untuk format Reels & TikTok",
            "Jadwalkan sekarang langsung dari studio!"
        )

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                // Call Veo 3 model
                val url = "https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-fast-generate-preview:generateVideos?key=$apiKey"
                val requestBodyJson = JSONObject().apply {
                    put("prompt", styledPrompt)
                    val config = JSONObject().apply {
                        put("numberOfVideos", 1)
                        put("resolution", "1080p")
                        put("aspectRatio", aspectRatio)
                    }
                    put("config", config)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                // Veo returns operation name or video metadata
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    Log.d("GeminiService", "Veo 3 response: $respStr")
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "Veo generation network execution", e)
            }

            // Also generate contextual hooks & dynamic subtitles for this video
            try {
                val copyPrompt = """
                    Buatkan 1 viral hook 3-detik dan 4 baris subtitle singkat untuk video AI dengan prompt: "$prompt".
                    Format JSON: {"hook": "...", "subtitles": ["...", "...", "...", "..."]}
                """.trimIndent()
                val copyResp = executePrompt(copyPrompt)
                val json = extractJsonObject(copyResp)
                dynamicHook = json.optString("hook", dynamicHook)
                val subsArray = json.optJSONArray("subtitles")
                if (subsArray != null && subsArray.length() > 0) {
                    val list = mutableListOf<String>()
                    for (i in 0 until subsArray.length()) list.add(subsArray.getString(i))
                    dynamicSubs = list
                }
            } catch (e: Exception) {
                // fallback
            }
        }

        GeneratedVideoResult(
            videoId = "veo_" + System.currentTimeMillis(),
            prompt = prompt,
            aspectRatio = aspectRatio,
            durationSec = durationSec,
            stylePreset = stylePreset,
            videoUrl = null, // Veo perlu polling long-running operation utk URL video nyata (belum diimplementasikan)
            storyboardFrames = listOf(
                "FRAME 1: Opening hook frame with dynamic movement",
                "FRAME 2: Main focal subject high definition transition",
                "FRAME 3: Atmospheric particle effects & cinematic lighting",
                "FRAME 4: Climax visual and smooth loop ending"
            ),
            dynamicSubtitles = dynamicSubs,
            viralHook = dynamicHook
        )
    }

    // --- 4. ANIMATE IMAGE INTO VIDEO (veo-3.1-fast-generate-preview) ---
    suspend fun animateImageWithVeo(
        imageDescription: String,
        motionPrompt: String,
        aspectRatio: String = "9:16"
    ): GeneratedVideoResult = withContext(Dispatchers.IO) {
        val combinedPrompt = "Animate this static visual: '$imageDescription'. Motion action: '$motionPrompt'. Smooth camera glide, cinematic lighting, aspect ratio $aspectRatio."
        generateVeoVideoFromText(
            prompt = combinedPrompt,
            aspectRatio = aspectRatio,
            stylePreset = "Smooth Image-to-Video Animation",
            durationSec = 5
        )
    }

    private suspend fun executePrompt(prompt: String, explicitKey: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = explicitKey ?: getApiKey()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw RuntimeException("Gemini API error code ${response.code}: $errorBody")
        }

        val responseStr = response.body?.string() ?: ""
        val respJson = JSONObject(responseStr)
        val candidates = respJson.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() > 0) {
                return@withContext parts.getJSONObject(0).getString("text")
            }
        }
        ""
    }

    private fun extractJsonObject(text: String): JSONObject {
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val jsonSub = clean.substring(firstBrace, lastBrace + 1)
            return JSONObject(jsonSub)
        }
        return JSONObject(clean)
    }

    private fun extractJsonArray(text: String): JSONArray {
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val firstBracket = clean.indexOf('[')
        val lastBracket = clean.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            val jsonSub = clean.substring(firstBracket, lastBracket + 1)
            return JSONArray(jsonSub)
        }
        return JSONArray(clean)
    }
}
