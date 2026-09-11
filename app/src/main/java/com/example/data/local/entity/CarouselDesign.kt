package com.example.data.local.entity

import org.json.JSONArray
import org.json.JSONObject

/**
 * Peran sebuah slide dalam sebuah carousel. Posisi elemen (layout) disimpan
 * per-peran sehingga saat mesin/AI meng-generate konten baru, posisi & gaya yang
 * sudah ditetapkan user langsung dipakai ulang (tinggal ganti isi & background).
 */
enum class SlideRole { HOOK, BODY, CTA }

/** Jenis elemen yang bisa diletakkan & digeser di atas slide. */
enum class CarouselElement { HEADLINE, BODY, SUBTEXT, CTA, LOGO, WATERMARK, PAGE_NUMBER }

enum class TextAlignH { START, CENTER, END }

/**
 * Layout & gaya satu elemen. Posisi disimpan sebagai fraksi 0..1 relatif terhadap
 * kanvas sehingga konsisten di semua rasio & resolusi (preview == hasil download).
 */
data class ElementLayout(
    val element: CarouselElement,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float = 0.82f,
    val fontScale: Float = 1f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: TextAlignH = TextAlignH.CENTER,
    val colorHex: String? = null,
    val visible: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("element", element.name)
        put("x", xFraction.toDouble())
        put("y", yFraction.toDouble())
        put("w", widthFraction.toDouble())
        put("scale", fontScale.toDouble())
        put("bold", bold)
        put("italic", italic)
        put("underline", underline)
        put("align", align.name)
        put("color", colorHex ?: JSONObject.NULL)
        put("visible", visible)
    }

    companion object {
        fun fromJson(o: JSONObject): ElementLayout = ElementLayout(
            element = try { CarouselElement.valueOf(o.optString("element", "BODY")) } catch (e: Exception) { CarouselElement.BODY },
            xFraction = o.optDouble("x", 0.5).toFloat(),
            yFraction = o.optDouble("y", 0.5).toFloat(),
            widthFraction = o.optDouble("w", 0.82).toFloat(),
            fontScale = o.optDouble("scale", 1.0).toFloat(),
            bold = o.optBoolean("bold", false),
            italic = o.optBoolean("italic", false),
            underline = o.optBoolean("underline", false),
            align = try { TextAlignH.valueOf(o.optString("align", "CENTER")) } catch (e: Exception) { TextAlignH.CENTER },
            colorHex = if (o.has("color") && !o.isNull("color")) o.optString("color") else null,
            visible = o.optBoolean("visible", true)
        )
    }
}

data class AspectRatioSpec(val key: String, val label: String, val width: Int, val height: Int)

/** Katalog preset yang bisa dipilih user (rasio, font, tipografi, tema, ikon CTA). */
object CarouselPresets {
    val aspectRatios = listOf(
        AspectRatioSpec("1:1", "1:1 Square", 1080, 1080),
        AspectRatioSpec("4:5", "4:5 Portrait", 1080, 1350),
        AspectRatioSpec("3:4", "3:4 Feed", 1080, 1440),
        AspectRatioSpec("9:16", "9:16 Story", 1080, 1920),
        AspectRatioSpec("16:9", "16:9 Landscape", 1920, 1080)
    )

    fun ratioSpec(key: String): AspectRatioSpec = aspectRatios.firstOrNull { it.key == key } ?: aspectRatios[1]

    val fontFamilies = listOf("Sans", "Serif", "Monospace", "Rounded", "Condensed")

    val typographyStyles = listOf(
        "Modern Minimalist", "Glassmorphism Card", "Bold Hero", "Editorial Serif",
        "Bottom Scrim", "Cyber Neon", "Center Stage", "Left Pro", "Big Quote", "Magazine"
    )

    val backgroundThemes = listOf(
        "Minimalist Tech", "Cyber Neon", "Aesthetic Pastel", "Dark Luxury",
        "Vintage Retro", "3D Illustration", "Solid Dark", "Solid Light",
        "Gradient Indigo", "Gradient Sunset"
    )

    val ctaIcons = listOf("\u27a1\ufe0f", "\ud83d\udc49", "\ud83d\udc47", "\ud83d\udcbe", "\ud83d\udd16", "\ud83d\udccc", "\u2764\ufe0f", "\ud83d\udd25", "\u2728", "\ud83d\udd01", "")

    fun defaultLayoutFor(role: SlideRole): List<ElementLayout> {
        val watermark = ElementLayout(CarouselElement.WATERMARK, 0.5f, 0.955f, 0.9f, 0.7f, align = TextAlignH.CENTER)
        val pageNum = ElementLayout(CarouselElement.PAGE_NUMBER, 0.12f, 0.055f, 0.3f, 0.7f, align = TextAlignH.START)
        val logo = ElementLayout(CarouselElement.LOGO, 0.87f, 0.07f, 0.16f, 1f, align = TextAlignH.END)
        return when (role) {
            SlideRole.HOOK -> listOf(
                pageNum, logo,
                ElementLayout(CarouselElement.HEADLINE, 0.5f, 0.44f, 0.86f, 1.6f, bold = true, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.BODY, 0.5f, 0.66f, 0.82f, 0.95f, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.SUBTEXT, 0.5f, 0.87f, 0.7f, 0.85f, align = TextAlignH.CENTER),
                watermark
            )
            SlideRole.BODY -> listOf(
                pageNum, logo,
                ElementLayout(CarouselElement.HEADLINE, 0.5f, 0.3f, 0.86f, 1.25f, bold = true, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.BODY, 0.5f, 0.56f, 0.84f, 1f, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.SUBTEXT, 0.82f, 0.9f, 0.5f, 0.8f, align = TextAlignH.END),
                watermark
            )
            SlideRole.CTA -> listOf(
                pageNum, logo,
                ElementLayout(CarouselElement.HEADLINE, 0.5f, 0.42f, 0.86f, 1.4f, bold = true, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.BODY, 0.5f, 0.62f, 0.82f, 0.95f, align = TextAlignH.CENTER),
                ElementLayout(CarouselElement.CTA, 0.5f, 0.82f, 0.7f, 1.05f, bold = true, align = TextAlignH.CENTER),
                watermark
            )
        }
    }

    fun roleForSlide(index: Int, total: Int): SlideRole = when {
        index <= 0 -> SlideRole.HOOK
        index >= total - 1 -> SlideRole.CTA
        else -> SlideRole.BODY
    }
}

/**
 * Konfigurasi desain lengkap satu carousel. Dipakai bareng oleh editor (manual),
 * mesin/AI (otomatis), preview, dan exporter — satu sumber kebenaran.
 */
data class CarouselDesign(
    val aspectRatio: String = "4:5",
    val typographyStyle: String = "Modern Minimalist",
    val fontFamily: String = "Sans",
    val backgroundTheme: String = "Minimalist Tech",
    val textColorHex: String = "#FFFFFF",
    val accentColorHex: String = "#38BDF8",
    val baseFontScale: Float = 1f,
    val ctaText: String = "Geser",
    val ctaIcon: String = "\u27a1\ufe0f",
    val watermarkText: String = "@AutoPostStudio",
    val logoBase64: String? = null,
    val showPageNumber: Boolean = true,
    val layouts: Map<SlideRole, List<ElementLayout>> = mapOf(
        SlideRole.HOOK to CarouselPresets.defaultLayoutFor(SlideRole.HOOK),
        SlideRole.BODY to CarouselPresets.defaultLayoutFor(SlideRole.BODY),
        SlideRole.CTA to CarouselPresets.defaultLayoutFor(SlideRole.CTA)
    )
) {
    fun layoutFor(role: SlideRole): List<ElementLayout> = layouts[role] ?: CarouselPresets.defaultLayoutFor(role)

    fun elementIn(role: SlideRole, element: CarouselElement): ElementLayout? =
        layoutFor(role).firstOrNull { it.element == element }

    fun withElement(role: SlideRole, updated: ElementLayout): CarouselDesign {
        val current = layoutFor(role).toMutableList()
        val idx = current.indexOfFirst { it.element == updated.element }
        if (idx >= 0) current[idx] = updated else current.add(updated)
        val newLayouts = layouts.toMutableMap()
        newLayouts[role] = current
        return copy(layouts = newLayouts)
    }

    fun toJsonString(): String = JSONObject().apply {
        put("aspectRatio", aspectRatio)
        put("typographyStyle", typographyStyle)
        put("fontFamily", fontFamily)
        put("backgroundTheme", backgroundTheme)
        put("textColorHex", textColorHex)
        put("accentColorHex", accentColorHex)
        put("baseFontScale", baseFontScale.toDouble())
        put("ctaText", ctaText)
        put("ctaIcon", ctaIcon)
        put("watermarkText", watermarkText)
        put("logoBase64", logoBase64 ?: JSONObject.NULL)
        put("showPageNumber", showPageNumber)
        val lay = JSONObject()
        layouts.forEach { (role, list) ->
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            lay.put(role.name, arr)
        }
        put("layouts", lay)
    }.toString()

    companion object {
        fun fromJsonString(s: String?): CarouselDesign {
            if (s.isNullOrBlank()) return CarouselDesign()
            return try { fromJson(JSONObject(s)) } catch (e: Exception) { CarouselDesign() }
        }

        fun fromJson(o: JSONObject): CarouselDesign {
            val base = CarouselDesign()
            val layouts = mutableMapOf<SlideRole, List<ElementLayout>>()
            val lay = o.optJSONObject("layouts")
            if (lay != null) {
                for (role in SlideRole.values()) {
                    val arr = lay.optJSONArray(role.name) ?: continue
                    val list = mutableListOf<ElementLayout>()
                    for (i in 0 until arr.length()) list.add(ElementLayout.fromJson(arr.getJSONObject(i)))
                    if (list.isNotEmpty()) layouts[role] = list
                }
            }
            return CarouselDesign(
                aspectRatio = o.optString("aspectRatio", base.aspectRatio),
                typographyStyle = o.optString("typographyStyle", base.typographyStyle),
                fontFamily = o.optString("fontFamily", base.fontFamily),
                backgroundTheme = o.optString("backgroundTheme", base.backgroundTheme),
                textColorHex = o.optString("textColorHex", base.textColorHex),
                accentColorHex = o.optString("accentColorHex", base.accentColorHex),
                baseFontScale = o.optDouble("baseFontScale", 1.0).toFloat(),
                ctaText = o.optString("ctaText", base.ctaText),
                ctaIcon = o.optString("ctaIcon", base.ctaIcon),
                watermarkText = o.optString("watermarkText", base.watermarkText),
                logoBase64 = if (o.has("logoBase64") && !o.isNull("logoBase64")) o.optString("logoBase64") else null,
                showPageNumber = o.optBoolean("showPageNumber", true),
                layouts = if (layouts.isEmpty()) base.layouts else layouts
            )
        }
    }
}
