package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ContentFormat {
    CAROUSEL,
    PODCAST_CLIP,
    SELF_VIDEO,
    AI_VIDEO,
    REMAKE
}

enum class SocialPlatform {
    TIKTOK,
    INSTAGRAM,
    YOUTUBE_SHORTS,
    FACEBOOK_REELS
}

enum class PostStatus {
    DRAFT,
    SCHEDULED,
    POSTING,
    POSTED,
    FAILED
}

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val brandName: String,
    val niche: String,
    val targetAudience: String,
    val languageStyle: String, // Santai, Formal, Lucu/Humoris, Edukatif, Inspiratif
    val tone: String, // Energetic, Empathetic, Provocative, Authoritative
    val language: String = "ID", // ID / EN
    val accountReferences: String = "", // e.g. @garyvee, @cleo, @feliciaputri
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "content_plans")
data class ContentPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personaId: Long,
    val theme: String,
    val durationDays: Int = 7, // 7 or 30
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "content_plan_items")
data class ContentPlanItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val dayNumber: Int,
    val scheduledDateMillis: Long,
    val title: String,
    val format: ContentFormat,
    val hook: String,
    val captionDraft: String,
    val hashtags: String,
    val isSentToStudio: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class CarouselSlide(
    val slideNumber: Int,
    val headline: String,
    val body: String,
    val subtext: String = "",
    val imageUrl: String? = null,
    val imageBase64: String? = null,
    val imagePrompt: String? = null,
    val themeName: String? = null
)

@Entity(tableName = "scheduled_posts")
data class ScheduledPostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planItemId: Long? = null,
    val personaId: Long? = null,
    val title: String,
    val format: ContentFormat,
    val hook: String,
    val caption: String,
    val hashtags: String,
    val targetPlatforms: List<SocialPlatform>, // e.g. [TIKTOK, INSTAGRAM, YOUTUBE_SHORTS]
    val scheduledTimeMillis: Long,
    val status: PostStatus = PostStatus.SCHEDULED,
    val mediaUri: String? = null, // video URI or thumbnail
    val carouselSlidesJson: String? = null, // Serialized List<CarouselSlide>
    val podcastVideoUrl: String? = null,
    val podcastSegmentStartSec: Int = 0,
    val podcastSegmentEndSec: Int = 60,
    val podcastChannelName: String = "",
    val subtitlesJson: String? = null, // Serialized list of subtitle lines
    val stylingJson: String? = null, // Font, colors, watermark configs
    val generatedVideoUrl: String? = null,
    val videoAspectRatio: String = "9:16",
    val lastErrorMessage: String? = null,
    val retryCount: Int = 0,
    val postedTimeMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "posting_logs")
data class PostingLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: Long,
    val postTitle: String,
    val platform: SocialPlatform,
    val isSuccess: Boolean,
    val message: String,
    val responseData: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)
