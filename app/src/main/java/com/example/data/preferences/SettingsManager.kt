package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.local.entity.CarouselDesign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val geminiApiKey: String = "",
    val tikTokApiKey: String = "",
    val metaAccessToken: String = "",
    val youTubeApiKey: String = "",
    val isTikTokConnected: Boolean = false,
    val isInstagramConnected: Boolean = false,
    val isYouTubeConnected: Boolean = false,
    val isFacebookConnected: Boolean = false,
    val tikTokAccountName: String = "@creator_tiktok",
    val instagramAccountName: String = "@creator_reels",
    val youTubeAccountName: String = "Creator Shorts Official",
    val defaultPostHour: Int = 19,
    val defaultPostMinute: Int = 0,
    val timezone: String = "Asia/Jakarta (GMT+7)",
    val postingFrequency: String = "DAILY", // DAILY, WEEKDAYS
    val themeMode: String = "DARK", // DARK, LIGHT, SYSTEM
    val appLanguage: String = "ID", // ID, EN
    val autoRetryOnError: Boolean = true,
    val carouselDesignJson: String = "", // Desain carousel favorit/default (JSON)
    val uploadedBackgroundsJson: String = "", // Galeri background yang pernah di-upload (JSON array base64)
    val savedStylesJson: String = "", // Gaya carousel tersimpan bernama (JSON array {name, design})
    val openverseClientId: String = "", // Openverse OAuth client_id (opsional, untuk cari gambar internet)
    val openverseClientSecret: String = "", // Openverse OAuth client_secret (opsional)
    val geminiImageModel: String = "", // Model gambar Gemini pilihan user (kosong = otomatis)
    val defaultClipAspectRatio: String = "9:16", // Rasio default hasil potong podcast: "9:16" atau "16:9"
    val autoPickBestPodcast: Boolean = true, // Jika true, mesin otomatis memilih video paling relevan
    val clipServerUrl: String = "", // URL OtoPost Clip Server (transkrip asli + download HD + potong + lipsync). Kosong = mode on-device.
    val clipServerToken: String = "", // Token opsional untuk Clip Server (Authorization: Bearer)
    val elevenLabsApiKey: String = "", // API key ElevenLabs untuk Text-to-Speech (fitur Remake Suara / Lipsync)
    val elevenLabsVoiceId: String = "", // Voice ID ElevenLabs pilihan user (kosong = suara default multilingual)
    val remakeMediaJson: String = "" // Galeri media (video/foto) untuk remake/lipsync (JSON array)
)

/** Satu gaya carousel tersimpan yang bisa diberi nama & dipakai ulang. */
data class SavedCarouselStyle(
    val name: String,
    val design: CarouselDesign
)

/** Satu media (video/foto) di galeri remake yang bisa dipakai ulang & ditetapkan sebagai default. */
data class RemakeMediaItem(
    val uri: String,
    val kind: String,        // "video" | "photo"
    val name: String = "",
    val isDefault: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("autopost_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val geminiKey = prefs.getString("gemini_api_key", "")?.ifBlank {
            try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        } ?: ""

        val elevenKey = prefs.getString("elevenlabs_api_key", "")?.ifBlank {
            try {
                BuildConfig.ELEVENLABS_API_KEY
            } catch (e: Exception) {
                ""
            }
        } ?: ""

        return AppSettings(
            geminiApiKey = geminiKey,
            tikTokApiKey = prefs.getString("tiktok_api_key", "") ?: "",
            metaAccessToken = prefs.getString("meta_access_token", "") ?: "",
            youTubeApiKey = prefs.getString("youtube_api_key", "") ?: "",
            isTikTokConnected = prefs.getBoolean("tiktok_connected", true), // Default connected in simulation
            isInstagramConnected = prefs.getBoolean("instagram_connected", true),
            isYouTubeConnected = prefs.getBoolean("youtube_connected", true),
            isFacebookConnected = prefs.getBoolean("facebook_connected", false),
            tikTokAccountName = prefs.getString("tiktok_account_name", "@creator_tiktok") ?: "@creator_tiktok",
            instagramAccountName = prefs.getString("instagram_account_name", "@creator_reels") ?: "@creator_reels",
            youTubeAccountName = prefs.getString("youtube_account_name", "Creator Shorts Official") ?: "Creator Shorts Official",
            defaultPostHour = prefs.getInt("default_post_hour", 19),
            defaultPostMinute = prefs.getInt("default_post_minute", 0),
            timezone = prefs.getString("timezone", "Asia/Jakarta (GMT+7)") ?: "Asia/Jakarta (GMT+7)",
            postingFrequency = prefs.getString("posting_frequency", "DAILY") ?: "DAILY",
            themeMode = prefs.getString("theme_mode", "DARK") ?: "DARK",
            appLanguage = prefs.getString("app_language", "ID") ?: "ID",
            autoRetryOnError = prefs.getBoolean("auto_retry", true),
            carouselDesignJson = prefs.getString("carousel_design_json", "") ?: "",
            uploadedBackgroundsJson = prefs.getString("uploaded_backgrounds_json", "") ?: "",
            savedStylesJson = prefs.getString("saved_styles_json", "") ?: "",
            openverseClientId = prefs.getString("openverse_client_id", "") ?: "",
            openverseClientSecret = prefs.getString("openverse_client_secret", "") ?: "",
            geminiImageModel = prefs.getString("gemini_image_model", "") ?: "",
            defaultClipAspectRatio = prefs.getString("default_clip_aspect_ratio", "9:16") ?: "9:16",
            autoPickBestPodcast = prefs.getBoolean("auto_pick_best_podcast", true),
            clipServerUrl = prefs.getString("clip_server_url", "") ?: "",
            clipServerToken = prefs.getString("clip_server_token", "") ?: "",
            elevenLabsApiKey = elevenKey,
            elevenLabsVoiceId = prefs.getString("elevenlabs_voice_id", "") ?: "",
            remakeMediaJson = prefs.getString("remake_media_json", "") ?: ""
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("gemini_api_key", newSettings.geminiApiKey)
            putString("tiktok_api_key", newSettings.tikTokApiKey)
            putString("meta_access_token", newSettings.metaAccessToken)
            putString("youtube_api_key", newSettings.youTubeApiKey)
            putBoolean("tiktok_connected", newSettings.isTikTokConnected)
            putBoolean("instagram_connected", newSettings.isInstagramConnected)
            putBoolean("youtube_connected", newSettings.isYouTubeConnected)
            putBoolean("facebook_connected", newSettings.isFacebookConnected)
            putString("tiktok_account_name", newSettings.tikTokAccountName)
            putString("instagram_account_name", newSettings.instagramAccountName)
            putString("youtube_account_name", newSettings.youTubeAccountName)
            putInt("default_post_hour", newSettings.defaultPostHour)
            putInt("default_post_minute", newSettings.defaultPostMinute)
            putString("timezone", newSettings.timezone)
            putString("posting_frequency", newSettings.postingFrequency)
            putString("theme_mode", newSettings.themeMode)
            putString("app_language", newSettings.appLanguage)
            putBoolean("auto_retry", newSettings.autoRetryOnError)
            putString("carousel_design_json", newSettings.carouselDesignJson)
            putString("uploaded_backgrounds_json", newSettings.uploadedBackgroundsJson)
            putString("saved_styles_json", newSettings.savedStylesJson)
            putString("openverse_client_id", newSettings.openverseClientId)
            putString("openverse_client_secret", newSettings.openverseClientSecret)
            putString("gemini_image_model", newSettings.geminiImageModel)
            putString("default_clip_aspect_ratio", newSettings.defaultClipAspectRatio)
            putBoolean("auto_pick_best_podcast", newSettings.autoPickBestPodcast)
            putString("clip_server_url", newSettings.clipServerUrl)
            putString("clip_server_token", newSettings.clipServerToken)
            putString("elevenlabs_api_key", newSettings.elevenLabsApiKey)
            putString("elevenlabs_voice_id", newSettings.elevenLabsVoiceId)
            putString("remake_media_json", newSettings.remakeMediaJson)
            apply()
        }
        _settings.value = newSettings
    }

    fun getEffectiveGeminiKey(): String {
        val customKey = _settings.value.geminiApiKey
        if (customKey.isNotBlank()) return customKey
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /** API key ElevenLabs efektif (Settings dulu, lalu fallback BuildConfig dari .env). */
    fun getEffectiveElevenLabsKey(): String {
        val customKey = _settings.value.elevenLabsApiKey
        if (customKey.isNotBlank()) return customKey
        return try {
            BuildConfig.ELEVENLABS_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /** Voice ID ElevenLabs pilihan user (kosong = service memakai suara default multilingual). */
    fun getEffectiveElevenLabsVoiceId(): String = _settings.value.elevenLabsVoiceId.trim()

    /** Model gambar Gemini pilihan user (kosong = biarkan service memakai urutan default). */
    fun getEffectiveGeminiImageModel(): String = _settings.value.geminiImageModel.trim()

    /** Menyimpan rasio klip podcast default ("9:16" atau "16:9"). */
    fun saveDefaultClipAspectRatio(ratio: String) {
        updateSettings(_settings.value.copy(defaultClipAspectRatio = ratio))
    }

    /** Memuat desain carousel favorit/default yang tersimpan (atau default bawaan). */
    fun loadFavoriteCarouselDesign(): CarouselDesign =
        CarouselDesign.fromJsonString(_settings.value.carouselDesignJson)

    /** Menyimpan desain carousel sebagai favorit/default untuk dipakai konten berikutnya. */
    fun saveFavoriteCarouselDesign(design: CarouselDesign) {
        updateSettings(_settings.value.copy(carouselDesignJson = design.toJsonString()))
    }

    // --- Galeri background upload (persisten, bisa dipakai ulang) ---
    fun loadUploadedBackgrounds(): List<String> {
        val json = _settings.value.uploadedBackgroundsJson
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val s = arr.optString(i)
                if (s.isNullOrBlank()) null else s
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveUploadedBackgrounds(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        updateSettings(_settings.value.copy(uploadedBackgroundsJson = arr.toString()))
    }

    // --- Galeri media remake/lipsync (video/foto, bisa dipakai ulang & ada default) ---
    fun loadRemakeMedia(): List<RemakeMediaItem> {
        val json = _settings.value.remakeMediaJson
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri")
                if (uri.isNullOrBlank()) return@mapNotNull null
                RemakeMediaItem(
                    uri = uri,
                    kind = o.optString("kind", "video").ifBlank { "video" },
                    name = o.optString("name", ""),
                    isDefault = o.optBoolean("isDefault", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRemakeMedia(list: List<RemakeMediaItem>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("uri", m.uri)
                put("kind", m.kind)
                put("name", m.name)
                put("isDefault", m.isDefault)
            })
        }
        updateSettings(_settings.value.copy(remakeMediaJson = arr.toString()))
    }

    /** Tambahkan satu media ke galeri remake (menghindari duplikat uri). */
    fun addRemakeMedia(item: RemakeMediaItem) {
        val current = loadRemakeMedia().filter { it.uri != item.uri }
        saveRemakeMedia(current + item)
    }

    /** Hapus satu media dari galeri remake berdasarkan uri. */
    fun removeRemakeMedia(uri: String) {
        saveRemakeMedia(loadRemakeMedia().filter { it.uri != uri })
    }

    /** Tetapkan satu media sebagai default (yang lain otomatis non-default). */
    fun setDefaultRemakeMedia(uri: String) {
        val updated = loadRemakeMedia().map { it.copy(isDefault = it.uri == uri) }
        saveRemakeMedia(updated)
    }

    /** Ambil media default (atau item pertama bila belum ada yang ditetapkan). */
    fun getDefaultRemakeMedia(): RemakeMediaItem? {
        val list = loadRemakeMedia()
        return list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }

    // --- Gaya carousel tersimpan bernama ---
    fun loadSavedStyles(): List<SavedCarouselStyle> {
        val json = _settings.value.savedStylesJson
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                if (name.isNullOrBlank()) return@mapNotNull null
                SavedCarouselStyle(name, CarouselDesign.fromJsonString(o.optString("design")))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSavedStyles(list: List<SavedCarouselStyle>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("design", s.design.toJsonString())
            })
        }
        updateSettings(_settings.value.copy(savedStylesJson = arr.toString()))
    }
}
