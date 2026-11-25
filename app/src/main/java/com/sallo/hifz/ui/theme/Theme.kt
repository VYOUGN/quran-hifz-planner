package com.sallo.hifz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sallo.hifz.R
import com.sallo.hifz.data.Item
import com.sallo.hifz.design.AppDesign
import com.sallo.hifz.design.AppFont
import com.sallo.hifz.design.DesignPalette
import com.sallo.hifz.design.HeroStyle
import com.sallo.hifz.design.Palettes
import com.sallo.hifz.plan.GradeLevel

/** Colors of the paper-style table. */
data class TableColors(
    val grid: Color,
    val header: Color,
    val done: Color,
    val missed: Color,
    val today: Color,
)

/** A design's own colors beyond Material's roles: the hero card, and the accent that marks today. */
@Immutable
data class DesignColors(
    val heroStyle: HeroStyle,
    /** Three stops of the hero card's background. */
    val hero: List<Color>,
    /** Text and marks on the hero card. */
    val onHero: Color,
    /** Around today's session and on the hero card (the emerald design's gold). */
    val accent: Color,
    /** The shine of the progress ring. */
    val accentBright: Color,
    /** The accent as text on the design's surfaces. */
    val accentText: Color,
)

private fun tableOf(p: DesignPalette) = TableColors(
    grid = Color(p.tableGrid),
    header = Color(p.tableHeader),
    done = Color(p.tableDone),
    missed = Color(p.tableMissed),
    today = Color(p.tableToday),
)

private fun designOf(p: DesignPalette) = DesignColors(
    heroStyle = p.heroStyle,
    hero = p.hero.map { Color(it) },
    onHero = Color(p.onHero),
    accent = Color(p.accent),
    accentBright = Color(p.accentBright),
    accentText = Color(p.accentText),
)

private fun schemeOf(p: DesignPalette): ColorScheme {
    fun c(argb: Int) = Color(argb)
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(p.primary),
        onPrimary = c(p.onPrimary),
        primaryContainer = c(p.primaryContainer),
        onPrimaryContainer = c(p.onPrimaryContainer),
        inversePrimary = c(p.inversePrimary),
        secondary = c(p.secondary),
        onSecondary = c(p.onSecondary),
        secondaryContainer = c(p.secondaryContainer),
        onSecondaryContainer = c(p.onSecondaryContainer),
        tertiary = c(p.tertiary),
        onTertiary = c(p.onTertiary),
        tertiaryContainer = c(p.tertiaryContainer),
        onTertiaryContainer = c(p.onTertiaryContainer),
        background = c(p.background),
        onBackground = c(p.onBackground),
        surface = c(p.surface),
        onSurface = c(p.onSurface),
        surfaceVariant = c(p.surfaceVariant),
        onSurfaceVariant = c(p.onSurfaceVariant),
        surfaceTint = c(p.primary),
        inverseSurface = c(p.inverseSurface),
        inverseOnSurface = c(p.inverseOnSurface),
        error = c(p.error),
        onError = c(p.onError),
        errorContainer = c(p.errorContainer),
        onErrorContainer = c(p.onErrorContainer),
        outline = c(p.outline),
        outlineVariant = c(p.outlineVariant),
        surfaceBright = c(p.surfaceBright),
        surfaceDim = c(p.surfaceDim),
        surfaceContainerLowest = c(p.surfaceContainerLowest),
        surfaceContainerLow = c(p.surfaceContainerLow),
        surfaceContainer = c(p.surfaceContainer),
        surfaceContainerHigh = c(p.surfaceContainerHigh),
        surfaceContainerHighest = c(p.surfaceContainerHighest),
    )
}

private val EMERALD_LIGHT = Palettes.of(AppDesign.EMERALD, false)

val LocalTableColors = staticCompositionLocalOf { tableOf(EMERALD_LIGHT) }

/** The design's hero card and accent colors. */
val LocalDesign = staticCompositionLocalOf { designOf(EMERALD_LIGHT) }

/** «إخفاء الأرقام»: no percentages, counts or streaks on the home screen and the plan page. */
val LocalHideNumbers = staticCompositionLocalOf { false }

/** Strong and soft color of one track (حفظ، صغرى، كبرى، تحضير). */
data class TrackColor(val main: Color, val soft: Color)

data class TrackPalette(val new: TrackColor, val minor: TrackColor, val major: TrackColor, val prep: TrackColor) {
    fun of(item: Item): TrackColor = when (item) {
        Item.NEW -> new
        Item.MINOR -> minor
        Item.MAJOR -> major
        Item.PREP -> prep
    }
}

// The tracks keep their colors in every design, so a track always looks the same.
private val LightTracks = TrackPalette(
    new = TrackColor(Color(0xFF1F8A5B), Color(0xFFDCF2E6)),
    minor = TrackColor(Color(0xFF1E7891), Color(0xFFDBEFF5)),
    major = TrackColor(Color(0xFFB7741A), Color(0xFFFBEBD3)),
    prep = TrackColor(Color(0xFF6A57B5), Color(0xFFECE7FA)),
)

private val DarkTracks = TrackPalette(
    new = TrackColor(Color(0xFF6FD3A1), Color(0xFF183B2B)),
    minor = TrackColor(Color(0xFF7CC9DE), Color(0xFF16343D)),
    major = TrackColor(Color(0xFFF0B865), Color(0xFF3E2E14)),
    prep = TrackColor(Color(0xFFB8A9F0), Color(0xFF2B2545)),
)

val LocalTracks = staticCompositionLocalOf { LightTracks }

/** Badge color of a grade by its score out of 100: green, teal, amber, or red when it is to be repeated. */
fun gradeColor(score: Int): Color = when {
    score >= 90 -> Color(0xFF2E7D32)
    score >= 75 -> Color(0xFF00796B)
    score >= GradeLevel.PASS -> Color(0xFFB07D00)
    else -> Color(0xFFC62828)
}

/** The typeface of [font], or null for the phone's own. */
@OptIn(ExperimentalTextApi::class)
fun fontFamily(font: AppFont): FontFamily? = when (font) {
    AppFont.SYSTEM -> null
    AppFont.ALMARAI -> FontFamily(
        Font(R.font.almarai_regular, FontWeight.Normal),
        Font(R.font.almarai_bold, FontWeight.Bold),
    )
    // One variable font file, drawn at each weight the app asks for.
    AppFont.NASKH -> FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(R.font.noto_naskh_arabic, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}

/** Material's type scale in [font]; the tall Naskh letters get more room between lines. */
private fun typographyOf(font: AppFont): Typography {
    val base = Typography()
    val family = fontFamily(font) ?: return base
    val lift = if (font == AppFont.NASKH) 1.22f else 1.06f
    fun TextStyle.inFont() = copy(fontFamily = family, lineHeight = if (lineHeight.isSp) (lineHeight.value * lift).sp else lineHeight)
    return Typography(
        displayLarge = base.displayLarge.inFont(),
        displayMedium = base.displayMedium.inFont(),
        displaySmall = base.displaySmall.inFont(),
        headlineLarge = base.headlineLarge.inFont(),
        headlineMedium = base.headlineMedium.inFont(),
        headlineSmall = base.headlineSmall.inFont(),
        titleLarge = base.titleLarge.inFont(),
        titleMedium = base.titleMedium.inFont(),
        titleSmall = base.titleSmall.inFont(),
        bodyLarge = base.bodyLarge.inFont(),
        bodyMedium = base.bodyMedium.inFont(),
        bodySmall = base.bodySmall.inFont(),
        labelLarge = base.labelLarge.inFont(),
        labelMedium = base.labelMedium.inFont(),
        labelSmall = base.labelSmall.inFont(),
    )
}

/** The app in [palette]'s colors and [font]. */
@Composable
fun HifzTheme(palette: DesignPalette, font: AppFont = AppFont.SYSTEM, content: @Composable () -> Unit) {
    val scheme = remember(palette) { schemeOf(palette) }
    val table = remember(palette) { tableOf(palette) }
    val design = remember(palette) { designOf(palette) }
    val typography = remember(font) { typographyOf(font) }
    CompositionLocalProvider(
        LocalTableColors provides table,
        LocalTracks provides if (palette.dark) DarkTracks else LightTracks,
        LocalDesign provides design,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** The first design, emerald, light or dark like the phone. */
@Composable
fun HifzTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = remember(dark) { Palettes.of(AppDesign.EMERALD, dark) }
    HifzTheme(palette, AppFont.SYSTEM, content)
}
