package com.sallo.hifz.design

/** How the home-screen widgets look («هيئة الودجت»); the same for all of them. */
enum class WidgetLook(val label: String, val hint: String) {
    APP("مثل التطبيق", "ألوان التصميم، فاتحة أو داكنة"),
    COLOR("ملوّن", "تدرّج لون التصميم ونص أبيض"),
    GLASS("زجاجي", "شفاف غامق يناسب أي خلفية"),
}

/**
 * A widget's colors as ARGB ints. [backgrounds] is one color, or three stops of a gradient, drawn at
 * [backgroundAlpha]. Marks: [arc] and [track] for the progress ring and bar; [done], [missed], [today]
 * and [idle] for the days of the week strip.
 */
class WidgetColors(
    val backgrounds: IntArray,
    val backgroundAlpha: Int,
    val title: Int,
    val text: Int,
    val muted: Int,
    val arc: Int,
    val track: Int,
    val done: Int,
    val missed: Int,
    val today: Int,
    val idle: Int,
) {
    /** The backgrounds as they look on screen: a see-through one over the brightest wallpaper. */
    val seenBackgrounds: IntArray
        get() = if (backgroundAlpha >= 255) backgrounds else IntArray(backgrounds.size) { Tones.over(Tones.alpha(backgrounds[it], backgroundAlpha), WHITE) }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val SOFT_RED = 0xFFFFB4AB.toInt()

        /** Dark enough behind white text over any wallpaper. */
        const val GLASS_ALPHA = 0xA0

        fun of(p: DesignPalette, look: WidgetLook): WidgetColors = when (look) {
            WidgetLook.APP -> WidgetColors(
                backgrounds = intArrayOf(if (p.dark) p.surfaceContainer else p.surfaceContainerLow),
                backgroundAlpha = 255,
                title = p.primary,
                text = p.onSurface,
                muted = p.onSurfaceVariant,
                arc = p.primary,
                track = if (p.dark) p.surfaceContainerHighest else p.surfaceVariant,
                done = p.primary,
                missed = p.error,
                today = if (p.dark) p.accent else p.accentText,
                idle = p.outlineVariant,
            )
            WidgetLook.COLOR -> WidgetColors(
                backgrounds = p.tint,
                backgroundAlpha = 255,
                title = WHITE,
                text = WHITE,
                muted = Tones.alpha(WHITE, 0xE6),
                arc = p.glow,
                track = Tones.alpha(WHITE, 0x40),
                done = WHITE,
                missed = SOFT_RED,
                today = p.glow,
                idle = Tones.alpha(WHITE, 0x55),
            )
            WidgetLook.GLASS -> WidgetColors(
                backgrounds = intArrayOf(BLACK),
                backgroundAlpha = GLASS_ALPHA,
                title = WHITE,
                text = WHITE,
                muted = Tones.alpha(WHITE, 0xE6),
                arc = p.glow,
                track = Tones.alpha(WHITE, 0x40),
                done = WHITE,
                missed = SOFT_RED,
                today = p.glow,
                idle = Tones.alpha(WHITE, 0x55),
            )
        }
    }
}
