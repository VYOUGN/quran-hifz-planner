package com.sallo.hifz.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/** The pictures a widget cannot draw by itself: its progress ring and bar, and «تم ✓». */
internal object WidgetArt {
    /** A ring of [sizePx], filled clockwise from the top by [progress] (0..1). */
    fun ring(progress: Float, arc: Int, track: Int, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(24, 512)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = size * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val box = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        paint.color = track
        canvas.drawArc(box, 0f, 360f, false, paint)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            paint.color = arc
            canvas.drawArc(box, -90f, sweep, false, paint)
        }
        return bitmap
    }

    /**
     * «تم ✓» of [sizePx]: a ring in [color] with a faint check while today's session is open, a circle
     * filled with [color] and a check in [mark] once it is done.
     */
    fun check(done: Boolean, color: Int, mark: Int, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(24, 256)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = size / 2f
        paint.color = color
        if (done) {
            canvas.drawCircle(half, half, half, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.08f
            canvas.drawCircle(half, half, half - paint.strokeWidth / 2, paint)
        }
        val tick = Path().apply {
            moveTo(size * 0.28f, size * 0.52f)
            lineTo(size * 0.43f, size * 0.66f)
            lineTo(size * 0.72f, size * 0.36f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.09f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (done) mark else (color and 0x00FFFFFF) or (((color ushr 24) * 0.4f).toInt() shl 24)
        canvas.drawPath(tick, paint)
        return bitmap
    }

    /** A rounded bar filled from the right, the start of an Arabic line. The widget scales it to its width. */
    fun bar(progress: Float, fill: Int, track: Int, widthPx: Int = 480, heightPx: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = heightPx / 2f
        paint.color = track
        canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)
        val filled = widthPx * progress.coerceIn(0f, 1f)
        if (filled > 0f) {
            paint.color = fill
            val left = widthPx - max(filled, heightPx.toFloat())
            canvas.drawRoundRect(RectF(left, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)
        }
        return bitmap
    }
}
