package com.sallo.hifz

import com.sallo.hifz.ui.theme.LocalHideNumbers
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.provider.CalendarContract
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import android.graphics.drawable.ColorDrawable
import com.sallo.hifz.data.isDark
import com.sallo.hifz.design.PhoneDesigns
import com.sallo.hifz.ui.AppNav
import com.sallo.hifz.ui.theme.HifzTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Screen to open, from a widget, a notification or a shortcut: a plan, «إحصائياتي» or «خطة جديدة». */
    private val openRoute = MutableStateFlow<String?>(null)

    /** The context Android created for this activity, before the Arabic wrapper. */
    private var originalBase: Context? = null

    override fun attachBaseContext(newBase: Context) {
        originalBase = newBase
        super.attachBaseContext(ArabicLocale.wrap(newBase))
    }

    /**
     * PrintManager only prints from a context whose outer context is an activity. The Arabic
     * wrapper is a separate context, so take the service from the original one.
     */
    fun printManager(): PrintManager =
        (originalBase ?: this).getSystemService(Context.PRINT_SERVICE) as PrintManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRoute.value = intent.route()
        val app = hifzApp
        // Plans set to «رحّل تلقائياً» move their missed lessons when the app opens.
        lifecycleScope.launch { app.repository.rescheduleAutomatic(LocalDate.now()) }
        // Catch up on a calendar update the app was closed before finishing.
        app.calendar.requestSync()
        setContent {
            val prefs by app.prefs.values.collectAsStateWithLifecycle()
            val dark = prefs.theme.isDark(isSystemInDarkTheme())
            // «التصميم»: its colors in the light or dark mode chosen.
            val palette = remember(prefs.design, dark, prefs.customPrimary, prefs.customAccent, prefs.customHero) {
                PhoneDesigns.palette(this@MainActivity, prefs.design, dark, prefs)
            }
            // The status bar icons follow the app's look, not the phone's.
            LaunchedEffect(dark) { followBars(dark) }
            // What shows between screens and while the app starts.
            LaunchedEffect(palette) { window.setBackgroundDrawable(ColorDrawable(palette.background)) }
            // Compose may take the window's configuration, so give it the Arabic locale explicitly too.
            val base = LocalConfiguration.current
            val arabic = remember(base) { ArabicLocale.configuration(base) }
            // «حجم الخط»: on top of the phone's own text size.
            val density = LocalDensity.current
            val scaled = remember(density, prefs.textSize) { Density(density.density, density.fontScale * prefs.textSize.scale) }
            CompositionLocalProvider(
                LocalConfiguration provides arabic,
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides scaled,
            ) {
                HifzTheme(palette, prefs.font) {
                    CompositionLocalProvider(LocalHideNumbers provides prefs.hideNumbers) { AppNav(app, openRoute) }
                }
            }
        }
    }

    private fun followBars(dark: Boolean) {
        val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) SystemBarStyle.dark(darkScrim) else SystemBarStyle.light(lightScrim, darkScrim),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.route()?.let { openRoute.value = it }
    }

    companion object {
        private const val EXTRA_PLAN_ID = "planId"
        private const val EXTRA_SCREEN = "screen"
        private const val SCREEN_OVERVIEW = "overview"
        private const val SCREEN_NEW_PLAN = "new"

        fun intent(context: Context, planId: Long?): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("hifz://plan/${planId ?: 0}")
                if (planId != null) putExtra(EXTRA_PLAN_ID, planId)
            }

        /** «إحصائياتي», from the weekly report. */
        fun overviewIntent(context: Context): Intent = screenIntent(context, SCREEN_OVERVIEW)

        /** «خطة جديدة», from the launcher shortcut. */
        fun newPlanIntent(context: Context): Intent = screenIntent(context, SCREEN_NEW_PLAN).setAction(Intent.ACTION_VIEW)

        private fun screenIntent(context: Context, screen: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("hifz://$screen")
                putExtra(EXTRA_SCREEN, screen)
            }

        /** Where [Intent] asks the app to go: a screen, or a plan. */
        private fun Intent?.route(): String? = when (this?.getStringExtra(EXTRA_SCREEN)) {
            SCREEN_OVERVIEW -> "overview"
            SCREEN_NEW_PLAN -> "editor/0"
            else -> planId()?.let { "table/$it" }
        }

        /** The plan asked for by the widget, a reminder, or a calendar event the app wrote («hifz://plan/3»). */
        private fun Intent?.planId(): Long? {
            if (this == null) return null
            getLongExtra(EXTRA_PLAN_ID, 0L).takeIf { it > 0 }?.let { return it }
            if (action != CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) return null
            val uri = getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI)?.let { Uri.parse(it) } ?: return null
            return uri.lastPathSegment?.toLongOrNull()?.takeIf { uri.scheme == "hifz" && it > 0 }
        }
    }
}

/** The app is Arabic only: Arabic resources and right-to-left layout, with Western digits. */
object ArabicLocale {
    // Not called "locale": inside a Configuration scope that name means Configuration.locale.
    val LOCALE: Locale = Locale.forLanguageTag("ar-SA-u-nu-latn")

    /**
     * Wraps the activity's base context the way AppCompat does: a ContextThemeWrapper with an
     * override configuration. The original context stays underneath, so system services still see
     * the activity, while every window, dialogs included, gets Arabic resources and formats.
     */
    fun wrap(base: Context): Context {
        Locale.setDefault(LOCALE)
        val override = Configuration().also {
            it.setLocale(LOCALE)
            it.setLayoutDirection(LOCALE)
        }
        return ContextThemeWrapper(base, 0).also { it.applyOverrideConfiguration(override) }
    }

    /** [base] with the Arabic locale, for Compose. */
    fun configuration(base: Configuration): Configuration = Configuration(base).also {
        it.setLocale(LOCALE)
        it.setLayoutDirection(LOCALE)
    }
}

/** Finds the activity behind a (possibly wrapped) context. */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
