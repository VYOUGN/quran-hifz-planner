package com.sallo.hifz.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.data.withAllDone
import com.sallo.hifz.hifzApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** «تم ✓» from outside the app: the reminder, the widgets and the launcher shortcuts. */
object QuickDone {
    /** The session «تم ✓» is about in [plan]: today's, or the current one in a plan without dates. */
    fun target(plan: PlanEntity, rows: List<SessionEntity>, today: LocalDate): SessionEntity? =
        if (plan.toSettings().hasDates) rows.firstOrNull { it.localDate == today } else rows.firstOrNull { !it.mainDone(plan) }

    /** Ticks everything of [row] that falls on its day; false when nothing was left to tick. */
    suspend fun mark(app: HifzApp, plan: PlanEntity, row: SessionEntity): Boolean {
        val done = row.withAllDone(plan)
        if (done == row) return false
        app.repository.updateSession(done)
        return true
    }

    /** What the reminder's button and a widget's circle send: the session they showed. */
    fun intent(context: Context, sessionId: Long): Intent =
        Intent(context, DoneReceiver::class.java).setData(Uri.parse("hifz://done/$sessionId"))

    /** What a launcher shortcut opens: today's session of [planId], whichever it is when tapped. */
    fun shortcutIntent(context: Context, planId: Long): Intent =
        Intent(context, DoneActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("hifz://done-today/$planId"))

    /** Ticks today's session of [planId] and says what happened, for the shortcut's toast. */
    suspend fun markToday(app: HifzApp, planId: Long, today: LocalDate): String {
        val plan = app.repository.getPlan(planId) ?: return "ما لقيت الخطة، يمكن انحذفت"
        val rows = app.repository.getSessions(planId)
        val row = target(plan, rows, today)
            ?: return if (rows.isNotEmpty() && rows.all { it.mainDone(plan) }) "${plan.name}: اكتملت الخطة" else "${plan.name}: ما فيه درس اليوم"
        val what = if (plan.toSettings().hasDates) "${PlanText.dayWord(row, plan)} اليوم" else "الجلسة"
        val marked = mark(app, plan, row)
        TodayReminder.refresh(app, ticked = plan.id)
        return if (marked) "${plan.name}: علّمت $what ✓" else "${plan.name}: سبق وعلّمت $what ✓"
    }
}

/** «تم ✓» on the reminder or a widget («hifz://done/<session>»). */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val app = context.hifzApp
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val row = app.repository.getSession(id)
                val plan = row?.let { app.repository.getPlan(it.planId) }
                if (row != null && plan != null) QuickDone.mark(app, plan, row)
                // The reminder that is showing follows; one already swiped away stays away.
                TodayReminder.refresh(app, ticked = plan?.id)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * A launcher shortcut's «✓ خطتي»: ticks today's session of its plan, says so in a toast, and is
 * gone. It has no window of its own and runs in a task of its own, so the app stays as it was.
 */
class DoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planId = intent?.data?.lastPathSegment?.toLongOrNull()
        if (planId == null || savedInstanceState != null) {
            finish()
            return
        }
        val app = hifzApp
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) { QuickDone.markToday(app, planId, LocalDate.now()) }
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
