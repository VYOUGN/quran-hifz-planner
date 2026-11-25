package com.sallo.hifz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sallo.hifz.R
import com.sallo.hifz.hifzApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The home-screen widgets; each kind is its own entry in the launcher's widget list. */
enum class WidgetKind(val label: String, val hint: String, val layout: Int, val singlePlan: Boolean) {
    LIST("قائمة الخطط", "درس اليوم والمراجعات لين 3 خطط", R.layout.widget_list, false),
    COMPACT("درس اليوم المختصر", "الدرس بخط كبير وشريط التقدم", R.layout.widget_compact, true),
    RING("حلقة التقدم", "نسبة إنجاز الخطة", R.layout.widget_ring, true),
    WEEK("شريط الأسبوع", "أيام الأسبوع ودرس اليوم", R.layout.widget_week, true),
    BOARD("الشامل", "الدرس والمراجعات والأسبوع والتقدم", R.layout.widget_board, true),
    ;

    val provider: Class<out BaseWidgetProvider>
        get() = when (this) {
            LIST -> TodayWidgetProvider::class.java
            COMPACT -> CompactWidgetProvider::class.java
            RING -> RingWidgetProvider::class.java
            WEEK -> WeekWidgetProvider::class.java
            BOARD -> BoardWidgetProvider::class.java
        }
}

/** Draws every placed widget of [kind] away from the main thread. */
abstract class BaseWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.hifzApp
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, WidgetRenderer.build(app, kind, id))
            } catch (e: Exception) {
                Log.w("Widgets", "update of $kind failed", e)
            } finally {
                result.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPlans.forget(context, it) }
    }
}

/** «قائمة الخطط». It keeps the name of the app's first widget, so the ones already placed keep working. */
class TodayWidgetProvider : BaseWidgetProvider(WidgetKind.LIST)

class CompactWidgetProvider : BaseWidgetProvider(WidgetKind.COMPACT)

class RingWidgetProvider : BaseWidgetProvider(WidgetKind.RING)

class WeekWidgetProvider : BaseWidgetProvider(WidgetKind.WEEK)

class BoardWidgetProvider : BaseWidgetProvider(WidgetKind.BOARD)

object Widgets {
    /** Asks every placed widget to draw itself again. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (kind in WidgetKind.entries) {
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
            if (ids.isEmpty()) continue
            context.sendBroadcast(
                Intent(context, kind.provider)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }

    /** Whether the launcher can put a widget on the home screen for the app. */
    fun canPin(context: Context): Boolean = AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    /** Asks the launcher to put [kind] on the home screen; false when it cannot. */
    fun pin(context: Context, kind: WidgetKind): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, kind.provider), null, null)
    }
}

/** The plan a widget shows, when one was chosen for it (0: the app picks today's). */
object WidgetPlans {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun chosen(context: Context, widgetId: Int): Long = prefs(context).getLong("plan_$widgetId", 0L)

    fun choose(context: Context, widgetId: Int, planId: Long) {
        prefs(context).edit().putLong("plan_$widgetId", planId).apply()
    }

    fun forget(context: Context, widgetId: Int) {
        prefs(context).edit().remove("plan_$widgetId").apply()
    }
}
