package com.sallo.hifz.design

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.sallo.hifz.data.Prefs

/** Designs on this phone: «ألوان جوالك» takes the colors of its wallpaper (Android 12 and later). */
object PhoneDesigns {
    val hasPhoneColors: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** The designs this phone can show. */
    val offered: List<AppDesign> get() = AppDesign.entries.filter { it != AppDesign.DYNAMIC || hasPhoneColors }

    /**
     * [design]'s colors in light or [dark]; without the phone's colors «ألوان جوالك» falls back to emerald.
     * «ألوانك» takes its colors from [prefs].
     */
    fun palette(context: Context, design: AppDesign, dark: Boolean, prefs: Prefs): DesignPalette {
        if (design == AppDesign.CUSTOM) {
            return Palettes.build(Palettes.customSeeds(prefs.customPrimary, prefs.customAccent, prefs.customHero), dark)
        }
        if (design == AppDesign.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
            val seeds = Palettes.dynamicSeeds(
                context.getColor(android.R.color.system_accent1_500),
                context.getColor(android.R.color.system_accent2_500),
                context.getColor(android.R.color.system_accent3_500),
                context.getColor(android.R.color.system_neutral1_500),
            )
            return Palettes.build(seeds, dark)
        }
        return Palettes.of(design, dark)
    }

    /** Whether the phone is in dark mode now. */
    fun phoneIsDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
