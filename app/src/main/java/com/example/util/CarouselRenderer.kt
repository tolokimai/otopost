package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.example.data.local.entity.CarouselDesign
import com.example.data.local.entity.CarouselElement
import com.example.data.local.entity.CarouselPresets
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.ElementLayout
import com.example.data.local.entity.SlideRole
import com.example.data.local.entity.TextAlignH
import com.example.data.local.entity.TextCase
import com.example.data.local.entity.TextEffect
import java.io.File
import java.io.FileOutputStream

/**
 * Merender satu slide carousel menjadi Bitmap final. Dipakai untuk preview DAN
 * untuk hasil download, sehingga preview == file yang tersimpan (WYSIWYG).
 */
object CarouselRenderer {

    /** Render background + scrim saja (tanpa teks/elemen). Dipakai editor Canva sebagai lapisan dasar. */
    fun renderBackground(
        context: Context,
        design: CarouselDesign,
        slide: CarouselSlide,
        slideNumber: Int
    ): Bitmap {
        val spec = CarouselPresets.ratioSpec(design.aspectRatio)
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, spec.width, spec.height, design, slide, slideNumber)
        drawScrim(canvas, spec.width, spec.height, design)
        return bitmap
    }

    fun render(
        context: Context,
        design: CarouselDesign,
        slide: CarouselSlide,
        role: SlideRole,
        slideNumber: Int,
        totalSlides: Int
    ): Bitmap {
        val spec = CarouselPresets.ratioSpec(design.aspectRatio)
        val w = spec.width
        val h = spec.height
        val bitmap = renderBackground(context, design, slide, slideNumber)
        val canvas = Canvas(bitmap)

        val baseSizePx = w * 0.052f * design.baseFontScale

        for (el in design.layoutFor(role)) {
            if (!el.visible) continue
            when (el.element) {
                CarouselElement.HEADLINE -> drawTextBox(canvas, w, h, slide.headline, el, design, baseSizePx)
                CarouselElement.BODY -> drawTextBox(canvas, w, h, slide.body, el, design, baseSizePx)
                CarouselElement.SUBTEXT -> if (slide.subtext.isNotBlank()) drawPill(canvas, w, h, slide.subtext, el, design, baseSizePx)
                CarouselElement.CTA -> {
                    val txt = (design.ctaText + (if (design.ctaIcon.isNotBlank()) " " + design.ctaIcon else "")).trim()
                    if (txt.isNotBlank()) drawTextBox(canvas, w, h, txt, el, design, baseSizePx)
                }
                CarouselElement.WATERMARK -> if (design.watermarkText.isNotBlank()) drawTextBox(canvas, w, h, design.watermarkText, el, design, baseSizePx)
                CarouselElement.PAGE_NUMBER -> if (design.showPageNumber) drawTextBox(canvas, w, h, String.format("%02d / %02d", slideNumber, totalSlides), el, design, baseSizePx)
                CarouselElement.LOGO -> drawLogo(canvas, w, h, el, design)
                CarouselElement.SWIPE -> drawSwipe(canvas, w, h, el, design, baseSizePx, slideNumber, totalSlides)
            }
        }
        return bitmap
    }

    fun renderAll(context: Context, design: CarouselDesign, slides: List<CarouselSlide>): List<Bitmap> {
        val total = slides.size
        return slides.mapIndexed { index, slide ->
            render(context, design, slide, CarouselPresets.roleForSlide(index, total), index + 1, total)
        }
    }

    private fun applyCase(text: String, case: TextCase): String = when (case) {
        TextCase.UPPER -> text.uppercase()
        TextCase.LOWER -> text.lowercase()
        TextCase.TITLE -> text.split(" ").joinToString(" ") { word -> if (word.isNotEmpty()) word.substring(0, 1).uppercase() + word.substring(1).lowercase() else word }
        else -> text
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, design: CarouselDesign, slide: CarouselSlide, slideNumber: Int) {
        val b64 = slide.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (src != null) { drawBitmapCenterCrop(canvas, src, w, h); return }
            } catch (e: Exception) { /* fall through */ }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (design.backgroundTheme) {
            "Solid Dark" -> { paint.color = Color.rgb(17, 17, 20); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); return }
            "Solid Light" -> { paint.color = Color.rgb(245, 245, 247); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); return }
            "Gradient Indigo" -> { drawVerticalGradient(canvas, w, h, intArrayOf(Color.rgb(15, 23, 42), Color.rgb(30, 27, 75), Color.rgb(49, 46, 129))); return }
            "Gradient Sunset" -> { drawVerticalGradient(canvas, w, h, intArrayOf(Color.rgb(76, 5, 25), Color.rgb(131, 24, 67), Color.rgb(157, 23, 77))); return }
        }
        try {
            val genB64 = SlideGraphicGenerator.generateThemedGraphicBase64(
                headline = slide.headline,
                body = slide.body,
                theme = design.backgroundTheme,
                slideNumber = slideNumber,
                aspectRatio = design.aspectRatio
            )
            val bytes = Base64.decode(genB64, Base64.DEFAULT)
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (src != null) { drawBitmapCenterCrop(canvas, src, w, h); return }
        } catch (e: Exception) { /* fall through */ }
        drawVerticalGradient(canvas, w, h, intArrayOf(Color.rgb(15, 23, 42), Color.rgb(30, 41, 59), Color.rgb(15, 23, 42)))
    }

    private fun drawVerticalGradient(canvas: Canvas, w: Int, h: Int, colors: IntArray) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawScrim(canvas: Canvas, w: Int, h: Int, design: CarouselDesign) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (design.typographyStyle) {
            "Bottom Scrim" -> paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.argb(38, 0, 0, 0), Color.argb(128, 0, 0, 0), Color.argb(230, 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            "Glassmorphism Card", "Center Stage" -> paint.color = Color.argb(70, 0, 0, 0)
            "Cyber Neon" -> paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.argb(110, 8, 2, 22), Color.argb(190, 8, 2, 22)), null, Shader.TileMode.CLAMP)
            "Solid Light", "Magazine" -> paint.color = Color.argb(0, 0, 0, 0)
            else -> paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.argb(90, 0, 0, 0), Color.argb(160, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun typefaceFor(family: String, bold: Boolean, italic: Boolean): Typeface {
        val base = when (family) {
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            "Rounded" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "Condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    private fun parseColor(hex: String?): Int = try {
        if (hex.isNullOrBlank()) Color.WHITE else Color.parseColor(hex)
    } catch (e: Exception) { Color.WHITE }

    private fun buildTextPaint(design: CarouselDesign, el: ElementLayout, sizePx: Float): TextPaint {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = typefaceFor(design.fontFamily, el.bold, el.italic)
        tp.textSize = sizePx
        tp.color = parseColor(el.colorHex ?: design.textColorHex)
        tp.isUnderlineText = el.underline
        tp.letterSpacing = el.letterSpacing
        return tp
    }

    private fun alignOf(a: TextAlignH): Layout.Alignment = when (a) {
        TextAlignH.START -> Layout.Alignment.ALIGN_NORMAL
        TextAlignH.END -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_CENTER
    }

    private fun buildLayout(text: String, tp: TextPaint, width: Int, align: TextAlignH): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(alignOf(align))
            .setLineSpacing(0f, 1.06f)
            .setIncludePad(false)
            .build()

    /**
     * Menggambar blok teks. PENTING: posisi memakai PUSAT KONTEN sebenarnya
     * (bukan pusat kotak lebar tetap), sehingga elemen kecil seperti nomor
     * halaman tidak lagi setengah keluar kanvas, dan posisinya konsisten
     * dengan editor. Konten juga di-clamp agar tidak terpotong tepi.
     */
    private fun drawTextBox(canvas: Canvas, w: Int, h: Int, rawText: String, el: ElementLayout, design: CarouselDesign, baseSizePx: Float) {
        val text = applyCase(rawText, el.case)
        if (text.isBlank()) return
        val size = baseSizePx * el.fontScale
        val tp = buildTextPaint(design, el, size)
        val accent = parseColor(design.accentColorHex)
        when (el.effect) {
            TextEffect.SHADOW -> tp.setShadowLayer(size * 0.18f, 0f, size * 0.06f, Color.argb(180, 0, 0, 0))
            TextEffect.NEON -> { tp.setShadowLayer(size * 0.55f, 0f, 0f, accent); tp.color = Color.WHITE }
            TextEffect.GRADIENT -> tp.shader = LinearGradient(0f, 0f, 0f, size * 1.4f, intArrayOf(parseColor(el.colorHex ?: design.textColorHex), accent), null, Shader.TileMode.CLAMP)
            else -> {}
        }
        val wrapWidth = (el.widthFraction * w).toInt().coerceIn(20, w)
        // Ukur lebar konten nyata (baris terpanjang) untuk positioning berbasis pusat konten.
        val measure = buildLayout(text, tp, wrapWidth, el.align)
        var contentW = 0f
        for (i in 0 until measure.lineCount) contentW = maxOf(contentW, measure.getLineWidth(i))
        val boxWidth = (contentW.toInt().coerceIn(1, wrapWidth)) + 2
        val sl = buildLayout(text, tp, boxWidth, el.align)
        var left = el.xFraction * w - boxWidth / 2f
        val top = el.yFraction * h - sl.height / 2f
        // Jaga tetap di dalam kanvas (tidak terpotong tepi kiri/kanan).
        val maxLeft = (w - boxWidth).toFloat().coerceAtLeast(0f)
        left = left.coerceIn(0f, maxLeft)

        if (el.effect == TextEffect.HIGHLIGHT) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = Color.argb(150, 0, 0, 0)
            val pad = size * 0.35f
            val rect = RectF(left - pad, top - pad, left + boxWidth + pad, top + sl.height + pad)
            canvas.drawRoundRect(rect, size * 0.3f, size * 0.3f, bg)
        }

        canvas.save()
        canvas.translate(left, top)
        if (el.effect == TextEffect.OUTLINE) {
            val stroke = buildTextPaint(design, el, size)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = size * 0.09f
            stroke.color = Color.BLACK
            stroke.clearShadowLayer()
            buildLayout(text, stroke, boxWidth, el.align).draw(canvas)
        }
        sl.draw(canvas)
        canvas.restore()
    }

    private fun drawPill(canvas: Canvas, w: Int, h: Int, rawText: String, el: ElementLayout, design: CarouselDesign, baseSizePx: Float) {
        val text = applyCase(rawText, el.case)
        if (text.isBlank()) return
        val size = baseSizePx * el.fontScale
        val tp = buildTextPaint(design, el, size)
        tp.clearShadowLayer()
        val maxW = el.widthFraction * w
        val textW = minOf(tp.measureText(text), maxW - size)
        val padH = size * 0.7f
        val padV = size * 0.42f
        val pillW = textW + padH * 2
        val fm = tp.fontMetrics
        val pillH = (fm.descent - fm.ascent) + padV * 2
        val cx = el.xFraction * w
        val cy = el.yFraction * h
        val rect = RectF(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = Color.argb(150, 0, 0, 0)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, bg)
        val border = Paint(Paint.ANTI_ALIAS_FLAG)
        border.style = Paint.Style.STROKE
        border.strokeWidth = size * 0.06f
        border.color = parseColor(design.accentColorHex)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, border)
        tp.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun drawLogo(canvas: Canvas, w: Int, h: Int, el: ElementLayout, design: CarouselDesign) {
        val b64 = design.logoBase64 ?: return
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val dw = el.widthFraction * w
            val dh = dw * (src.height.toFloat() / src.width.toFloat())
            val cx = el.xFraction * w
            val cy = el.yFraction * h
            canvas.drawBitmap(src, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) { /* ignore */ }
    }

    /**
     * Indikator geser (swipe): teks polos, emoji bawaan, atau ikon PNG upload.
     * Sesuai permintaan: TANPA background & TANPA garis tepi -> hanya teks/ikon.
     */
    private fun drawSwipe(
        canvas: Canvas,
        w: Int,
        h: Int,
        el: ElementLayout,
        design: CarouselDesign,
        baseSizePx: Float,
        slideNumber: Int,
        totalSlides: Int
    ) {
        if (!design.swipeEnabled) return
        val isLast = slideNumber >= totalSlides
        if (isLast && !design.swipeShowOnLastSlide) return

        val size = baseSizePx * el.fontScale
        val tp = buildTextPaint(design, el, size)
        tp.textAlign = Paint.Align.LEFT

        val text = if (design.swipeIconOnly) "" else applyCase(design.swipeText, el.case)
        val pngIcon: Bitmap? = if (!design.swipeIconBase64.isNullOrBlank()) {
            try {
                val b = Base64.decode(design.swipeIconBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(b, 0, b.size)
            } catch (e: Exception) { null }
        } else null
        val emojiIcon = if (pngIcon == null) design.swipeIconBuiltin else ""

        val iconSize = size * 1.2f
        val textW = if (text.isNotBlank()) tp.measureText(text) else 0f
        val iconW = when {
            pngIcon != null -> iconSize
            emojiIcon.isNotBlank() -> tp.measureText(emojiIcon)
            else -> 0f
        }
        val gap = if (textW > 0f && iconW > 0f) size * 0.3f else 0f
        val contentW = textW + gap + iconW
        if (contentW <= 0f) return

        val fm = tp.fontMetrics
        val cx = el.xFraction * w
        val cy = el.yFraction * h

        // Tanpa background & tanpa garis tepi: langsung gambar teks/ikon polos.
        var drawX = cx - contentW / 2f
        val baselineY = cy - (fm.ascent + fm.descent) / 2f
        if (text.isNotBlank()) {
            canvas.drawText(text, drawX, baselineY, tp)
            drawX += textW + gap
        }
        if (pngIcon != null) {
            val top = cy - iconSize / 2f
            canvas.drawBitmap(pngIcon, null, RectF(drawX, top, drawX + iconSize, top + iconSize), Paint(Paint.FILTER_BITMAP_FLAG))
        } else if (emojiIcon.isNotBlank()) {
            canvas.drawText(emojiIcon, drawX, baselineY, tp)
        }
    }
}

/** Menyimpan slide-slide carousel ke penyimpanan lokal (galeri) sebagai PNG. */
object CarouselExporter {
    fun saveBitmaps(context: Context, bitmaps: List<Bitmap>, baseName: String): List<String> {
        val saved = mutableListOf<String>()
        val safeBase = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "carousel" }
        val stamp = System.currentTimeMillis()
        bitmaps.forEachIndexed { index, bmp ->
            val name = safeBase + "_" + stamp + "_slide" + (index + 1) + ".png"
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoPostStudio")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
                        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                        saved.add(uri.toString())
                    }
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AutoPostStudio")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, name)
                    FileOutputStream(file).use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
                    saved.add(file.absolutePath)
                }
            } catch (e: Exception) { /* lewati slide ini */ }
        }
        return saved
    }
}
