package com.sallo.hifz.design

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Color math on ARGB ints, with no Android or Compose types so the designs can be tested on their own
 * and shared by the app theme and the widgets. A «tone» is a color's lightness L* (0 black, 100 white)
 * in CIELAB: tones of one seed keep its hue, and as much of its chroma as the screen can show.
 */
object Tones {
    private const val XN = 0.95047
    private const val ZN = 1.08883
    private const val E = 216.0 / 24389.0
    private const val K = 24389.0 / 27.0

    private fun linear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun gamma(v: Double): Double = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055

    private fun f(t: Double): Double = if (t > E) cbrt(t) else (K * t + 16) / 116

    private fun inv(t: Double): Double = if (t * t * t > E) t * t * t else (116 * t - 16) / K

    /** L*, a*, b* of [argb]. */
    private fun lab(argb: Int): DoubleArray {
        val r = linear(argb shr 16 and 0xFF)
        val g = linear(argb shr 8 and 0xFF)
        val b = linear(argb and 0xFF)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    /** Linear sRGB of a CIELAB color; channels fall outside 0..1 when the screen cannot show it. */
    private fun rgb(l: Double, a: Double, b: Double): DoubleArray {
        val fy = (l + 16) / 116
        val x = inv(fy + a / 500) * XN
        val y = inv(fy)
        val z = inv(fy - b / 200) * ZN
        return doubleArrayOf(
            3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
        )
    }

    private fun inGamut(c: DoubleArray): Boolean = c.all { it >= -1e-7 && it <= 1 + 1e-7 }

    private fun pack(c: DoubleArray): Int {
        fun channel(v: Double) = (gamma(v.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(c[0]) shl 16) or (channel(c[1]) shl 8) or channel(c[2])
    }

    /** L* of [argb]: 0 for black, 100 for white. */
    fun lightness(argb: Int): Double = lab(argb)[0]

    /** How colorful [argb] is (0 for grays). */
    fun chroma(argb: Int): Double = lab(argb).let { hypot(it[1], it[2]) }

    /**
     * [seed]'s hue at lightness [tone]. Its chroma is the seed's own, or [chroma] when given, lowered as
     * far as needed for the screen to show the color.
     */
    fun tone(seed: Int, tone: Double, chroma: Double? = null): Int {
        if (tone <= 0.0) return 0xFF000000.toInt()
        if (tone >= 100.0) return 0xFFFFFFFF.toInt()
        val lab = lab(seed)
        val hue = atan2(lab[2], lab[1])
        val own = hypot(lab[1], lab[2])
        // A gray seed has no hue to speak of, so it stays gray whatever chroma is asked for.
        val wanted = if (own < 0.5) 0.0 else chroma ?: own
        val l = tone.coerceIn(0.0, 100.0)
        fun at(c: Double) = rgb(l, c * cos(hue), c * sin(hue))
        if (inGamut(at(wanted))) return pack(at(wanted))
        var lo = 0.0
        var hi = wanted
        repeat(24) {
            val mid = (lo + hi) / 2
            if (inGamut(at(mid))) lo = mid else hi = mid
        }
        return pack(at(lo))
    }

    /** WCAG relative luminance. */
    fun luminance(argb: Int): Double =
        0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)

    /** WCAG contrast ratio of two opaque colors: 1 (the same) to 21 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [a] with [share] (0..1) of [b] mixed in. */
    fun mix(a: Int, b: Int, share: Double): Int {
        fun channel(shift: Int): Int {
            val x = a shr shift and 0xFF
            val y = b shr shift and 0xFF
            return (x + (y - x) * share).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** [argb] with its alpha set to [alpha] (0..255). */
    fun alpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** [top] (which may be translucent) drawn over the opaque [bottom]. */
    fun over(top: Int, bottom: Int): Int = mix(bottom, top or (0xFF shl 24), (top ushr 24) / 255.0)
}
