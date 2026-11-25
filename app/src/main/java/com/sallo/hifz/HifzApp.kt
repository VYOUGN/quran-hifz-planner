package com.sallo.hifz

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.sallo.hifz.calendar.CalendarSync
import com.sallo.hifz.data.AppDatabase
import com.sallo.hifz.data.AppPrefs
import com.sallo.hifz.data.BadgeStore
import com.sallo.hifz.data.PlanRepository
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.plan.Badges
import com.sallo.hifz.reminder.Notifications
import com.sallo.hifz.reminder.ReminderScheduler
import com.sallo.hifz.widget.Shortcuts
import com.sallo.hifz.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HifzApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val prefs: AppPrefs by lazy { AppPrefs(this) }
    val badges: BadgeStore by lazy { BadgeStore(this) }

    /** Plans written to the phone's calendar follow every change to their tables. */
    val calendar: CalendarSync by lazy { CalendarSync(this, database) }

    /** Work that outlives a screen: after a change, the badges' check and the launcher shortcuts. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Conflated: a change sent before the check below starts listening still counts.
    private val changes = Channel<Unit>(Channel.CONFLATED)

    val repository: PlanRepository by lazy {
        PlanRepository(database) {
            Widgets.refresh(this)
            calendar.requestSync()
            changes.trySend(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        ReminderScheduler.schedule(this)
        // A burst of changes (ticking several items) makes one check, a moment after the last.
        scope.launch {
            changes.receiveAsFlow().collectLatest {
                delay(400)
                val today = LocalDate.now()
                val facts = repository.allPlans().map { planFacts(it, repository.getSessions(it.id)) }
                badges.update(Badges.input(facts, today), today)
                Shortcuts.refresh(this@HifzApp)
            }
        }
        changes.trySend(Unit)
    }

    /** The phone switched to dark or light: the widgets follow it (Android 12 and later do it by themselves). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Widgets.refresh(this)
    }
}

val Context.hifzApp: HifzApp get() = applicationContext as HifzApp
