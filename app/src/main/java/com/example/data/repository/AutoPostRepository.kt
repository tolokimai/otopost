package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.preferences.SettingsManager
import com.example.data.remote.AiImageService
import com.example.data.remote.GeminiService
import com.example.data.remote.ImageSearchService
import com.example.data.remote.SocialMediaPublisher
import com.example.data.remote.YouTubeTranscriptService

/**
 * Repository pusat yang menyatukan sumber data lokal (Room), preferensi, dan
 * layanan remote (Gemini teks, generate gambar AI, pencarian gambar internet,
 * publisher sosial media, transkrip YouTube).
 */
class AutoPostRepository(
    context: Context,
    val database: AppDatabase,
    val settingsManager: SettingsManager
) {
    // Layanan teks Gemini (persona, plan, caption, transkrip, dll).
    val geminiService = GeminiService { settingsManager.getEffectiveGeminiKey() }

    // Layanan khusus generate GAMBAR via model image Gemini/Imagen.
    val aiImageService = AiImageService { settingsManager.getEffectiveGeminiKey() }

    // Pencarian gambar dari internet (Openverse, gratis tanpa key).
    val imageSearchService = ImageSearchService()

    // Publisher ke platform sosial media.
    val socialPublisher = SocialMediaPublisher(settingsManager)

    // Layanan transkrip YouTube.
    val youTubeTranscriptService = YouTubeTranscriptService()
}
