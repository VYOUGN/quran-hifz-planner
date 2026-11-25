package com.sallo.hifz.data

import com.sallo.hifz.plan.BehindPolicy
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.plan.MajorMode
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.MinorMode
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.Rounding
import com.sallo.hifz.plan.RowDraft
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.DayRow
import com.sallo.hifz.plan.MistakeRow
import com.sallo.hifz.plan.PlanFacts
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.plan.TrackSchedule
import com.sallo.hifz.plan.Vacation
import com.sallo.hifz.quran.Ranges
import java.time.DayOfWeek
import java.time.LocalDate

fun weekdaysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl day.value) }

fun maskToWeekdays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl it.value) != 0 }.toSet()

private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

fun PlanEntity.toSettings(): PlanSettings = PlanSettings(
    name = name,
    startDate = LocalDate.ofEpochDay(startDate),
    scheduleMode = enumOr(scheduleMode, ScheduleMode.WEEKDAYS),
    weekdays = maskToWeekdays(weekdaysMask),
    every = intervalDays,
    endDate = endDate?.let { LocalDate.ofEpochDay(it) },
    includeNew = includeNew,
    newPortion = PortionSpec(
        unit = enumOr(unit, MemUnit.AYAH),
        amount = newAmount,
        range = RangeSpec(enumOr(newRangeType, RangeType.WHOLE), newFrom, newTo, newFromAyah, newToAyah),
        rounding = enumOr(newRounding, Rounding.NEAREST),
        resume = resumeIndex,
        completeSurah = newCompleteSurah,
    ),
    includeMinor = includeMinor,
    minorMode = enumOr(minorMode, MinorMode.LAST_N),
    minorCount = minorCount,
    includeMajor = includeMajor,
    majorMode = when {
        !majorAuto -> MajorMode.MANUAL
        majorMemorized -> MajorMode.MEMORIZED
        else -> MajorMode.RANGE
    },
    majorPortion = PortionSpec(
        unit = enumOr(majorUnit, MemUnit.PAGE),
        amount = majorAmount,
        range = RangeSpec(enumOr(majorRangeType, RangeType.JUZ), majorFrom, majorTo, majorFromAyah, majorToAyah),
        rounding = enumOr(majorRounding, Rounding.NEAREST),
        resume = majorResume,
        completeSurah = majorCompleteSurah,
    ),
    majorPrior = majorPrior,
    majorWrap = majorWrap,
    gradeScale = GradeScale.fromName(gradeScale),
    prepEnabled = prepEnabled,
    behind = enumOr(behind, BehindPolicy.ASK),
    separateSchedules = separateSchedules,
    minorSchedule = TrackSchedule(enumOr(minorScheduleMode, ScheduleMode.WEEKDAYS), maskToWeekdays(minorWeekdaysMask), minorEvery),
    majorSchedule = TrackSchedule(enumOr(majorScheduleMode, ScheduleMode.WEEKDAYS), maskToWeekdays(majorWeekdaysMask), majorEvery),
    vacations = Vacation.decode(vacations),
)

fun PlanSettings.toEntity(id: Long, createdAt: Long, template: PlanTemplate): PlanEntity {
    val n = newPortion
    val m = majorPortion
    return PlanEntity(
        id = id,
        name = name.trim(),
        createdAt = createdAt,
        template = template.name,
        startDate = startDate.toEpochDay(),
        scheduleMode = scheduleMode.name,
        weekdaysMask = weekdaysToMask(weekdays),
        intervalDays = every,
        unit = n.unit.name,
        amount = n.amount.toInt(),
        fromSurah = 1,
        fromAyah = 1,
        toSurah = 1,
        toAyah = 1,
        fromPage = 1,
        toPage = 1,
        resumeIndex = n.resume,
        minorMode = minorMode.name,
        minorCount = minorCount,
        prepEnabled = prepEnabled,
        includeNew = includeNew,
        includeMinor = includeMinor,
        includeMajor = includeMajor,
        newRangeType = n.range.type.name,
        newFrom = n.range.from,
        newTo = n.range.to,
        newFromAyah = n.range.fromAyah,
        newToAyah = n.range.toAyah,
        newAmount = n.amount,
        newRounding = n.rounding.name,
        endDate = endDate?.toEpochDay(),
        majorAuto = majorMode != MajorMode.MANUAL,
        majorUnit = m.unit.name,
        majorAmount = m.amount,
        majorRangeType = m.range.type.name,
        majorFrom = m.range.from,
        majorTo = m.range.to,
        majorFromAyah = m.range.fromAyah,
        majorToAyah = m.range.toAyah,
        majorRounding = m.rounding.name,
        majorResume = m.resume,
        behind = behind.name,
        newCompleteSurah = n.completeSurah,
        majorCompleteSurah = m.completeSurah,
        majorMemorized = majorMode == MajorMode.MEMORIZED,
        majorPrior = majorPrior,
        gradeScale = gradeScale.name,
        separateSchedules = separateSchedules,
        minorScheduleMode = minorSchedule.mode.name,
        minorWeekdaysMask = weekdaysToMask(minorSchedule.weekdays),
        minorEvery = minorSchedule.every,
        majorScheduleMode = majorSchedule.mode.name,
        majorWeekdaysMask = weekdaysToMask(majorSchedule.weekdays),
        majorEvery = majorSchedule.every,
        majorWrap = majorWrap,
        vacations = Vacation.encode(vacations),
    )
}

fun RowDraft.toEntity(planId: Long, idx: Int): SessionEntity = SessionEntity(
    planId = planId,
    idx = idx,
    date = date.toEpochDay(),
    lessonStart = lesson?.first ?: -1,
    lessonEnd = lesson?.last ?: -1,
    minorRanges = Ranges.encode(minor),
    majorStart = major?.first,
    majorEnd = major?.last,
    tracks = tracks,
    majorLead = Ranges.encode(majorLead),
)

/**
 * Whether the row has the plan's main item: its new lesson, or the major review in a review-only plan.
 * A review-only day of a plan whose reviews have their own days has none.
 */
fun SessionEntity.hasMain(plan: PlanEntity): Boolean = if (plan.includeNew) hasLesson else true

/** Whether the row's main item is done; a row without one has nothing left of it. */
fun SessionEntity.mainDone(plan: PlanEntity): Boolean = when {
    !hasMain(plan) -> true
    plan.includeNew -> newDone
    else -> majorDone
}

/** The row with its main item ticked. */
fun SessionEntity.withMainDone(plan: PlanEntity): SessionEntity = if (plan.includeNew) copy(newDone = true) else copy(majorDone = true)

/** Main items whose day passed without being done (only rows that have one count). */
fun missedCount(plan: PlanEntity, rows: List<SessionEntity>, today: LocalDate): Int {
    val main = rows.filter { it.hasMain(plan) }
    return Missed.count(main.map { it.localDate }, main.map { it.mainDone(plan) }, today)
}

/** What «ترحيل» would do to a plan, for the choices it offers. */
class CatchUpOptions(
    /** The missed rows: the plan's main item not done, after the last one that is, in order. */
    val missed: List<SessionEntity>,
    private val dates: List<LocalDate>,
    private val first: Int,
    private val calendar: com.sallo.hifz.plan.StudyCalendar,
    /** Where «كمّلها من جديد» starts unless another day is chosen: today or the next study day. */
    val shiftStart: LocalDate,
    /** The last day of «عوّضها بجلسة كل يوم»; null when that would not get the plan back onto its dates. */
    val dailyUntil: LocalDate?,
) {
    /** The plan's last session now. */
    val end: LocalDate get() = dates.last()

    /** The plan's last session after starting the missed lessons again on [start]. */
    fun shiftEnd(start: LocalDate): LocalDate = Missed.shift(dates, first, start, calendar).last()
}

/** The plan's choices for «ترحيل», or null when no lesson was missed. */
fun catchUpOptions(plan: PlanEntity, rows: List<SessionEntity>, today: LocalDate): CatchUpOptions? {
    val s = plan.toSettings()
    if (!s.hasDates) return null
    val main = rows.filter { it.hasMain(plan) }
    val dates = main.map { it.localDate }
    val first = Missed.firstMissed(dates, main.map { it.mainDone(plan) }, today) ?: return null
    return CatchUpOptions(
        missed = main.drop(first).takeWhile { it.localDate < today },
        dates = dates,
        first = first,
        calendar = s.calendar,
        shiftStart = s.calendar.firstOnOrAfter(today),
        dailyUntil = Missed.dailyUntil(dates, first, today),
    )
}

/** Whether something that falls on the row's day is still to do. */
fun SessionEntity.pending(plan: PlanEntity): Boolean {
    if (plan.includeNew && has(Item.NEW) && hasLesson && !newDone) return true
    if (plan.includeNew && plan.includeMinor && has(Item.MINOR) && Ranges.decode(minorRanges).isNotEmpty() && !minorDone) return true
    if (plan.includeMajor && has(Item.MAJOR) && (majorRange != null || !majorText.isNullOrBlank()) && !majorDone) return true
    return false
}

/**
 * The row with everything of its day ticked that [pending] counts, and the preparation: what «تم ✓»
 * does from the notification, the widgets and the shortcuts. A major review not written yet stays.
 */
fun SessionEntity.withAllDone(plan: PlanEntity): SessionEntity {
    var s = this
    if (plan.includeNew && has(Item.NEW) && hasLesson) s = s.copy(newDone = true)
    if (plan.includeNew && plan.includeMinor && has(Item.MINOR) && Ranges.decode(minorRanges).isNotEmpty()) s = s.copy(minorDone = true)
    if (plan.includeMajor && has(Item.MAJOR) && (majorRange != null || !majorText.isNullOrBlank())) s = s.copy(majorDone = true)
    if (plan.includeNew && plan.prepEnabled && has(Item.PREP)) s = s.copy(prepDone = true)
    return s
}

/** The row's main range, for progress and statistics. */
fun SessionEntity.mainRange(plan: PlanEntity): IntRange? = if (plan.includeNew) lessonOrNull else majorRange

/**
 * A plan's statistics from its main track. With dates, a study day that passed with no session
 * breaks the streak.
 */
fun planStats(plan: PlanEntity, rows: List<SessionEntity>, today: LocalDate): PlanStats {
    val settings = plan.toSettings()
    return Stats.compute(
        rows.mapNotNull { r -> r.mainRange(plan)?.let { StatRow(r.localDate, it, r.mainDone(plan)) } },
        today,
        settings.calendar.takeIf { settings.hasDates },
    )
}

/** What the numbers across plans need from [plan] ([PlanFacts]): its main track, its days, its mistakes. */
fun planFacts(plan: PlanEntity, rows: List<SessionEntity>): PlanFacts {
    val settings = plan.toSettings()
    return PlanFacts(
        planId = plan.id,
        name = plan.name,
        memorizes = plan.includeNew,
        stats = rows.mapNotNull { r -> r.mainRange(plan)?.let { StatRow(r.localDate, it, r.mainDone(plan)) } },
        days = if (settings.hasDates) {
            rows.map { DayRow(it.localDate, done = !it.pending(plan), partly = it.newDone || it.minorDone || it.majorDone || it.prepDone) }
        } else {
            emptyList()
        },
        calendar = settings.calendar.takeIf { settings.hasDates },
        mistakes = rows.mapNotNull { r ->
            val items = Item.entries.filter { r.mistakes(it) != null || r.alerts(it) != null }
            if (items.isEmpty()) {
                null
            } else {
                MistakeRow(
                    planId = plan.id,
                    planName = plan.name,
                    idx = r.idx,
                    date = r.localDate,
                    lesson = com.sallo.hifz.common.PlanText.main(r, plan),
                    mistakes = items.sumOf { r.mistakes(it) ?: 0 },
                    alerts = items.sumOf { r.alerts(it) ?: 0 },
                    items = items.size,
                )
            }
        },
    )
}
