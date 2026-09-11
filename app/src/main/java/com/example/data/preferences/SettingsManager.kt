package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val autoRetryOnError: Boolean = true
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
            autoRetryOnError = prefs.getBoolean("auto_retry", true)
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
}
