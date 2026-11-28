package com.sallo.hifz.data

import com.sallo.hifz.plan.Tracks
import java.time.LocalDate

/** How a lesson is studied again («إعادة الدرس»). */
enum class RepeatWay {
    /** In place of the next session; every lesson after it moves one session later. */
    NEXT_SESSION,

    /** On an extra day before the next session; nothing else moves. */
    EXTRA_DAY,
}

/** What «إعادة الدرس» can do for one row. */
class RepeatOptions(
    /** The next session's day, where the lesson goes with [RepeatWay.NEXT_SESSION]. */
    val nextDay: LocalDate?,
    /** The plan's last session before, and after the lessons move one session later. */
    val finishBefore: LocalDate?,
    val finishAfter: LocalDate?,
    /** The free day for [RepeatWay.EXTRA_DAY], or null when the next session leaves no room. */
    val extraDay: LocalDate?,
    /** Why the lesson cannot move the ones after it, or null when it can. */
    val blocked: String?,
)

/**
 * «إعادة الدرس»: a lesson recited with too many mistakes is studied again. The main item moves with
 * what belongs to it: the lesson with its preparation and, when the reviews keep the lessons' days,
 * its minor review; in a plan of reviews only, the major review. Everything else stays on its day.
 */
object Repeats {
    /** What can be done for row [at] of [rows], or null when it is not a session to repeat. */
    fun options(plan: PlanEntity, rows: List<SessionEntity>, at: Int, today: LocalDate): RepeatOptions? {
        val row = rows.getOrNull(at) ?: return null
        val s = plan.toSettings()
        if (!row.hasMain(plan) || (s.hasDates && row.localDate > today)) return null
        val later = rows.drop(at + 1).filter { it.hasMain(plan) }
        val separate = s.hasSeparateSchedules
        val blocked = if (later.any { it.payloadProgress(plan, separate) }) "سجّلت تقدّم في دروس بعده، فما أقدر أأخّرها" else null
        val last = (listOf(row) + later).last()
        return RepeatOptions(
            nextDay = if (s.hasDates) later.firstOrNull()?.localDate ?: appendedDay(plan, last) else null,
            finishBefore = if (s.hasDates) last.localDate else null,
            finishAfter = if (s.hasDates) appendedDay(plan, last) else null,
            extraDay = extraDay(plan, rows, at, today),
            blocked = blocked,
        )
    }

    /**
     * The rows from the one after [at] on, as they become with [way] (renumbered, ids kept), or null
     * when [way] does not fit. The rows up to [at] stay as they are.
     */
    fun apply(plan: PlanEntity, rows: List<SessionEntity>, at: Int, way: RepeatWay, today: LocalDate): List<SessionEntity>? {
        val opts = options(plan, rows, at, today) ?: return null
        val row = rows[at]
        val separate = plan.toSettings().hasSeparateSchedules
        val after = rows.drop(at + 1)
        val again = blank(plan, row, separate).withPayload(row, plan, separate, fresh = true)
        val out: List<SessionEntity> = when (way) {
            RepeatWay.EXTRA_DAY -> {
                val day = opts.extraDay ?: return null
                listOf(again.copy(date = day.toEpochDay())) + after
            }
            RepeatWay.NEXT_SESSION -> {
                if (opts.blocked != null) return null
                // Each lesson takes the place of the one after it; the last one goes to a new session.
                val main = after.withIndex().filter { it.value.hasMain(plan) }
                val payloads = listOf(again) + main.map { it.value }
                val moved = after.toMutableList()
                main.forEachIndexed { k, (i, r) -> moved[i] = r.withPayload(payloads[k], plan, separate, fresh = false) }
                val tail = blank(plan, row, separate).withPayload(payloads.last(), plan, separate, fresh = false)
                    .copy(date = (opts.finishAfter ?: row.localDate).toEpochDay())
                val position = moved.indexOfFirst { it.date > tail.date }.let { if (it < 0) moved.size else it }
                moved.apply { add(position, tail) }
            }
        }
        return out.mapIndexed { j, r -> r.copy(idx = row.idx + 1 + j) }
    }

    /** A day free for an extra session: the day after the lesson (today at the earliest), before the next session. */
    private fun extraDay(plan: PlanEntity, rows: List<SessionEntity>, at: Int, today: LocalDate): LocalDate? {
        val s = plan.toSettings()
        if (!s.hasDates) return null
        val day = maxOf(rows[at].localDate.plusDays(1), today)
        val next = rows.getOrNull(at + 1)?.localDate
        if (next != null && day >= next) return null
        if (s.calendar.vacationOn(day) != null) return null
        return day
    }

    /** The plan's next day for a lesson after [last]. */
    private fun appendedDay(plan: PlanEntity, last: SessionEntity): LocalDate {
        val s = plan.toSettings()
        return if (s.hasDates) s.calendar.next(last.localDate) else last.localDate
    }

    /** An empty row of the plan, on the days of its main track. */
    private fun blank(plan: PlanEntity, from: SessionEntity, separate: Boolean) = SessionEntity(
        planId = plan.id,
        idx = from.idx,
        date = from.date,
        lessonStart = -1,
        lessonEnd = -1,
        minorRanges = "",
        tracks = when {
            !separate -> Tracks.ALL
            plan.includeNew -> Tracks.NEW or Tracks.PREP
            else -> Tracks.MAJOR
        },
    )

    /** Ticks, grades or counts on what would move: a row that has them stays where it is. */
    private fun SessionEntity.payloadProgress(plan: PlanEntity, separate: Boolean): Boolean =
        if (plan.includeNew) {
            newDone || newGrade != 0 || newMistakes != null || newAlerts != null || prepDone || prepGrade != 0 ||
                (!separate && (minorDone || minorGrade != 0 || minorMistakes != null || minorAlerts != null))
        } else {
            majorDone || majorGrade != 0 || majorMistakes != null || majorAlerts != null
        }

    /** This row with [src]'s main item (and what moves with it); [fresh] leaves its ticks, grades and counts behind. */
    private fun SessionEntity.withPayload(src: SessionEntity, plan: PlanEntity, separate: Boolean, fresh: Boolean): SessionEntity {
        fun <T> keep(value: T, empty: T) = if (fresh) empty else value
        return if (plan.includeNew) {
            var r = copy(
                lessonStart = src.lessonStart,
                lessonEnd = src.lessonEnd,
                newText = src.newText,
                newDone = keep(src.newDone, false),
                newGrade = keep(src.newGrade, 0),
                newMistakes = keep(src.newMistakes, null),
                newAlerts = keep(src.newAlerts, null),
                prepText = src.prepText,
                prepDone = keep(src.prepDone, false),
                prepGrade = keep(src.prepGrade, 0),
            )
            if (!separate) {
                r = r.copy(
                    minorRanges = src.minorRanges,
                    minorText = src.minorText,
                    minorDone = keep(src.minorDone, false),
                    minorGrade = keep(src.minorGrade, 0),
                    minorMistakes = keep(src.minorMistakes, null),
                    minorAlerts = keep(src.minorAlerts, null),
                )
            }
            r
        } else {
            copy(
                majorStart = src.majorStart,
                majorEnd = src.majorEnd,
                majorLead = src.majorLead,
                majorText = src.majorText,
                majorDone = keep(src.majorDone, false),
                majorGrade = keep(src.majorGrade, 0),
                majorMistakes = keep(src.majorMistakes, null),
                majorAlerts = keep(src.majorAlerts, null),
            )
        }
    }
}
