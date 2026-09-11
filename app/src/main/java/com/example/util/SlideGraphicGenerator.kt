package com.example.util

import android.graphics.*
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

object SlideGraphicGenerator {

    fun generateThemedGraphicBase64(
        headline: String,
        body: String,
        theme: String,
        slideNumber: Int = 1,
        aspectRatio: String = "1:1"
    ): String {
        val (width, height) = when (aspectRatio) {
            "4:5" -> 720 to 900
            "3:4" -> 720 to 960
            "9:16" -> 720 to 1280
            else -> 720 to 720
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (theme.uppercase()) {
            "CYBER NEON", "NEON" -> {
                drawCyberNeonBackground(canvas, width, height, paint, slideNumber)
            }
            "AESTHETIC PASTEL", "PASTEL" -> {
                drawAestheticPastelBackground(canvas, width, height, paint, slideNumber)
            }
            "DARK LUXURY", "LUXURY" -> {
                drawDarkLuxuryBackground(canvas, width, height, paint, slideNumber)
            }
            "VINTAGE RETRO", "RETRO" -> {
                drawVintageRetroBackground(canvas, width, height, paint, slideNumber)
            }
            "3D ILLUSTRATION", "3D ABSTRACT", "3D" -> {
                draw3DAbstractBackground(canvas, width, height, paint, slideNumber)
            }
            else -> { // MINIMALIST TECH
                drawMinimalistTechBackground(canvas, width, height, paint, slideNumber)
            }
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun drawMinimalistTechBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Deep Slate/Navy Background
        val bgShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(11, 15, 25), Color.rgb(23, 37, 84), Color.rgb(15, 23, 42)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Ambient Volumetric Glow Orbs
        val orbX = if (seed % 2 == 0) w * 0.75f else w * 0.25f
        val orbY = if (seed % 2 == 0) h * 0.3f else h * 0.7f
        paint.shader = RadialGradient(
            orbX, orbY, w * 0.6f,
            intArrayOf(Color.argb(120, 56, 189, 248), Color.argb(40, 37, 99, 235), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(orbX, orbY, w * 0.6f, paint)

        // Secondary ambient glow
        val orb2X = w - orbX
        val orb2Y = h - orbY
        paint.shader = RadialGradient(
            orb2X, orb2Y, w * 0.5f,
            intArrayOf(Color.argb(80, 99, 102, 241), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(orb2X, orb2Y, w * 0.5f, paint)
        paint.shader = null

        // Geometric Grid / Tech Mesh
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(22, 255, 255, 255)
        val step = 60f
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), paint)
            x += step
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w.toFloat(), y, paint)
            y += step
        }

        // Concentric geometric circles in background
        paint.color = Color.argb(30, 56, 189, 248)
        paint.strokeWidth = 2f
        for (i in 1..4) {
            canvas.drawCircle(w * 0.5f, h * 0.5f, i * 85f, paint)
        }
    }

    private fun drawCyberNeonBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Deep Black/Purple Canvas
        val bgShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(8, 2, 22), Color.rgb(24, 7, 54), Color.rgb(10, 2, 20)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Neon Glow Orbs (Cyan & Magenta)
        paint.shader = RadialGradient(
            w * 0.8f, h * 0.25f, w * 0.55f,
            intArrayOf(Color.argb(140, 0, 240, 255), Color.argb(40, 0, 180, 255), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.8f, h * 0.25f, w * 0.55f, paint)

        paint.shader = RadialGradient(
            w * 0.2f, h * 0.75f, w * 0.55f,
            intArrayOf(Color.argb(140, 255, 0, 128), Color.argb(40, 200, 0, 100), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.2f, h * 0.75f, w * 0.55f, paint)
        paint.shader = null

        // Futuristic diagonal laser streaks
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        for (i in -2..6) {
            paint.color = if (i % 2 == 0) Color.argb(35, 0, 240, 255) else Color.argb(35, 255, 0, 128)
            val startX = i * 140f
            canvas.drawLine(startX, 0f, startX + h * 0.6f, h.toFloat(), paint)
        }

        // Cyber hexagonal/isometric accents
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.argb(45, 0, 240, 255)
        drawHexagon(canvas, w * 0.5f, h * 0.5f, 160f, paint)
        drawHexagon(canvas, w * 0.5f, h * 0.5f, 240f, paint)
    }

    private fun drawAestheticPastelBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Soft Dreamy Cream/Peach Gradient
        val bgShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(255, 241, 235), Color.rgb(254, 226, 226), Color.rgb(250, 232, 255)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Fluid organic aura blobs
        paint.shader = RadialGradient(
            w * 0.7f, h * 0.3f, w * 0.5f,
            intArrayOf(Color.argb(160, 253, 164, 175), Color.argb(70, 254, 205, 211), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.7f, h * 0.3f, w * 0.5f, paint)

        paint.shader = RadialGradient(
            w * 0.3f, h * 0.7f, w * 0.55f,
            intArrayOf(Color.argb(150, 254, 215, 170), Color.argb(60, 253, 230, 138), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.3f, h * 0.7f, w * 0.55f, paint)

        paint.shader = RadialGradient(
            w * 0.5f, h * 0.5f, w * 0.4f,
            intArrayOf(Color.argb(120, 199, 210, 254), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.4f, paint)
        paint.shader = null

        // Gentle abstract curved wave lines
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.argb(40, 244, 114, 182)
        val path = Path().apply {
            moveTo(0f, h * 0.4f)
            cubicTo(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.6f, w.toFloat(), h * 0.35f)
        }
        canvas.drawPath(path, paint)

        val path2 = Path().apply {
            moveTo(0f, h * 0.65f)
            cubicTo(w * 0.4f, h * 0.85f, w * 0.6f, h * 0.45f, w.toFloat(), h * 0.7f)
        }
        paint.color = Color.argb(40, 251, 146, 60)
        canvas.drawPath(path2, paint)
    }

    private fun drawDarkLuxuryBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Obsidian Matte Black Base
        val bgShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(15, 15, 18), Color.rgb(28, 25, 23), Color.rgb(10, 10, 12)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Metallic Gold Ambient Radiance
        paint.shader = RadialGradient(
            w * 0.65f, h * 0.35f, w * 0.55f,
            intArrayOf(Color.argb(120, 234, 179, 8), Color.argb(45, 180, 120, 10), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.65f, h * 0.35f, w * 0.55f, paint)

        paint.shader = RadialGradient(
            w * 0.3f, h * 0.75f, w * 0.45f,
            intArrayOf(Color.argb(90, 217, 119, 6), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.3f, h * 0.75f, w * 0.45f, paint)
        paint.shader = null

        // Luxury Geometric Diamond Gold Shimmer Lines
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(50, 234, 179, 8)

        for (i in 1..3) {
            val dSize = i * 110f
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.5f - dSize)
                lineTo(w * 0.5f + dSize, h * 0.5f)
                lineTo(w * 0.5f, h * 0.5f + dSize)
                lineTo(w * 0.5f - dSize, h * 0.5f)
                close()
            }
            canvas.drawPath(path, paint)
        }

        // Elegant framing border
        paint.strokeWidth = 2f
        paint.color = Color.argb(60, 234, 179, 8)
        canvas.drawRect(30f, 30f, w - 30f, h - 30f, paint)
    }

    private fun drawVintageRetroBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Warm 70s-80s Teal & Terracotta Base
        val bgShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(38, 70, 83), Color.rgb(42, 157, 143), Color.rgb(233, 196, 106), Color.rgb(231, 111, 81)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Sunburst Retro Arch / Sun Sphere
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.55f, w * 0.4f,
            intArrayOf(Color.argb(180, 244, 162, 97), Color.argb(90, 231, 111, 81), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, h * 0.55f, w * 0.4f, paint)
        paint.shader = null

        // Retro Wave Bands
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        val colors = intArrayOf(
            Color.argb(80, 233, 196, 106),
            Color.argb(80, 244, 162, 97),
            Color.argb(80, 231, 111, 81),
            Color.argb(80, 42, 157, 143)
        )
        for (i in colors.indices) {
            paint.color = colors[i]
            val path = Path().apply {
                val offset = i * 22f
                moveTo(0f, h * 0.3f + offset)
                cubicTo(w * 0.35f, h * 0.2f + offset, w * 0.65f, h * 0.45f + offset, w.toFloat(), h * 0.35f + offset)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun draw3DAbstractBackground(canvas: Canvas, w: Int, h: Int, paint: Paint, seed: Int) {
        // Modern Studio Backdrop
        val bgShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(19, 14, 46), Color.rgb(49, 27, 110), Color.rgb(13, 8, 32)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // 3D Spheres with Realistic Specular Highlight
        draw3DSphere(canvas, w * 0.75f, h * 0.28f, w * 0.22f, Color.rgb(168, 85, 247), Color.rgb(236, 72, 153), paint)
        draw3DSphere(canvas, w * 0.22f, h * 0.72f, w * 0.18f, Color.rgb(56, 189, 248), Color.rgb(59, 130, 246), paint)
        draw3DSphere(canvas, w * 0.82f, h * 0.8f, w * 0.12f, Color.rgb(251, 146, 60), Color.rgb(244, 63, 94), paint)

        // Torus / Ring accent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 14f
        paint.color = Color.argb(90, 255, 255, 255)
        val oval = RectF(w * 0.35f, h * 0.45f, w * 0.75f, h * 0.6f)
        canvas.save()
        canvas.rotate(-25f, w * 0.55f, h * 0.52f)
        canvas.drawOval(oval, paint)
        canvas.restore()
    }

    private fun draw3DSphere(canvas: Canvas, cx: Float, cy: Float, radius: Float, baseColor: Int, lightColor: Int, paint: Paint) {
        // Main sphere gradient with 3D highlight offset
        paint.style = Paint.Style.FILL
        val lightX = cx - radius * 0.35f
        val lightY = cy - radius * 0.35f

        paint.shader = RadialGradient(
            lightX, lightY, radius * 1.3f,
            intArrayOf(Color.WHITE, lightColor, baseColor, Color.argb(200, 10, 5, 20)),
            floatArrayOf(0f, 0.25f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = (i * 60 - 30) * Math.PI / 180.0
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
