package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.ContentFormat
import com.example.data.local.entity.PostStatus
import com.example.data.local.entity.SocialPlatform
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromContentFormat(format: ContentFormat): String = format.name

    @TypeConverter
    fun toContentFormat(value: String): ContentFormat = try {
        ContentFormat.valueOf(value)
    } catch (e: Exception) {
        ContentFormat.CAROUSEL
    }

    @TypeConverter
    fun fromPostStatus(status: PostStatus): String = status.name

    @TypeConverter
    fun toPostStatus(value: String): PostStatus = try {
        PostStatus.valueOf(value)
    } catch (e: Exception) {
        PostStatus.DRAFT
    }

    @TypeConverter
    fun fromSocialPlatformList(platforms: List<SocialPlatform>?): String {
        if (platforms.isNullOrEmpty()) return ""
        return platforms.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toSocialPlatformList(value: String?): List<SocialPlatform> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull {
            try {
                SocialPlatform.valueOf(it.trim())
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromCarouselSlideList(slides: List<CarouselSlide>?): String {
        if (slides.isNullOrEmpty()) return ""
        val array = JSONArray()
        slides.forEach { slide ->
            val obj = JSONObject()
            obj.put("slideNumber", slide.slideNumber)
            obj.put("headline", slide.headline)
            obj.put("body", slide.body)
            obj.put("subtext", slide.subtext)
            obj.put("imageUrl", slide.imageUrl)
            obj.put("imageBase64", slide.imageBase64)
            obj.put("imagePrompt", slide.imagePrompt)
            obj.put("themeName", slide.themeName)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toCarouselSlideList(value: String?): List<CarouselSlide> {
        if (value.isNullOrBlank()) return emptyList()
        val list = mutableListOf<CarouselSlide>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CarouselSlide(
                        slideNumber = obj.optInt("slideNumber", i + 1),
                        headline = obj.optString("headline", ""),
                        body = obj.optString("body", ""),
                        subtext = obj.optString("subtext", ""),
                        imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.optString("imageUrl") else null,
                        imageBase64 = if (obj.has("imageBase64") && !obj.isNull("imageBase64")) obj.optString("imageBase64") else null,
                        imagePrompt = if (obj.has("imagePrompt") && !obj.isNull("imagePrompt")) obj.optString("imagePrompt") else null,
                        themeName = if (obj.has("themeName") && !obj.isNull("themeName")) obj.optString("themeName") else null
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }
}
