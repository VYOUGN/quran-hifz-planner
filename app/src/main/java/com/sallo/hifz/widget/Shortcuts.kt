package com.sallo.hifz.widget

import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sallo.hifz.HifzApp
import com.sallo.hifz.MainActivity
import com.sallo.hifz.R
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.reminder.QuickDone

/**
 * The app icon's long-press menu: «✓ خطتي» ticks today's session, «خطتي» opens the plan, and
 * «خطة جديدة». Up to two plans, the ones not finished first; each can be dragged to the home screen.
 */
object Shortcuts {
    private const val PLANS = 2
    private const val DONE = "done_"
    private const val OPEN = "plan_"
    private const val NEW = "new"

    /** After every change and when the app starts. Android limits how often an app in the background may change them, so only real changes go out. */
    suspend fun refresh(app: HifzApp) {
        try {
            val plans = app.repository.allPlans()
            val unfinished = plans.filterNot { plan ->
                val rows = app.repository.getSessions(plan.id)
                rows.isNotEmpty() && rows.all { it.mainDone(plan) }
            }
            val shown = (unfinished + (plans - unfinished.toSet())).take(PLANS)
            val wanted = buildList {
                shown.forEach { add(done(app, it, size)) }
                shown.forEach { add(open(app, it, size)) }
                if (size < 4) add(newPlan(app, size))
            }
            fun ShortcutInfoCompat.key() = Triple(id, longLabel?.toString(), rank)
            if (ShortcutManagerCompat.getDynamicShortcuts(app).map { it.key() }.toSet() != wanted.map { it.key() }.toSet()) {
                ShortcutManagerCompat.setDynamicShortcuts(app, wanted)
            }
            // Dragged to the home screen: a renamed plan's follow it, a deleted plan's stop working.
            val ids = plans.map { it.id }.toSet()
            val pinned = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            val gone = pinned.filter { s -> planOf(s.id)?.let { it !in ids } == true && s.isEnabled }.map { it.id }
            if (gone.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(app, gone, "الخطة انحذفت")
            val renamed = pinned.mapNotNull { s ->
                val plan = plans.firstOrNull { it.id == planOf(s.id) } ?: return@mapNotNull null
                val now = if (s.id.startsWith(DONE)) done(app, plan, s.rank) else open(app, plan, s.rank)
                now.takeIf { it.longLabel?.toString() != s.longLabel?.toString() && wanted.none { w -> w.id == s.id } }
            }
            if (renamed.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(app, renamed)
        } catch (e: Exception) {
            // Too many changes in the background: the next one after the app opens catches up.
            Log.w("Shortcuts", "update failed", e)
        }
    }

    private fun planOf(id: String): Long? = when {
        id.startsWith(DONE) -> id.removePrefix(DONE).toLongOrNull()
        id.startsWith(OPEN) -> id.removePrefix(OPEN).toLongOrNull()
        else -> null
    }

    private fun done(app: HifzApp, plan: PlanEntity, rank: Int): ShortcutInfoCompat {
        val what = if (plan.toSettings().hasDates) "درس اليوم" else "الجلسة الحالية"
        return ShortcutInfoCompat.Builder(app, DONE + plan.id)
            .setShortLabel("✓ ${plan.name}")
            // The name first: a launcher that cuts the label still says which plan it is.
            .setLongLabel("✓ ${plan.name}: تم $what")
            .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_done))
            .setIntent(QuickDone.shortcutIntent(app, plan.id))
            .setRank(rank)
            .build()
    }

    private fun open(app: HifzApp, plan: PlanEntity, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(app, OPEN + plan.id)
            .setShortLabel(plan.name)
            .setLongLabel(plan.name)
            .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_plan))
            .setIntent(MainActivity.intent(app, plan.id).setAction(Intent.ACTION_VIEW))
            .setRank(rank)
            .build()

    private fun newPlan(app: HifzApp, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(app, NEW)
            .setShortLabel("خطة جديدة")
            .setLongLabel("خطة جديدة")
            .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_new))
            .setIntent(MainActivity.newPlanIntent(app))
            .setRank(rank)
            .build()
}
