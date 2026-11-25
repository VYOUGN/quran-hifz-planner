package com.sallo.hifz.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sallo.hifz.HifzApp
import com.sallo.hifz.MainActivity
import com.sallo.hifz.R
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.missedCount
import com.sallo.hifz.data.pending
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.hifzApp
import com.sallo.hifz.plan.WeeklyReport
import com.sallo.hifz.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object Notifications {
    const val CHANNEL_ID = "daily_reminder"
    const val WEEKLY_CHANNEL_ID = "weekly_report"
    const val REMINDER_ID = 1
    const val WEEKLY_ID = 2

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تذكير الحفظ", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "تذكير يومي بدرس الحفظ"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(WEEKLY_CHANNEL_ID, "التقرير الأسبوعي", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "ملخص أسبوعك في كل خططك"
            },
        )
    }

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Inexact alarms that re-arm themselves after firing: the daily reminder, a second one later in the
 * day while today's session is still open, one just after midnight that moves the missed lessons
 * of plans set to «رحّلها تلقائياً», and the weekly report.
 */
object ReminderScheduler {
    private const val REMINDER = 7001
    private const val FOLLOW_UP = 7002
    private const val DAY_START = 7003
    private const val WEEKLY = 7004
    const val EXTRA_FOLLOW_UP = "followUp"

    fun schedule(context: Context) {
        val prefs = context.hifzApp.prefs.values.value
        val alarms = context.getSystemService(AlarmManager::class.java)
        val reminder = Intent(context, ReminderReceiver::class.java)
        arm(context, alarms, REMINDER, reminder, prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute)
        arm(
            context,
            alarms,
            FOLLOW_UP,
            Intent(reminder).putExtra(EXTRA_FOLLOW_UP, true),
            prefs.reminderEnabled && prefs.followUpEnabled,
            prefs.followUpHour,
            prefs.followUpMinute,
        )
        arm(context, alarms, DAY_START, Intent(context, DayStartReceiver::class.java), true, 0, 5)
        arm(
            context,
            alarms,
            WEEKLY,
            Intent(context, WeeklyReportReceiver::class.java),
            prefs.weeklyReport,
            prefs.weeklyHour,
            prefs.weeklyMinute,
            prefs.weeklyDay,
        )
    }

    /** Every day at [hour]:[minute], or only on [day] when given. */
    private fun arm(
        context: Context,
        alarms: AlarmManager,
        code: Int,
        intent: Intent,
        on: Boolean,
        hour: Int,
        minute: Int,
        day: DayOfWeek? = null,
    ) {
        val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if (!on) return
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (day != null) next = next.with(TemporalAdjusters.nextOrSame(day))
        if (!next.isAfter(now)) next = next.plusDays(if (day != null) 7 else 1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }
}

/** Just after midnight: plans set to «رحّلها تلقائياً» move their missed lessons, even with the app closed. */
class DayStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.hifzApp
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.repository.rescheduleAutomatic(LocalDate.now()) > 0) app.calendar.syncNow()
                Widgets.refresh(app)
            } finally {
                ReminderScheduler.schedule(app)
                result.finish()
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.hifzApp
        val followUp = intent.getBooleanExtra(ReminderScheduler.EXTRA_FOLLOW_UP, false)
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Plans that move missed lessons by themselves do it before being reported as behind.
                app.repository.rescheduleAutomatic(LocalDate.now())
                TodayReminder.post(app, followUp)
            } finally {
                ReminderScheduler.schedule(app)
                result.finish()
            }
        }
    }
}

/** Today's reminder: what is still open in every plan, with «تم ✓» for up to three of them. */
object TodayReminder {
    private const val MAX_BUTTONS = 3

    // Kept on the notification itself, so an update after «تم ✓» knows what it is updating.
    private const val EXTRA_FOLLOW_UP = "hifz.followUp"
    private const val EXTRA_TICKED = "hifz.ticked"

    /**
     * [followUp]: the second reminder, only about sessions of today still open. [quiet]: an update of
     * the one showing, without a sound, that goes away once nothing is left. [ticked]: plans ticked
     * from it; a plan without dates leaves it then, instead of showing its next session.
     */
    @SuppressLint("MissingPermission")
    suspend fun post(app: HifzApp, followUp: Boolean, quiet: Boolean = false, ticked: Set<Long> = emptySet()) {
        val today = LocalDate.now()
        val lines = mutableListOf<String>()
        val open = mutableListOf<Pair<PlanEntity, SessionEntity>>()
        var firstPlan: Long? = null
        for (plan in app.repository.allPlans()) {
            val s = plan.toSettings()
            val rows = app.repository.getSessions(plan.id)
            val focus = when {
                plan.id in ticked && !s.hasDates -> null
                s.hasDates -> rows.firstOrNull { it.localDate == today && if (s.hasSeparateSchedules) it.pending(plan) else !it.mainDone(plan) }
                else -> rows.firstOrNull { !it.mainDone(plan) }
            }
            val missed = if (s.hasDates && !followUp) missedCount(plan, rows, today) else 0
            if (focus == null && missed == 0) continue
            if (followUp && !s.hasDates) continue
            val parts = mutableListOf<String>()
            if (focus != null) parts += (if (s.hasDates) "${PlanText.dayWord(focus, plan)} اليوم: " else "الجلسة الحالية: ") + PlanText.main(focus, plan)
            if (missed > 0) parts += Plurals.missed(missed)
            lines += "${plan.name}: ${parts.joinToString("، ")}"
            if (firstPlan == null) firstPlan = plan.id
            if (focus != null) open += plan to focus
        }
        val manager = NotificationManagerCompat.from(app)
        if (lines.isEmpty()) {
            if (quiet) manager.cancel(Notifications.REMINDER_ID)
            return
        }
        if (!Notifications.allowed(app)) return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(app, 0, MainActivity.intent(app, if (lines.size == 1) firstPlan else null), flags)
        val builder = NotificationCompat.Builder(app, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (followUp) "لسا ما خلصت جلسة اليوم" else "حان وقت الحفظ")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setSilent(quiet)
            .addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_FOLLOW_UP, followUp)
                    putLongArray(EXTRA_TICKED, ticked.toLongArray())
                },
            )
        // One plan: «تم ✓». Several: a button each, «✓ خطتي».
        open.take(MAX_BUTTONS).forEach { (plan, row) ->
            val label = if (open.size == 1) "تم ✓" else "✓ ${plan.name}"
            val tick = PendingIntent.getBroadcast(app, 0, QuickDone.intent(app, row.id), flags)
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_done, label, tick).build())
        }
        manager.notify(Notifications.REMINDER_ID, builder.build())
    }

    /** After «تم ✓» on [ticked]: the reminder that is showing follows, quietly; one swiped away stays away. */
    suspend fun refresh(app: HifzApp, ticked: Long?) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val showing = manager.activeNotifications.firstOrNull { it.id == Notifications.REMINDER_ID } ?: return
        val extras = showing.notification.extras
        val before = extras.getLongArray(EXTRA_TICKED)?.toSet().orEmpty()
        post(app, extras.getBoolean(EXTRA_FOLLOW_UP), quiet = true, ticked = before + listOfNotNull(ticked))
    }
}

/** «التقرير الأسبوعي»: the last seven days of every plan, on the day and at the time chosen. */
class WeeklyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.hifzApp
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.prefs.values.value.weeklyReport) post(app)
            } finally {
                ReminderScheduler.schedule(app)
                result.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun post(app: HifzApp) {
        val today = LocalDate.now()
        val weeks = app.repository.allPlans().map { WeeklyReport.week(planFacts(it, app.repository.getSessions(it.id)), today) }
        val lines = WeeklyReport.lines(weeks)
        if (lines.isEmpty() || !Notifications.allowed(app)) return
        val open = PendingIntent.getActivity(
            app,
            1,
            MainActivity.overviewIntent(app),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, Notifications.WEEKLY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تقريرك الأسبوعي")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(app).notify(Notifications.WEEKLY_ID, notification)
    }
}

/** Alarms and the widget need refreshing after a reboot, a clock change, or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.schedule(context.applicationContext)
        Widgets.refresh(context.applicationContext)
    }
}
