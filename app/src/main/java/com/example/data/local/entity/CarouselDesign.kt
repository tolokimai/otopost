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

/** Model penulisan huruf (bagian dari tipografi). */
enum class TextCase { NORMAL, UPPER, LOWER, TITLE }

/** Efek visual/art pada teks (bagian dari tipografi). */
enum class TextEffect { NONE, SHADOW, OUTLINE, HIGHLIGHT, NEON, GRADIENT }

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
    val visible: Boolean = true,
    val case: TextCase = TextCase.NORMAL,
    val effect: TextEffect = TextEffect.NONE,
    val letterSpacing: Float = 0f
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
        put("case", case.name)
        put("effect", effect.name)
        put("letterSpacing", letterSpacing.toDouble())
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
            visible = o.optBoolean("visible", true),
            case = try { TextCase.valueOf(o.optString("case", "NORMAL")) } catch (e: Exception) { TextCase.NORMAL },
            effect = try { TextEffect.valueOf(o.optString("effect", "NONE")) } catch (e: Exception) { TextEffect.NONE },
            letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat()
        )
    }
}

data class AspectRatioSpec(val key: String, val label: String, val width: Int, val height: Int)

/** Preset tipografi: bukan cuma jenis huruf, tapi juga model penulisan, efek art, & spasi. */
data class TypographyPreset(
    val name: String,
    val fontFamily: String,
    val case: TextCase,
    val effect: TextEffect,
    val letterSpacing: Float,
    val bold: Boolean,
    val description: String
)

/** Katalog preset yang bisa dipilih user (rasio, font, tipografi, tema, ikon CTA, layout). */
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

    val typographyPresets = listOf(
        TypographyPreset("Modern Minimalist", "Sans", TextCase.NORMAL, TextEffect.NONE, 0f, false, "Bersih & netral"),
        TypographyPreset("Glassmorphism Card", "Sans", TextCase.NORMAL, TextEffect.HIGHLIGHT, 0f, true, "Kartu highlight"),
        TypographyPreset("Bold Hero", "Sans", TextCase.UPPER, TextEffect.SHADOW, 0.02f, true, "Kapital tebal"),
        TypographyPreset("Editorial Serif", "Serif", TextCase.TITLE, TextEffect.NONE, 0f, false, "Elegan majalah"),
        TypographyPreset("Bottom Scrim", "Sans", TextCase.NORMAL, TextEffect.SHADOW, 0f, true, "Teks bawah gelap"),
        TypographyPreset("Cyber Neon", "Monospace", TextCase.UPPER, TextEffect.NEON, 0.06f, true, "Glow neon"),
        TypographyPreset("Center Stage", "Sans", TextCase.NORMAL, TextEffect.SHADOW, 0.01f, true, "Fokus tengah"),
        TypographyPreset("Left Pro", "Sans", TextCase.NORMAL, TextEffect.NONE, 0f, true, "Rata kiri pro"),
        TypographyPreset("Big Quote", "Serif", TextCase.NORMAL, TextEffect.NONE, 0f, false, "Kutipan besar"),
        TypographyPreset("Magazine", "Serif", TextCase.UPPER, TextEffect.OUTLINE, 0.04f, true, "Outline majalah")
    )

    val typographyStyles = typographyPresets.map { it.name }

    fun typographyPresetFor(name: String): TypographyPreset =
        typographyPresets.firstOrNull { it.name == name } ?: typographyPresets[0]

    val backgroundThemes = listOf(
        "Minimalist Tech", "Cyber Neon", "Aesthetic Pastel", "Dark Luxury",
        "Vintage Retro", "3D Illustration", "Solid Dark", "Solid Light",
        "Gradient Indigo", "Gradient Sunset"
    )

    val ctaIcons = listOf("\u27a1\ufe0f", "\ud83d\udc49", "\ud83d\udc47", "\ud83d\udcbe", "\ud83d\udd16", "\ud83d\udccc", "\u2764\ufe0f", "\ud83d\udd25", "\u2728", "\ud83d\udd01", "")

    val layoutTemplates = listOf("Classic Center", "Top Heading", "Bottom Bar", "Left Aligned", "Big Quote")

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

    /** Template layout siap-pakai (mengatur posisi semua elemen sekaligus). */
    fun templateLayoutFor(template: String, role: SlideRole): List<ElementLayout> {
        val base = defaultLayoutFor(role)
        return when (template) {
            "Top Heading" -> base.map { el ->
                when (el.element) {
                    CarouselElement.HEADLINE -> el.copy(yFraction = 0.2f)
                    CarouselElement.BODY -> el.copy(yFraction = 0.44f)
                    CarouselElement.SUBTEXT -> el.copy(yFraction = 0.62f)
                    CarouselElement.CTA -> el.copy(yFraction = 0.8f)
                    else -> el
                }
            }
            "Bottom Bar" -> base.map { el ->
                when (el.element) {
                    CarouselElement.SUBTEXT -> el.copy(yFraction = 0.62f)
                    CarouselElement.HEADLINE -> el.copy(yFraction = 0.72f)
                    CarouselElement.BODY -> el.copy(yFraction = 0.83f)
                    CarouselElement.CTA -> el.copy(yFraction = 0.92f)
                    else -> el
                }
            }
            "Left Aligned" -> base.map { el ->
                when (el.element) {
                    CarouselElement.HEADLINE, CarouselElement.BODY -> el.copy(xFraction = 0.32f, widthFraction = 0.62f, align = TextAlignH.START)
                    CarouselElement.SUBTEXT, CarouselElement.CTA -> el.copy(xFraction = 0.32f, align = TextAlignH.START)
                    else -> el
                }
            }
            "Big Quote" -> base.map { el ->
                when (el.element) {
                    CarouselElement.HEADLINE -> el.copy(yFraction = 0.5f, fontScale = el.fontScale * 1.15f)
                    CarouselElement.BODY -> el.copy(yFraction = 0.72f)
                    CarouselElement.SUBTEXT -> el.copy(yFraction = 0.86f)
                    else -> el
                }
            }
            else -> base
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
 * mesin/AI (otomatis), preview, dan exporter - satu sumber kebenaran.
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
    val aiBackgroundPrompt: String = "",
    val layoutTemplate: String = "Classic Center",
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

    /** Terapkan template posisi ke semua peran sekaligus. */
    fun withLayoutTemplate(template: String): CarouselDesign {
        val newLayouts = mutableMapOf<SlideRole, List<ElementLayout>>()
        for (r in SlideRole.values()) newLayouts[r] = CarouselPresets.templateLayoutFor(template, r)
        return copy(layoutTemplate = template, layouts = newLayouts)
    }

    /** Terapkan preset tipografi (model penulisan, efek art, spasi, bold) ke semua elemen teks. */
    fun withTypographyApplied(): CarouselDesign {
        val preset = CarouselPresets.typographyPresetFor(typographyStyle)
        val newLayouts = layouts.mapValues { (_, list) ->
            list.map { el ->
                if (el.element == CarouselElement.LOGO || el.element == CarouselElement.PAGE_NUMBER) el
                else el.copy(
                    case = preset.case,
                    effect = preset.effect,
                    letterSpacing = preset.letterSpacing,
                    bold = if (el.element == CarouselElement.HEADLINE || el.element == CarouselElement.CTA) preset.bold else el.bold
                )
            }
        }
        return copy(fontFamily = preset.fontFamily, layouts = newLayouts)
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
        put("aiBackgroundPrompt", aiBackgroundPrompt)
        put("layoutTemplate", layoutTemplate)
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
                aiBackgroundPrompt = o.optString("aiBackgroundPrompt", base.aiBackgroundPrompt),
                layoutTemplate = o.optString("layoutTemplate", base.layoutTemplate),
                layouts = if (layouts.isEmpty()) base.layouts else layouts
            )
        }
    }
}
