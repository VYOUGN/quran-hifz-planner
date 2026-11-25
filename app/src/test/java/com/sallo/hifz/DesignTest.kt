package com.sallo.hifz

import com.sallo.hifz.design.AppDesign
import com.sallo.hifz.design.DesignPalette
import com.sallo.hifz.design.HeroStyle
import com.sallo.hifz.design.Palettes
import com.sallo.hifz.design.Tones
import com.sallo.hifz.design.WidgetColors
import com.sallo.hifz.design.WidgetLook
import com.sallo.hifz.ui.design.CUSTOM_SWATCHES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTest {
    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

    private fun assertContrast(what: String, fg: Int, bg: Int, at: Double) {
        val ratio = Tones.contrast(fg, bg)
        assertTrue("$what: ${hex(fg)} on ${hex(bg)} is ${"%.2f".format(ratio)}, wanted $at", ratio >= at - 1e-9)
    }

    /** The designs that need no phone colors, and designs from a few typical phone wallpapers. */
    private val palettes: List<Pair<String, DesignPalette>> = buildList {
        for (d in AppDesign.entries.filter { it != AppDesign.DYNAMIC }) {
            add("$d light" to Palettes.of(d, false))
            add("$d dark" to Palettes.of(d, true))
        }
        val wallpapers = listOf(
            intArrayOf(0xFF3F51B5.toInt(), 0xFF7986CB.toInt(), 0xFFE91E63.toInt(), 0xFF8D8D99.toInt()),
            intArrayOf(0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFF4CAF50.toInt(), 0xFFA39E8E.toInt()),
            intArrayOf(0xFF9E9E9E.toInt(), 0xFF757575.toInt(), 0xFF607D8B.toInt(), 0xFF909090.toInt()),
        )
        for ((i, w) in wallpapers.withIndex()) {
            val seeds = Palettes.dynamicSeeds(w[0], w[1], w[2], w[3])
            add("phone $i light" to Palettes.build(seeds, false))
            add("phone $i dark" to Palettes.build(seeds, true))
        }
    }

    @Test
    fun tonesKeepGraysAndEnds() {
        assertEquals(0xFFFFFFFF.toInt(), Tones.tone(0xFF1E6B52.toInt(), 100.0))
        assertEquals(0xFF000000.toInt(), Tones.tone(0xFF1E6B52.toInt(), 0.0))
        val gray = 0xFF777777.toInt()
        val back = Tones.tone(gray, Tones.lightness(gray))
        for (shift in listOf(16, 8, 0)) assertTrue(kotlin.math.abs((back shr shift and 0xFF) - 0x77) <= 1)
        // A tone keeps its seed's hue: a darker green is still green.
        val green = Tones.tone(0xFF1E6B52.toInt(), 30.0)
        assertTrue((green shr 8 and 0xFF) > (green shr 16 and 0xFF))
        assertEquals(21.0, Tones.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun emeraldIsTheAppAsItWas() {
        val light = Palettes.of(AppDesign.EMERALD, false)
        assertEquals(0xFF1E6B52.toInt(), light.primary)
        assertEquals(0xFFF8FAF6.toInt(), light.surface)
        assertEquals(0xFFF2F5F0.toInt(), light.surfaceContainerLow)
        assertEquals(0xFFB9C6BE.toInt(), light.tableGrid)
        assertEquals(0xFFF2C14E.toInt(), light.accent)
        assertEquals(listOf(0xFF0B4F3B, 0xFF17795C, 0xFF26987C).map { it.toInt() }, light.hero.toList())
        val dark = Palettes.of(AppDesign.EMERALD, true)
        assertEquals(0xFF8BD6B6.toInt(), dark.primary)
        assertEquals(0xFF111412.toInt(), dark.surface)
        assertEquals(0xFF1D201E.toInt(), dark.surfaceContainer)
        assertEquals(0xFF3A3420.toInt(), dark.tableToday)
        // The emerald widget keeps the colors it had before the designs.
        val widget = WidgetColors.of(light, WidgetLook.APP)
        assertEquals(0xFFF2F5F0.toInt(), widget.backgrounds.single())
        assertEquals(0xFF1E6B52.toInt(), widget.title)
        assertEquals(0xFF404943.toInt(), widget.muted)
        assertEquals(0xFF1D201E.toInt(), WidgetColors.of(dark, WidgetLook.APP).backgrounds.single())
    }

    @Test
    fun textReadsOnEveryDesign() {
        for ((name, p) in palettes) textReads(name, p)
    }

    /** «ألوانك»: every main color with every accent, light and dark, the widgets too. */
    @Test
    fun customColorsRead() {
        val (primaries, accents) = CUSTOM_SWATCHES
        for (primary in primaries) {
            for (accent in accents) {
                val seeds = Palettes.customSeeds(primary, accent, HeroStyle.GRADIENT)
                for (dark in listOf(false, true)) {
                    val name = "custom ${hex(primary)}/${hex(accent)} ${if (dark) "dark" else "light"}"
                    val p = Palettes.build(seeds, dark)
                    textReads(name, p)
                    widgetsRead(name, p)
                }
            }
        }
    }

    private fun textReads(name: String, p: DesignPalette) {
        run {
            assertContrast("$name onPrimary", p.onPrimary, p.primary, 4.5)
            assertContrast("$name onPrimaryContainer", p.onPrimaryContainer, p.primaryContainer, 4.5)
            assertContrast("$name onSecondaryContainer", p.onSecondaryContainer, p.secondaryContainer, 4.5)
            assertContrast("$name onTertiaryContainer", p.onTertiaryContainer, p.tertiaryContainer, 4.5)
            assertContrast("$name onSurface", p.onSurface, p.surface, 4.5)
            assertContrast("$name onSurfaceVariant", p.onSurfaceVariant, p.surface, 4.5)
            assertContrast("$name onSurfaceVariant on containers", p.onSurfaceVariant, p.surfaceContainerHigh, 4.5)
            assertContrast("$name primary as text", p.primary, p.surface, 4.5)
            assertContrast("$name error", p.error, p.surface, 4.5)
            assertContrast("$name today's label", p.accentText, p.surface, 3.0)
            // The hero card: its text on the darker stops, and big text and the ring on all of them.
            assertContrast("$name hero text", p.onHero, p.hero[0], 4.5)
            assertContrast("$name hero text", p.onHero, p.hero[1], 4.5)
            assertContrast("$name hero title", p.onHero, p.hero[2], 3.0)
            assertContrast("$name progress ring", p.accent, p.hero[1], 3.0)
        }
    }

    @Test
    fun widgetGradientsMatchTheDesigns() {
        val files = mapOf(
            AppDesign.EMERALD to "emerald",
            AppDesign.NIGHT to "night",
            AppDesign.PAPER to "paper",
            AppDesign.BURGUNDY to "burgundy",
            AppDesign.TEAL to "teal",
            AppDesign.GRAPHITE to "graphite",
        )
        for ((design, name) in files) {
            val xml = java.io.File("src/main/res/drawable/widget_bg_$name.xml").readText()
            fun color(attr: String): Int {
                val hex = Regex("android:$attr=\"#FF([0-9A-Fa-f]{6})\"").find(xml)!!.groupValues[1]
                return (0xFF000000L or hex.toLong(16)).toInt()
            }
            val tint = Palettes.of(design, false).tint
            assertEquals("$name gradient", tint.toList(), listOf(color("startColor"), color("centerColor"), color("endColor")))
        }
    }

    @Test
    fun widgetsReadOnEveryLook() {
        for ((name, p) in palettes) widgetsRead(name, p)
    }

    private fun widgetsRead(name: String, p: DesignPalette) {
        run {
            for (look in WidgetLook.entries) {
                val w = WidgetColors.of(p, look)
                for (bg in w.seenBackgrounds) {
                    val what = "$name $look"
                    assertContrast("$what text", w.text, bg, 4.5)
                    assertContrast("$what muted", Tones.over(w.muted, bg), bg, 4.5)
                    assertContrast("$what title", w.title, bg, 3.0)
                    assertContrast("$what today", Tones.over(w.today, bg), bg, 2.5)
                }
            }
        }
    }
}
