package com.sallo.hifz.design

import kotlin.math.max
import kotlin.math.min

/** A look of the whole app («التصميم»): its colors and the style of the hero card. */
enum class AppDesign(val label: String, val hint: String) {
    EMERALD("زمردي", "أخضر زمردي وذهبي"),
    NIGHT("ليلي ذهبي", "كحلي غامق وذهبي"),
    PAPER("ورق المصحف", "كريمي دافئ وبني"),
    BURGUNDY("عنّابي", "عنّابي وكريمي وذهبي"),
    TEAL("فيروزي", "فيروزي وبرتقالي هادئ"),
    GRAPHITE("فحمي بسيط", "رمادي غامق وأبيض"),

    /** The phone's own colors (Android 12 and later). */
    DYNAMIC("ألوان جوالك", "من ألوان خلفية الجوال"),

    /** «ألوانك»: a main color, an accent and a hero card chosen in «التصاميم». */
    CUSTOM("ألوانك", "ألوان تختارها بنفسك"),
}

/** The typeface of the app («الخط»). */
enum class AppFont(val label: String, val hint: String) {
    SYSTEM("خط الجوال", "الخط اللي في إعدادات جوالك"),
    ALMARAI("المراعي", "عصري وواضح"),
    NASKH("نسخ", "تقليدي مثل الكتب"),
}

/** How the hero card of the table is drawn. */
enum class HeroStyle {
    /** A diagonal gradient. */
    GRADIENT,

    /** A gradient under a faint lattice of eight-pointed stars. */
    STARS,

    /** A paper card inside a double border. */
    FRAME,

    /** One flat color with an accent bar at its start edge. */
    FLAT,
}

/**
 * Every color of a design in light or dark, as ARGB ints. The first block is Material's color roles;
 * the rest are the app's own: the paper table, the hero card, and the accent that marks today.
 */
class DesignPalette(
    val dark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceBright: Int,
    val surfaceDim: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val tableGrid: Int,
    val tableHeader: Int,
    val tableDone: Int,
    val tableMissed: Int,
    val tableToday: Int,
    val heroStyle: HeroStyle,
    /** Three stops of the hero card's background. */
    val hero: IntArray,
    /** Text and marks on the hero card. */
    val onHero: Int,
    /** The accent on the hero card and around today's session (the gold of the emerald design). */
    val accent: Int,
    /** A lighter accent, for the shine of the progress ring. */
    val accentBright: Int,
    /** The accent as text on the design's surfaces. */
    val accentText: Int,
    /** An accent bright enough for dark and colored backgrounds, whatever the mode. */
    val glow: Int,
    /** Three stops of a colored background that takes white text (the «ملوّن» widgets). */
    val tint: IntArray,
)

/** What a generated design is made from. */
class Seeds(
    /** The brand color: the light scheme's primary as it is, and the hue of every primary tone. */
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    /** The hue of backgrounds and surfaces, tinted by [neutralChroma]. */
    val neutral: Int = primary,
    val neutralChroma: Double = 4.0,
    val accent: Int,
    val accentDark: Int = accent,
    val heroStyle: HeroStyle = HeroStyle.GRADIENT,
    val hero: IntArray,
    val heroDark: IntArray = hero,
    val onHero: Int = WHITE,
    val onHeroDark: Int = onHero,
    val tint: IntArray = hero,
)

private const val WHITE = 0xFFFFFFFF.toInt()
private const val GREEN = 0xFF2E9E6B.toInt()
private const val RED = 0xFFD64545.toInt()

private fun c(rgb: Long): Int = (0xFF000000L or rgb).toInt()

object Palettes {
    /** The palette of [design]. The phone's own colors need a Context: without one they fall back to emerald. */
    fun of(design: AppDesign, dark: Boolean): DesignPalette = when (val seeds = SEEDS[design]) {
        null -> if (dark) EMERALD_DARK else EMERALD_LIGHT
        else -> build(seeds, dark)
    }

    /** [seed]'s hue at [tone], no more colorful than [cap]. */
    private fun capped(seed: Int, tone: Double, cap: Double): Int = Tones.tone(seed, tone, min(cap, Tones.chroma(seed)))

    /** A palette from [s], with Material's tones: primary 40/80, containers 90/30, surfaces 98/6. */
    fun build(s: Seeds, dark: Boolean): DesignPalette {
        fun n(tone: Double) = Tones.tone(s.neutral, tone, s.neutralChroma)
        fun nv(tone: Double) = Tones.tone(s.neutral, tone, s.neutralChroma * 2)
        val surface = n(if (dark) 6.0 else 98.0)
        val accent = if (dark) s.accentDark else s.accent
        return DesignPalette(
            dark = dark,
            primary = if (dark) capped(s.primary, 80.0, 40.0) else s.primary,
            onPrimary = if (dark) capped(s.primary, 20.0, 40.0) else WHITE,
            primaryContainer = if (dark) capped(s.primary, 30.0, 40.0) else capped(s.primary, 90.0, 30.0),
            onPrimaryContainer = if (dark) capped(s.primary, 90.0, 30.0) else capped(s.primary, 12.0, 40.0),
            inversePrimary = if (dark) capped(s.primary, 40.0, 48.0) else capped(s.primary, 80.0, 40.0),
            secondary = if (dark) capped(s.secondary, 80.0, 45.0) else capped(s.secondary, 38.0, 48.0),
            onSecondary = if (dark) capped(s.secondary, 20.0, 30.0) else WHITE,
            secondaryContainer = if (dark) capped(s.secondary, 30.0, 35.0) else capped(s.secondary, 90.0, 30.0),
            onSecondaryContainer = if (dark) capped(s.secondary, 90.0, 30.0) else capped(s.secondary, 12.0, 30.0),
            tertiary = if (dark) capped(s.tertiary, 80.0, 40.0) else capped(s.tertiary, 38.0, 40.0),
            onTertiary = if (dark) capped(s.tertiary, 20.0, 30.0) else WHITE,
            tertiaryContainer = if (dark) capped(s.tertiary, 30.0, 30.0) else capped(s.tertiary, 90.0, 24.0),
            onTertiaryContainer = if (dark) capped(s.tertiary, 90.0, 24.0) else capped(s.tertiary, 12.0, 30.0),
            background = surface,
            onBackground = n(if (dark) 90.0 else 10.0),
            surface = surface,
            onSurface = n(if (dark) 90.0 else 10.0),
            surfaceVariant = nv(if (dark) 30.0 else 90.0),
            onSurfaceVariant = nv(if (dark) 80.0 else 30.0),
            inverseSurface = n(if (dark) 90.0 else 20.0),
            inverseOnSurface = n(if (dark) 20.0 else 95.0),
            error = if (dark) c(0xFFB4AB) else c(0xBA1A1A),
            onError = if (dark) c(0x690005) else WHITE,
            errorContainer = if (dark) c(0x93000A) else c(0xFFDAD6),
            onErrorContainer = if (dark) c(0xFFDAD6) else c(0x410002),
            outline = nv(if (dark) 60.0 else 50.0),
            outlineVariant = nv(if (dark) 30.0 else 80.0),
            surfaceBright = n(if (dark) 24.0 else 98.0),
            surfaceDim = n(if (dark) 6.0 else 87.0),
            surfaceContainerLowest = n(if (dark) 4.0 else 100.0),
            surfaceContainerLow = n(if (dark) 10.0 else 96.0),
            surfaceContainer = n(if (dark) 12.0 else 94.0),
            surfaceContainerHigh = n(if (dark) 17.0 else 92.0),
            surfaceContainerHighest = n(if (dark) 22.0 else 90.0),
            tableGrid = nv(if (dark) 32.0 else 79.0),
            tableHeader = capped(s.primary, if (dark) 17.0 else 93.0, 12.0),
            tableDone = Tones.mix(surface, GREEN, if (dark) 0.25 else 0.18),
            tableMissed = Tones.mix(surface, RED, if (dark) 0.22 else 0.15),
            tableToday = Tones.mix(surface, s.accentDark, if (dark) 0.18 else 0.25),
            heroStyle = s.heroStyle,
            hero = if (dark) s.heroDark else s.hero,
            onHero = if (dark) s.onHeroDark else s.onHero,
            accent = accent,
            accentBright = Tones.tone(accent, Tones.lightness(accent) + 10),
            accentText = capped(s.accentDark, if (dark) 78.0 else 42.0, 60.0),
            glow = s.accentDark,
            tint = s.tint,
        )
    }

    /** A design from the phone's own colors: [primary], [secondary] and [tertiary] accents and its [neutral]. */
    fun dynamicSeeds(primary: Int, secondary: Int, tertiary: Int, neutral: Int): Seeds {
        val brand = capped(primary, 36.0, 48.0)
        val hero = intArrayOf(capped(primary, 22.0, 48.0), capped(primary, 32.0, 48.0), capped(primary, 44.0, 48.0))
        return Seeds(
            primary = brand,
            secondary = secondary,
            tertiary = tertiary,
            neutral = neutral,
            neutralChroma = Tones.chroma(neutral).coerceIn(2.0, 8.0),
            accent = capped(tertiary, 85.0, 50.0),
            hero = hero,
        )
    }

    /**
     * «ألوانك»: a design from a chosen [primary] and [accent]. Whatever is chosen, the hero card stays dark
     * enough for white text, and the accent light enough to read on it.
     */
    fun customSeeds(primary: Int, accent: Int, hero: HeroStyle): Seeds {
        val stops = intArrayOf(capped(primary, 20.0, 60.0), capped(primary, 30.0, 60.0), capped(primary, 42.0, 60.0))
        val glow = Tones.tone(accent, max(Tones.lightness(accent), 76.0), min(Tones.chroma(accent), 70.0))
        return Seeds(
            primary = capped(primary, 36.0, 60.0),
            secondary = capped(accent, 45.0, 40.0),
            tertiary = capped(accent, 40.0, 50.0),
            neutral = primary,
            neutralChroma = 4.0,
            accent = glow,
            heroStyle = hero,
            hero = stops,
            tint = stops,
        )
    }

    private val SEEDS: Map<AppDesign, Seeds> = mapOf(
        AppDesign.NIGHT to Seeds(
            primary = c(0x1F3A5F),
            secondary = c(0xC9A227),
            tertiary = c(0x3E6E8E),
            neutralChroma = 5.0,
            accent = c(0xE8C15A),
            heroStyle = HeroStyle.STARS,
            hero = intArrayOf(c(0x0C1F3F), c(0x1B3A66), c(0x2C5389)),
        ),
        AppDesign.PAPER to Seeds(
            primary = c(0x7A5B2E),
            secondary = c(0x9A6B1F),
            tertiary = c(0x5E6B3A),
            neutral = c(0xC8A96A),
            neutralChroma = 10.0,
            accent = c(0xA87618),
            accentDark = c(0xE0B45A),
            heroStyle = HeroStyle.FRAME,
            hero = intArrayOf(c(0xF6ECD5), c(0xF1E4C6), c(0xEBDAB4)),
            heroDark = intArrayOf(c(0x2B2317), c(0x32291A), c(0x3A2F1D)),
            onHero = c(0x3F2F14),
            onHeroDark = c(0xF1E2C0),
            tint = intArrayOf(c(0x4E3A1C), c(0x6B4F27), c(0x85663A)),
        ),
        AppDesign.BURGUNDY to Seeds(
            primary = c(0x7B1E3A),
            secondary = c(0xA9824A),
            tertiary = c(0x8A5A44),
            neutralChroma = 4.0,
            accent = c(0xE3B565),
            heroStyle = HeroStyle.STARS,
            hero = intArrayOf(c(0x4A0F22), c(0x7B1E3A), c(0xA23A55)),
        ),
        AppDesign.TEAL to Seeds(
            primary = c(0x00677D),
            secondary = c(0xB5533C),
            tertiary = c(0x3F7A56),
            neutralChroma = 4.0,
            accent = c(0xFFC98A),
            hero = intArrayOf(c(0x004E5F), c(0x00798F), c(0x1B97AA)),
            tint = intArrayOf(c(0x004A5A), c(0x006070), c(0x007485)),
        ),
        AppDesign.GRAPHITE to Seeds(
            primary = c(0x2F3437),
            secondary = c(0x5B6770),
            tertiary = c(0x2E7D6B),
            neutralChroma = 1.5,
            accent = c(0x6FD3A1),
            heroStyle = HeroStyle.FLAT,
            hero = intArrayOf(c(0x1F2326), c(0x2F3437), c(0x3D4347)),
        ),
    )

    private val EMERALD_HERO = intArrayOf(c(0x0B4F3B), c(0x17795C), c(0x26987C))

    /** The hero's green, a little deeper where small white text sits on it (the «ملوّن» widgets). */
    private val EMERALD_TINT = intArrayOf(c(0x0B4F3B), c(0x125F48), c(0x17795C))

    /** The app's first design, color for color as it has always been. */
    private val EMERALD_LIGHT = DesignPalette(
        dark = false,
        primary = c(0x1E6B52),
        onPrimary = WHITE,
        primaryContainer = c(0xCDEBDD),
        onPrimaryContainer = c(0x00382A),
        inversePrimary = c(0x8BD6B6),
        secondary = c(0x8A6D1F),
        onSecondary = WHITE,
        secondaryContainer = c(0xF6E7BC),
        onSecondaryContainer = c(0x2C2100),
        tertiary = c(0x3B6470),
        onTertiary = WHITE,
        tertiaryContainer = c(0xD6ECF2),
        onTertiaryContainer = c(0x0E333C),
        background = c(0xF8FAF6),
        onBackground = c(0x191C1A),
        surface = c(0xF8FAF6),
        onSurface = c(0x191C1A),
        surfaceVariant = c(0xDCE5DD),
        onSurfaceVariant = c(0x404943),
        inverseSurface = c(0x2E312F),
        inverseOnSurface = c(0xEFF1ED),
        error = c(0xBA1A1A),
        onError = WHITE,
        errorContainer = c(0xFFDAD6),
        onErrorContainer = c(0x410002),
        outline = c(0x707973),
        outlineVariant = c(0xC0C9C1),
        surfaceBright = c(0xF8FAF6),
        surfaceDim = c(0xD8DBD7),
        surfaceContainerLowest = WHITE,
        surfaceContainerLow = c(0xF2F5F0),
        surfaceContainer = c(0xECF0EB),
        surfaceContainerHigh = c(0xE6EAE5),
        surfaceContainerHighest = c(0xE1E4DF),
        tableGrid = c(0xB9C6BE),
        tableHeader = c(0xE3EFE8),
        tableDone = c(0xD8F0E2),
        tableMissed = c(0xFBE3E0),
        tableToday = c(0xFFF4D6),
        heroStyle = HeroStyle.GRADIENT,
        hero = EMERALD_HERO,
        onHero = WHITE,
        accent = c(0xF2C14E),
        accentBright = c(0xFFE08A),
        accentText = c(0xB7791F),
        glow = c(0xF2C14E),
        tint = EMERALD_TINT,
    )

    private val EMERALD_DARK = DesignPalette(
        dark = true,
        primary = c(0x8BD6B6),
        onPrimary = c(0x003828),
        primaryContainer = c(0x00513B),
        onPrimaryContainer = c(0xA7F2D1),
        inversePrimary = c(0x1E6B52),
        secondary = c(0xE2C46F),
        onSecondary = c(0x3D2F00),
        secondaryContainer = c(0x574500),
        onSecondaryContainer = c(0xFFE08F),
        tertiary = c(0xA2CEDB),
        onTertiary = c(0x033640),
        tertiaryContainer = c(0x224C57),
        onTertiaryContainer = c(0xBEEAF7),
        background = c(0x111412),
        onBackground = c(0xE1E3DF),
        surface = c(0x111412),
        onSurface = c(0xE1E3DF),
        surfaceVariant = c(0x404943),
        onSurfaceVariant = c(0xC0C9C1),
        inverseSurface = c(0xE1E3DF),
        inverseOnSurface = c(0x2E312F),
        error = c(0xFFB4AB),
        onError = c(0x690005),
        errorContainer = c(0x93000A),
        onErrorContainer = c(0xFFDAD6),
        outline = c(0x8A938C),
        outlineVariant = c(0x404943),
        surfaceBright = c(0x373A38),
        surfaceDim = c(0x111412),
        surfaceContainerLowest = c(0x0C0F0D),
        surfaceContainerLow = c(0x191C1A),
        surfaceContainer = c(0x1D201E),
        surfaceContainerHigh = c(0x272B29),
        surfaceContainerHighest = c(0x323634),
        tableGrid = c(0x3E4A44),
        tableHeader = c(0x1F2B26),
        tableDone = c(0x1E3B2E),
        tableMissed = c(0x3F2624),
        tableToday = c(0x3A3420),
        heroStyle = HeroStyle.GRADIENT,
        hero = EMERALD_HERO,
        onHero = WHITE,
        accent = c(0xF2C14E),
        accentBright = c(0xFFE08A),
        accentText = c(0xB7791F),
        glow = c(0xF2C14E),
        tint = EMERALD_TINT,
    )
}
