package com.android.music.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object GradientGenerator {

    private val gradientSchemes = listOf(
        GradientScheme(0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(), 0xFF22D3EE.toInt()),
        GradientScheme(0xFF3B82F6.toInt(), 0xFF1E3A8A.toInt(), 0xFF60A5FA.toInt()),
        GradientScheme(0xFF10B981.toInt(), 0xFF064E3B.toInt(), 0xFF34D399.toInt()),
        GradientScheme(0xFFF97316.toInt(), 0xFF7C2D12.toInt(), 0xFFFBBF24.toInt()),
        GradientScheme(0xFFEC4899.toInt(), 0xFF4A044E.toInt(), 0xFFF472B6.toInt()),
        GradientScheme(0xFF06B6D4.toInt(), 0xFF083344.toInt(), 0xFF22D3EE.toInt()),
        GradientScheme(0xFFA855F7.toInt(), 0xFF2E1065.toInt(), 0xFFC084FC.toInt()),
    )

    data class GradientScheme(val primary: Int, val secondary: Int, val accent: Int)

    fun getGradientScheme(songId: Long): GradientScheme {
        return gradientSchemes[kotlin.math.abs(songId.toInt()) % gradientSchemes.size]
    }

    fun generateTexturedGradientBitmap(
        context: Context,
        songId: Long,
        size: Int
    ): Bitmap {

        val scheme = getGradientScheme(songId)
        val rnd = Random(songId)

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val density = context.resources.displayMetrics.density
        val radius = 12 * density
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        /* ───────── Base Flow Gradient ───────── */
        val angle = rnd.nextInt(0, 360)
        val rad = Math.toRadians(angle.toDouble())

        val x = cos(rad).toFloat() * size
        val y = sin(rad).toFloat() * size

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, x, y,
                intArrayOf(
                    shiftColor(scheme.primary, rnd),
                    scheme.secondary
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, basePaint)

        /* ───────── Primary Light Bloom ───────── */
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.25f,
                size * 0.2f,
                size * 0.85f,
                scheme.accent,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            alpha = 90
        }.also {
            canvas.drawRoundRect(rect, radius, radius, it)
        }

        /* ───────── Secondary Soft Glow ───────── */
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.8f,
                size * 0.75f,
                size.toFloat(),
                Color.WHITE,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            alpha = 18
        }.also {
            canvas.drawRoundRect(rect, radius, radius, it)
        }

        /* ───────── Edge Vignette ───────── */
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size / 2f,
                size / 2f,
                size.toFloat(),
                Color.TRANSPARENT,
                Color.argb(160, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }.also {
            canvas.drawRoundRect(rect, radius, radius, it)
        }

        /* ───────── Film Grain Texture ───────── */
        val noise = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val noisePixels = ByteArray(size * size) {
            rnd.nextInt(0, 255).toByte()
        }
        noise.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(noisePixels))

        Paint().apply {
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = 14
        }.also {
            canvas.drawRoundRect(rect, radius, radius, it)
        }

        return bitmap
    }

    fun generateTexturedGradient(
        context: Context,
        songId: Long,
        sizeDp: Int = 48
    ): Drawable {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        return generateTexturedGradientBitmap(context, songId, sizePx)
            .toDrawable(context.resources)
    }

    /* ───────── Small color drift for organic feel ───────── */
    private fun shiftColor(color: Int, rnd: Random): Int {
        fun clamp(v: Int) = v.coerceIn(0, 255)
        val r = clamp(Color.red(color) + rnd.nextInt(-12, 12))
        val g = clamp(Color.green(color) + rnd.nextInt(-12, 12))
        val b = clamp(Color.blue(color) + rnd.nextInt(-12, 12))
        return Color.rgb(r, g, b)
    }

}
