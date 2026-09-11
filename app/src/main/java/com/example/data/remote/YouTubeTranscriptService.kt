package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationFormatted: String,
    val transcriptText: String
)

class YouTubeTranscriptService {

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

    suspend fun fetchVideoInfoAndTranscript(urlOrTopic: String): YouTubeVideoInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrTopic)

        // Preset rich podcast database or custom generated transcript
        if (urlOrTopic.contains("deddy", ignoreCase = true) || urlOrTopic.contains("corbuzier", ignoreCase = true)) {
            return@withContext YouTubeVideoInfo(
                videoId = videoId,
                title = "RAHASIA BISNIS 100 MILIAR TANPA MODAL INVESTOR! - Close The Door",
                channelName = "Deddy Corbuzier",
                durationFormatted = "48:20",
                transcriptText = """
                    [00:15] Deddy: Lu mulai dari umur berapa sebenarnya pas pertama kali jualan?
                    [00:30] Guest: Umur 19 tahun Om Ded. Waktu itu gue gak punya uang sepeserpun, modal cuma HP second.
                    [00:45] Deddy: Banyak anak muda sekarang alibi gak ada modal, gak ada privilege.
                    [01:05] Guest: Betul, padahal yang paling mahal itu bukan modal uang, tapi 'speed of execution' dan 'reputasi'. Kalau lu bisa dipercaya, modal bakal nyari lu sendiri.
                    [02:15] Deddy: Setuju banget. Kenapa orang susah dapet kepercayaan? Karena mereka pengen instan.
                    [02:40] Guest: Dalam 3 tahun pertama, gue gak ngambil gaji. Semua profit gue putar balik buat ningkatin sistem dan tim.
                    [03:50] Deddy: Ini poin paling krusial yang 99% orang lupakan. Mereka baru dapet untung 10 juta langsung beli lifestyle.
                    [04:40] Guest: Fatal banget itu. Kunci compounding itu menahan kenikmatan sesaat demi financial freedom jangka panjang.
                """.trimIndent()
            )
        } else if (urlOrTopic.contains("hormozi", ignoreCase = true) || urlOrTopic.contains("business", ignoreCase = true)) {
            return@withContext YouTubeVideoInfo(
                videoId = videoId,
                title = "How To Get Rich In Your 20s (The Real Blueprint)",
                channelName = "Alex Hormozi",
                durationFormatted = "32:10",
                transcriptText = """
                    [00:20] Alex: If you want to make a million dollars, you have to acquire skills that are worth a million dollars.
                    [00:50] Alex: Most people spend 4 years in college and wonder why nobody pays them high income. College teaches you theory, the market pays for outcome.
                    [01:30] Alex: Step 1: Learn one high-income skill like copywriting, media buying, or closing sales.
                    [02:10] Alex: Step 2: Work for free if you must to get 10 undeniable case studies.
                    [03:15] Alex: Step 3: Productize your service and raise your prices because demand exceeds your capacity.
                    [04:20] Alex: Don't diversify when you have nothing. Focus on one offer, one avatar, one channel until you hit 7 figures.
                """.trimIndent()
            )
        } else {
            val titleTopic = if (urlOrTopic.startsWith("http")) "Masterclass Strategi Konten Viral 2026" else urlOrTopic.replaceFirstChar { it.uppercase() }
            return@withContext YouTubeVideoInfo(
                videoId = videoId,
                title = "$titleTopic (Eksklusif Podcast)",
                channelName = "The Creators Podcast ID",
                durationFormatted = "25:40",
                transcriptText = """
                    [00:10] Host: Kenapa algoritma media sosial sekarang lebih suka short-form content daripada video panjang biasa?
                    [00:35] Speaker: Karena rentang perhatian audiens turun drastis ke 3 detik. Kalau hook lu gagal, seluruh isi videomu sia-sia.
                    [01:10] Host: Apa formula hook terbaik yang terbukti konsisten tembus 1 juta views?
                    [01:40] Speaker: Formula 3E: Empathy (pahami keresahan mereka), Element of Surprise (hal yang belum pernah mereka dengar), dan Easy Solution (janji solusi cepat).
                    [02:30] Host: Banyak kreator yang rajin upload tiap hari tapi views tetap 200, kenapa tuh?
                    [02:55] Speaker: Karena mereka fokus di kuantitas tanpa retensi. Algoritma gak peduli lu upload 100 video kalau penonton selalu swipe away di detik kelima.
                    [04:10] Speaker: Trik rahasianya adalah ganti visual atau teks tiap 2.5 detik agar mata penonton gak bosan!
                """.trimIndent()
            )
        }
    }
}
