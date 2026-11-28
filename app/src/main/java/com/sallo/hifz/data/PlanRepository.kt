package com.sallo.hifz.data

import androidx.room.withTransaction
import com.sallo.hifz.plan.BehindPolicy
import com.sallo.hifz.plan.CatchUp
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.Vacation
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** All reads and writes of plans and their rows. [onChanged] runs after every write. */
class PlanRepository(private val db: AppDatabase, private val onChanged: () -> Unit) {
    private val dao = db.planDao()

    val plans: Flow<List<PlanEntity>> = dao.plansFlow()
    val allSessions: Flow<List<SessionEntity>> = dao.allSessionsFlow()

    fun plan(id: Long): Flow<PlanEntity?> = dao.planFlow(id)
    fun sessions(planId: Long): Flow<List<SessionEntity>> = dao.sessionsFlow(planId)

    suspend fun allPlans(): List<PlanEntity> = dao.plans()
    suspend fun getPlan(id: Long): PlanEntity? = dao.plan(id)
    suspend fun getSessions(planId: Long): List<SessionEntity> = dao.sessions(planId)
    suspend fun getSession(id: Long): SessionEntity? = dao.session(id)

    suspend fun createPlan(settings: PlanSettings, template: PlanTemplate): Long {
        val rows = PlanBuilder.build(settings)
        val id = db.withTransaction {
            val id = dao.insertPlan(settings.toEntity(0, System.currentTimeMillis(), template))
            dao.insertSessions(rows.mapIndexed { i, row -> row.toEntity(id, i) })
            id
        }
        onChanged()
        return id
    }

    /** Name, template label and the prep column: nothing that changes the rows. */
    suspend fun updatePlanInfo(plan: PlanEntity) {
        dao.updatePlan(plan)
        onChanged()
    }

    /**
     * Keeps the first [keep] rows as they are and rebuilds the rest from [settings]. A major review
     * written by hand stays on the same row number.
     */
    suspend fun regenerate(plan: PlanEntity, settings: PlanSettings, template: PlanTemplate, keep: Int) {
        db.withTransaction {
            val old = dao.sessions(plan.id)
            val kept = old.take(keep)
            val drafts = PlanBuilder.extend(
                keptLessons = kept.mapNotNull { it.lessonOrNull },
                lastMajor = kept.lastOrNull { it.majorRange != null }?.majorRange,
                s = settings,
            )
            val handWritten = if (settings.hasAutoMajor) emptyMap() else old.associate { it.idx to it.majorText }
            dao.deleteSessionsFrom(plan.id, keep)
            dao.insertSessions(
                drafts.mapIndexed { j, row ->
                    row.toEntity(plan.id, keep + j).copy(majorText = handWritten[keep + j])
                },
            )
            dao.updatePlan(settings.toEntity(plan.id, plan.createdAt, template))
        }
        onChanged()
    }

    suspend fun updateSession(session: SessionEntity) {
        dao.updateSessions(listOf(session))
        onChanged()
    }

    suspend fun postpone(plan: PlanEntity, fromIdx: Int, newDate: LocalDate) {
        val sessions = dao.sessions(plan.id)
        val settings = plan.toSettings()
        if (settings.hasSeparateSchedules) {
            // Each track has its own days: plan everything from this row again, starting on the new date.
            val lessonRow = sessions.drop(fromIdx).firstOrNull { it.hasLesson } ?: return
            val continued = settings.copy(
                startDate = newDate,
                newPortion = settings.newPortion.copy(resume = settings.newPortion.resumeAt(lessonRow.lesson)),
            )
            regenerate(plan, continued, PlanTemplate.fromName(plan.template), fromIdx)
            return
        }
        val dates = Missed.postpone(sessions.map { it.localDate }, fromIdx, newDate, plan.toSettings().calendar)
        setDates(sessions, dates)
        onChanged()
    }

    /**
     * «ترحيل»: puts the plan's missed lessons back by [method]. [start] is where «كمّلها من جديد» starts
     * (today or the next study day when not given). Returns the rows as they were, for «تراجع», or null
     * when nothing was missed.
     */
    suspend fun catchUp(plan: PlanEntity, method: CatchUp, today: LocalDate, start: LocalDate? = null): List<SessionEntity>? {
        val settings = plan.toSettings()
        if (!settings.hasDates) return null
        val before = dao.sessions(plan.id)
        val main = before.filter { it.hasMain(plan) }
        val dates = main.map { it.localDate }
        val first = Missed.firstMissed(dates, main.map { it.mainDone(plan) }, today) ?: return null
        when (method) {
            CatchUp.MARK_DONE -> dao.updateSessions(main.drop(first).takeWhile { it.localDate < today }.map { it.withMainDone(plan) })
            CatchUp.SHIFT, CatchUp.DAILY -> {
                val moved = if (method == CatchUp.SHIFT) {
                    Missed.shift(dates, first, maxOf(start ?: settings.calendar.firstOnOrAfter(today), today), settings.calendar)
                } else {
                    Missed.daily(dates, first, today)
                }
                if (settings.hasSeparateSchedules) {
                    // Each track has its own days, so the days from today are planned again around the lessons' new days.
                    val r = rescheduleSeparately(settings, before, today, moved.drop(first)) ?: return null
                    db.withTransaction {
                        dao.deleteSessionsFrom(plan.id, r.from)
                        dao.insertSessions(
                            r.kept.mapIndexed { j, row -> row.copy(id = 0, idx = r.from + j) } +
                                r.added.mapIndexed { j, draft -> draft.toEntity(plan.id, r.from + r.kept.size + j) },
                        )
                    }
                } else {
                    setDates(main, moved)
                }
            }
        }
        onChanged()
        return before
    }

    /**
     * «إعادة الدرس»: [edited] (the row as the sheet left it) is saved, and its lesson is studied again
     * by [way]. The row itself was recited, so it counts as done, with its grade and mistakes; left
     * open it would be a missed lesson, and «ترحيل» would move the lessons a second time. Returns the
     * rows as they were, for «تراجع», or null when [way] does not fit.
     */
    suspend fun repeat(plan: PlanEntity, recited: SessionEntity, way: RepeatWay, today: LocalDate): List<SessionEntity>? {
        val edited = recited.withMainDone(plan)
        val before = dao.sessions(plan.id)
        val at = before.indexOfFirst { it.id == edited.id }
        if (at < 0) return null
        val rows = before.toMutableList().apply { set(at, edited) }
        val after = Repeats.apply(plan, rows, at, way, today) ?: return null
        db.withTransaction {
            dao.updateSessions(listOf(edited))
            dao.deleteSessionsFrom(plan.id, edited.idx + 1)
            dao.insertSessions(after)
        }
        onChanged()
        return before
    }

    /** «إجازة»: adds [vacation] to the plan, and moves what is still to do out of it. */
    suspend fun addVacation(plan: PlanEntity, vacation: Vacation, today: LocalDate) =
        changeVacations(plan, vacation.start, today, pullBack = false) { (it + vacation).distinct() }

    /** Takes [vacation] off the plan; what is still to do comes back to its days. */
    suspend fun removeVacation(plan: PlanEntity, vacation: Vacation, today: LocalDate) =
        changeVacations(plan, vacation.start, today, pullBack = true) { it - vacation }

    private suspend fun changeVacations(
        plan: PlanEntity,
        from: LocalDate,
        today: LocalDate,
        pullBack: Boolean,
        change: (List<Vacation>) -> List<Vacation>,
    ) {
        val current = dao.plan(plan.id) ?: return
        val updated = current.copy(vacations = Vacation.encode(change(Vacation.decode(current.vacations)).sortedBy { it.start }))
        db.withTransaction {
            dao.updatePlan(updated)
            val settings = updated.toSettings()
            if (!settings.hasDates) return@withTransaction
            val rows = dao.sessions(plan.id)
            val main = rows.filter { it.hasMain(updated) }
            val dates = main.map { it.localDate }
            val (first, start) = Missed.afterVacation(dates, main.map { it.mainDone(updated) }, from, today, settings.calendar, pullBack)
                ?: return@withTransaction
            if (settings.hasSeparateSchedules) {
                // Each track has its own days: plan everything from that row again, around the vacation.
                postpone(updated, main[first].idx, start)
            } else {
                setDates(main, Missed.shift(dates, first, start, settings.calendar))
            }
        }
        onChanged()
    }

    /** «تراجع»: puts the plan's rows back exactly as they were. */
    suspend fun restore(planId: Long, rows: List<SessionEntity>) {
        db.withTransaction {
            dao.deleteSessionsFrom(planId, 0)
            dao.insertSessions(rows)
        }
        onChanged()
    }

    /** Plans set to move missed lessons by themselves («رحّل تلقائياً»). Returns how many plans moved. */
    suspend fun rescheduleAutomatic(today: LocalDate): Int =
        dao.plans()
            .filter { it.behind == BehindPolicy.AUTO.name && it.scheduleMode != ScheduleMode.ANYTIME.name }
            .count { catchUp(it, CatchUp.SHIFT, today) != null }

    /** Every plan with its rows, for «حفظ نسخة احتياطية». */
    suspend fun exportPlans(): List<PlanBackup> = dao.plans().map { PlanBackup(it, dao.sessions(it.id)) }

    /** «استرجاع نسخة»: the plans of a backup, added to the ones here or [replace]-ing them all. */
    suspend fun importPlans(backups: List<PlanBackup>, replace: Boolean) {
        db.withTransaction {
            if (replace) {
                for (p in dao.plans()) {
                    dao.deleteSessionsFrom(p.id, 0)
                    dao.deletePlan(p.id)
                }
            }
            for (b in backups) {
                val id = dao.insertPlan(b.plan.copy(id = 0))
                dao.insertSessions(b.sessions.map { it.copy(id = 0, planId = id) })
            }
        }
        onChanged()
    }

    suspend fun deletePlan(id: Long) {
        db.withTransaction {
            dao.deleteSessionsFrom(id, 0)
            dao.deletePlan(id)
        }
        onChanged()
    }

    private suspend fun setDates(sessions: List<SessionEntity>, dates: List<LocalDate>) {
        val changed = sessions.mapIndexedNotNull { i, s ->
            val day = dates.getOrNull(i)?.toEpochDay() ?: return@mapIndexedNotNull null
            if (day != s.date) s.copy(date = day) else null
        }
        if (changed.isNotEmpty()) dao.updateSessions(changed)
    }
}
