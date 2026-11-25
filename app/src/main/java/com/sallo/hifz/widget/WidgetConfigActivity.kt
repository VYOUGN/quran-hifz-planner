package com.sallo.hifz.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sallo.hifz.ArabicLocale
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.isDark
import com.sallo.hifz.design.PhoneDesigns
import com.sallo.hifz.hifzApp
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.OptionRow
import com.sallo.hifz.ui.theme.HifzTheme
import kotlinx.coroutines.launch

/**
 * Which plan a one-plan widget shows: today's automatically, or one chosen here. It opens when the
 * widget is added (Android 11 and earlier) and from the widget's own settings (Android 12 and later).
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ArabicLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out leaves the widget as it is (or, while adding it, does not add it).
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val app = hifzApp
        lifecycleScope.launch {
            val plans = app.repository.allPlans()
            // With one plan or none there is nothing to choose.
            if (plans.size <= 1) {
                done(widgetId, 0L)
                return@launch
            }
            val chosen = WidgetPlans.chosen(app, widgetId)
            setContent {
                val prefs = app.prefs.values.value
                val dark = prefs.theme.isDark(isSystemInDarkTheme())
                val palette = remember(dark) { PhoneDesigns.palette(this@WidgetConfigActivity, prefs.design, dark, prefs) }
                LaunchedEffect(dark) { followBars(dark) }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HifzTheme(palette, prefs.font) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Chooser(plans, chosen) { done(widgetId, it) }
                        }
                    }
                }
            }
        }
    }

    /** Status bar icons that read on the chooser's background, light or dark like the app. */
    private fun followBars(dark: Boolean) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun done(widgetId: Int, planId: Long) {
        WidgetPlans.choose(this, widgetId, planId)
        // Draw it with its plan right away.
        Widgets.refresh(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun Chooser(plans: List<PlanEntity>, chosen: Long, onPick: (Long) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("أي خطة يعرضها الودجت؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Hint("تقدر تغيّرها بعدين: اضغط على الودجت مطوّلاً ثم إعداداته.")
        OptionRow("تلقائي", "الخطة اللي فيها جلسة اليوم", chosen == 0L) { onPick(0L) }
        plans.forEach { plan -> OptionRow(plan.name, "تعرض هذي الخطة دائماً", chosen == plan.id) { onPick(plan.id) } }
    }
}
