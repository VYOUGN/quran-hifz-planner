package com.sallo.hifz.calendar

import com.sallo.hifz.common.PlanText
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.quran.RangeFormatter
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** How a plan's sessions look in the calendar. */
data class CalendarStyle(
    /** An all-day event on each session's day; otherwise one at [minuteOfDay]. */
    val allDay: Boolean = true,
    /** Start of a timed session, in minutes after midnight. */
    val minuteOfDay: Int = 17 * 60,
    val durationMinutes: Int = 30,
    /** The calendar's own alert, in minutes before a timed session; [NO_ALERT] for none. */
    val alertMinutes: Int = NO_ALERT,
) {
    companion object {
        const val NO_ALERT = -1
        val DURATIONS = listOf(15, 30, 45, 60, 90, 120)
        val ALERTS = listOf(NO_ALERT, 0, 10, 30, 60)
    }
}

/** One table row as a calendar event. Times are epoch milliseconds. */
data class EventDraft(
    val idx: Int,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val timeZone: String,
    val title: String,
    val description: String,
    val alertMinutes: Int,
) {
    /** Changes whenever anything the calendar shows changes. */
    val hash: Int get() = listOf(start, end, allDay, timeZone, title, description, alertMinutes).hashCode()
}

/** The table as calendar events: what each day holds, with the items already done ticked. */
object CalendarEvents {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** One event per row; none for a plan without dates. */
    fun drafts(plan: PlanEntity, rows: List<SessionEntity>, style: CalendarStyle, zone: ZoneId): List<EventDraft> {
        if (plan.scheduleMode == ScheduleMode.ANYTIME.name) return emptyList()
        // The lesson after each row, for the preparation item.
        val next = arrayOfNulls<SessionEntity>(rows.size)
        var upcoming: SessionEntity? = null
        for (i in rows.indices.reversed()) {
            next[i] = upcoming
            if (rows[i].hasLesson) upcoming = rows[i]
        }
        return rows.mapIndexed { i, row -> draft(plan, row, next[i], style, zone) }
    }

    private class Line(val item: Item, val text: String, val done: Boolean)

    /** The items that fall on the row's day and have something to do. */
    private fun lines(plan: PlanEntity, row: SessionEntity, next: SessionEntity?): List<Line> = buildList {
        if (plan.includeNew && row.has(Item.NEW) && row.hasLesson) add(Line(Item.NEW, PlanText.lesson(row), row.newDone))
        if (plan.includeNew && plan.includeMinor && row.has(Item.MINOR)) {
            PlanText.minor(row).takeIf { it != RangeFormatter.NONE }?.let { add(Line(Item.MINOR, it, row.minorDone)) }
        }
        if (plan.includeMajor && row.has(Item.MAJOR)) {
            PlanText.major(row).takeIf { it.isNotBlank() }?.let { add(Line(Item.MAJOR, it, row.majorDone)) }
        }
        if (plan.includeNew && plan.prepEnabled && row.has(Item.PREP)) {
            (row.prepText ?: next?.let { PlanText.lesson(it) })?.let { add(Line(Item.PREP, it, row.prepDone)) }
        }
    }

    private fun draft(plan: PlanEntity, row: SessionEntity, next: SessionEntity?, style: CalendarStyle, zone: ZoneId): EventDraft {
        val lines = lines(plan, row, next)
        val done = lines.isNotEmpty() && lines.all { it.done }
        val title = (if (done) "✓ " else "") + "${plan.name}: ${PlanText.main(row, plan)}"
        val description = buildString {
            lines.forEach { appendLine("${if (it.done) "✓" else "○"} ${it.item.label}: ${it.text}") }
            row.note?.takeIf { it.isNotBlank() }?.let { appendLine("ملاحظة: ${it.trim()}") }
            append("من تطبيق جدول الحفظ")
        }
        val date = row.localDate
        if (style.allDay) {
            // All-day events are stored at midnight UTC.
            val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return EventDraft(row.idx, start, start + DAY_MS, true, "UTC", title, description, CalendarStyle.NO_ALERT)
        }
        val time = LocalTime.of(style.minuteOfDay / 60 % 24, style.minuteOfDay % 60)
        val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return EventDraft(
            row.idx,
            start,
            start + style.durationMinutes * 60_000L,
            false,
            zone.id,
            title,
            description,
            style.alertMinutes,
        )
    }
}
