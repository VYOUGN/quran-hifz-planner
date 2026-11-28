package com.sallo.hifz.data

import com.sallo.hifz.common.PlanText
import com.sallo.hifz.plan.StudyCalendar
import com.sallo.hifz.plan.Tracks
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.quran.Ranges
import java.time.LocalDate

/** How a lesson or a review is studied again («إعادة»). */
enum class RepeatWay {
    /**
     * In its next session: a lesson or a major review takes the next one's place, and the ones after it
     * move one session later; a minor review is added to the next one.
     */
    NEXT_SESSION,

    /** On an extra day before its next session; nothing else moves. */
    EXTRA_DAY,
}

/** What «إعادة» can do for one item of a row: its lesson, its minor review or its major review. */
class RepeatOptions(
    val item: Item,
    /** The plan's main item (the lesson, or the major review of a plan of reviews only), whose sessions set the plan's length. */
    val main: Boolean,
    /** The day of the session [RepeatWay.NEXT_SESSION] puts it in; null without dates. */
    val nextDay: LocalDate?,
    /** The main item: the plan's last session now, and after the sessions move one later. */
    val finishBefore: LocalDate?,
    val finishAfter: LocalDate?,
    /** The minor review: how the next one reads with this one added to it. */
    val merged: String?,
    /** The day for [RepeatWay.EXTRA_DAY], or null when there is none before its next session. */
    val extraDay: LocalDate?,
    /** [extraDay] already has sessions of other tracks, and this joins them. */
    val extraJoins: Boolean,
    /** Why [RepeatWay.NEXT_SESSION] cannot be done, or null when it can. */
    val blocked: String?,
    /** Its next session already goes over it again: it was repeated, or the minor review there takes it in. */
    val repeated: Boolean,
) {
    val possible: Boolean get() = blocked == null || extraDay != null

    /** «الدرس», «المراجعة» (a plan of reviews only), «المراجعة الصغرى» or «المراجعة الكبرى». */
    val noun: String get() = itemNoun(item, main)

    /** A review: the words around [noun] are feminine. */
    val feminine: Boolean get() = item != Item.NEW
}

/** «الدرس», «المراجعة» (the main item of a plan of reviews only), «المراجعة الصغرى» or «المراجعة الكبرى». */
internal fun itemNoun(item: Item, main: Boolean): String = when {
    item == Item.NEW -> "الدرس"
    main -> "المراجعة"
    else -> item.label
}

/**
 * What «تقديم» can do for one item of the next session: bring it to today. The ones after it each
 * take the place of the one before, so a lesson brought forward ends the plan a session earlier.
 */
class AdvanceOptions(
    val item: Item,
    /** The plan's main item, whose sessions set the plan's length. */
    val main: Boolean,
    /** Today already has a session without this item, and it joins it. */
    val joins: Boolean,
    /** The main item: the plan's last session now, and after the sessions move one earlier. */
    val finishBefore: LocalDate?,
    val finishAfter: LocalDate?,
    /** Why it cannot be brought forward, or null when it can. */
    val blocked: String?,
) {
    val noun: String get() = itemNoun(item, main)
    val feminine: Boolean get() = item != Item.NEW
}

/**
 * «احذف من اليوم»: one item of today's session taken out of it. Only today changes: the sessions after
 * it keep what they have, so a lesson taken out is not memorized in this plan.
 */
class RemoveOptions(
    val item: Item,
    val main: Boolean,
    /** Nothing would be left of today's session, so it goes. */
    val sessionGoes: Boolean,
) {
    val noun: String get() = itemNoun(item, main)
    val feminine: Boolean get() = item != Item.NEW
}

/**
 * «إعادة»: a lesson or a review recited with too many mistakes is studied again.
 *
 * The main item moves with what belongs to it: the lesson with its preparation and, when the reviews
 * keep the lessons' days, its minor review (the lessons before each one); in a plan of reviews only,
 * the major review. A major review alongside the lessons moves on its own, through the sessions that
 * have one, and the lessons stay. A minor review is the lessons before its day, so it does not move:
 * the next one takes it in.
 */
object Repeats {
    /** The items of row [at] that can be studied again, in the row's order, with what can be done for each. */
    fun options(plan: PlanEntity, rows: List<SessionEntity>, at: Int, today: LocalDate): List<RepeatOptions> {
        val row = rows.getOrNull(at) ?: return emptyList()
        val c = Repeat(plan, rows, at, today)
        if (c.s.hasDates && row.localDate > today) return emptyList()
        return listOf(Item.NEW, Item.MINOR, Item.MAJOR).filter { c.recites(it, row) }.map { c.options(it) }
    }

    /**
     * The rows from the one after [at] on, as they become when [item] of row [at] is studied again by
     * [way] (renumbered, ids kept), or null when [way] does not fit. The rows up to [at] stay as they are.
     */
    fun apply(plan: PlanEntity, rows: List<SessionEntity>, at: Int, item: Item, way: RepeatWay, today: LocalDate): List<SessionEntity>? {
        val opts = options(plan, rows, at, today).firstOrNull { it.item == item } ?: return null
        val c = Repeat(plan, rows, at, today)
        val out = when (way) {
            RepeatWay.EXTRA_DAY -> c.onExtraDay(item, opts.extraDay ?: return null)
            RepeatWay.NEXT_SESSION -> if (opts.blocked != null) return null else c.inNextSession(item, opts)
        }
        return out.mapIndexed { j, r -> r.copy(idx = c.row.idx + 1 + j) }
    }

    /**
     * «تقديم»: the items of row [at] that can come to today, when it is their next session. A minor
     * review goes with its lesson when the reviews keep the lessons' days, and is not brought on its own:
     * it is the lessons before its day.
     */
    fun advanceOptions(plan: PlanEntity, rows: List<SessionEntity>, at: Int, today: LocalDate): List<AdvanceOptions> {
        val row = rows.getOrNull(at) ?: return emptyList()
        val c = Repeat(plan, rows, at, today)
        return listOf(Item.NEW, Item.MAJOR).filter { c.recites(it, row) }.mapNotNull { c.advanceOptions(it) }
    }

    /** «احذف من اليوم»: the items of row [at] that can be taken out of it, when it is today's session. */
    fun removeOptions(plan: PlanEntity, rows: List<SessionEntity>, at: Int, today: LocalDate): List<RemoveOptions> {
        val row = rows.getOrNull(at) ?: return emptyList()
        val c = Repeat(plan, rows, at, today)
        if (!c.s.hasDates || row.localDate != today) return emptyList()
        return listOf(Item.NEW, Item.MINOR, Item.MAJOR).filter { c.recites(it, row) }
            .map { RemoveOptions(it, it == c.main, sessionGoes = c.removed(it) == null) }
    }

    /** The whole table, renumbered, after [item] is taken out of today's row [at]; null when it cannot. */
    fun remove(plan: PlanEntity, rows: List<SessionEntity>, at: Int, item: Item, today: LocalDate): List<SessionEntity>? {
        if (removeOptions(plan, rows, at, today).none { it.item == item }) return null
        val c = Repeat(plan, rows, at, today)
        val left = c.removed(item)
        val out = rows.toMutableList().apply { if (left == null) removeAt(at) else set(at, left) }
        val base = rows.first().idx
        return out.mapIndexed { i, r -> r.copy(idx = base + i) }
    }

    /** The whole table, renumbered, after [item] of row [at] comes to today; null when it cannot. */
    fun advance(plan: PlanEntity, rows: List<SessionEntity>, at: Int, item: Item, today: LocalDate): List<SessionEntity>? {
        val opts = advanceOptions(plan, rows, at, today).firstOrNull { it.item == item } ?: return null
        if (opts.blocked != null) return null
        return Repeat(plan, rows, at, today).advanced(item)
    }
}

/**
 * The plan's rows after the ones with its main item take [dates] (one per such row, in order), for
 * «ترحيل», «إجازة» and «تأجيل» when every track keeps the plan's days. A review studied again on a day
 * of its own («إعادة») keeps that day, out of any vacation, and the rows are put back in date order
 * around it. Rows are renumbered from the first one.
 */
fun withMainDates(plan: PlanEntity, rows: List<SessionEntity>, dates: List<LocalDate>, today: LocalDate): List<SessionEntity> {
    val calendar = plan.toSettings().calendar
    var k = 0
    val dated = rows.map { r ->
        when {
            r.hasMain(plan) -> dates.getOrNull(k++)?.let { r.copy(date = it.toEpochDay()) } ?: r
            !r.hasProgress() && calendar.vacationOn(r.localDate) != null ->
                r.copy(date = outOfVacation(calendar, maxOf(r.localDate, today)).toEpochDay())
            else -> r
        }
    }
    val base = rows.firstOrNull()?.idx ?: 0
    return dated.sortedBy { it.date }.mapIndexed { i, r -> r.copy(idx = base + i) }
}

/** [day], or the first day after it that is not in a vacation. */
private fun outOfVacation(calendar: StudyCalendar, day: LocalDate): LocalDate {
    var d = day
    while (true) {
        val vacation = calendar.vacationOn(d) ?: return d
        d = vacation.end.plusDays(1)
    }
}

/** Row [at] of [rows] being studied again. */
private class Repeat(val plan: PlanEntity, val rows: List<SessionEntity>, val at: Int, val today: LocalDate) {
    val s = plan.toSettings()
    val separate = s.hasSeparateSchedules
    val row = rows[at]
    val after = rows.drop(at + 1)

    /** The item whose sessions set the plan's length. */
    val main = if (plan.includeNew) Item.NEW else Item.MAJOR

    /** Whether [r] has [item] to recite. */
    fun recites(item: Item, r: SessionEntity): Boolean = when (item) {
        Item.NEW -> plan.includeNew && r.hasLesson
        Item.MINOR -> s.hasMinor && r.has(Item.MINOR) && (Ranges.decode(r.minorRanges).isNotEmpty() || !r.minorText.isNullOrBlank())
        Item.MAJOR -> s.includeMajor && r.has(Item.MAJOR) && (r.majorParts.isNotEmpty() || !r.majorText.isNullOrBlank())
        Item.PREP -> false
    }

    /** Whether [r] is a session of [item], one it can go to next. A major review written by hand may not be written yet. */
    fun isSession(item: Item, r: SessionEntity): Boolean = when {
        item == main -> r.hasMain(plan)
        item == Item.MINOR -> r.has(Item.MINOR)
        item == Item.MAJOR -> r.has(Item.MAJOR) && (!s.hasAutoMajor || recites(Item.MAJOR, r))
        else -> false
    }

    /** The lesson carries its minor review when the reviews keep the lessons' days. */
    fun carriesMinor(item: Item): Boolean = item == Item.NEW && !separate

    fun options(item: Item): RepeatOptions {
        val extra = extraDay(item)
        val joins = extra != null && after.any { it.localDate == extra }
        return when {
            item == main -> {
                val later = after.filter { it.hasMain(plan) }
                val blocked = if (later.any { progress(item, it, carriesMinor(item)) }) {
                    if (item == Item.NEW) "سجّلت تقدّم في دروس بعده، فما أقدر أأخّرها" else "سجّلت تقدّم في مراجعات بعدها، فما أقدر أأخّرها"
                } else {
                    null
                }
                val last = (listOf(row) + later).last()
                val tail = if (s.hasDates) s.calendar.next(last.localDate) else null
                RepeatOptions(
                    item = item,
                    main = true,
                    nextDay = if (s.hasDates) later.firstOrNull()?.localDate ?: tail else null,
                    finishBefore = if (s.hasDates) last.localDate else null,
                    finishAfter = tail,
                    merged = null,
                    extraDay = extra,
                    extraJoins = joins,
                    blocked = blocked,
                    repeated = later.firstOrNull()?.let { same(item, it) } == true,
                )
            }
            item == Item.MAJOR -> {
                val slots = after.filter { isSession(item, it) }
                RepeatOptions(
                    item = item,
                    main = false,
                    nextDay = slots.firstOrNull()?.localDate?.takeIf { s.hasDates },
                    finishBefore = null,
                    finishAfter = null,
                    merged = null,
                    extraDay = extra,
                    extraJoins = joins,
                    blocked = when {
                        slots.isEmpty() -> "ما بعدها مراجعة كبرى في الخطة"
                        slots.any { progress(item, it) } -> "سجّلت تقدّم في مراجعات بعدها، فما أقدر أأخّرها"
                        else -> null
                    },
                    repeated = slots.firstOrNull()?.let { same(item, it) } == true,
                )
            }
            else -> {
                val next = after.firstOrNull { isSession(item, it) }
                val covered = next != null && covers(next)
                val blocked = when {
                    next == null -> "ما بعدها مراجعة صغرى في الخطة"
                    covered -> "الجلسة الجاية تراجع نفس الآيات"
                    progress(item, next) -> "سجّلت المراجعة الصغرى الجاية، فما أقدر أزيد عليها"
                    else -> null
                }
                RepeatOptions(
                    item = item,
                    main = false,
                    nextDay = next?.localDate?.takeIf { s.hasDates },
                    finishBefore = null,
                    finishAfter = null,
                    merged = if (blocked == null && next != null) PlanText.minor(withThisMinor(next)) else null,
                    extraDay = extra,
                    extraJoins = joins,
                    blocked = blocked,
                    repeated = covered,
                )
            }
        }
    }

    /** NEXT_SESSION. */
    fun inNextSession(item: Item, opts: RepeatOptions): List<SessionEntity> = when {
        item == main -> {
            // Each one takes the place of the one after it; the last one goes to a new session.
            val withMinor = carriesMinor(item)
            val slots = after.withIndex().filter { it.value.hasMain(plan) }
            val moved = after.toMutableList()
            slots.forEachIndexed { k, (i, r) -> moved[i] = r.withPart(item, if (k == 0) row else slots[k - 1].value, fresh = k == 0, withMinor) }
            val tracks = when {
                !separate -> Tracks.ALL
                plan.includeNew -> Tracks.NEW or Tracks.PREP
                else -> Tracks.MAJOR
            }
            val tail = blank(tracks).withPart(item, slots.lastOrNull()?.value ?: row, fresh = slots.isEmpty(), withMinor)
                .copy(date = (opts.finishAfter ?: row.localDate).toEpochDay())
            moved.apply { add(positionFor(this, tail.date), tail) }
        }
        item == Item.MAJOR -> {
            // Each one takes the place of the one after it, through the sessions that have a major review.
            val slots = after.withIndex().filter { isSession(item, it.value) }
            val moved = after.toMutableList()
            slots.forEachIndexed { k, (i, r) -> moved[i] = r.withPart(item, if (k == 0) row else slots[k - 1].value, fresh = k == 0) }
            // The last one is past the plan's days. A generated review goes round again, so it can go;
            // one written by hand gets a session after the plan's last.
            val dropped = slots.last().value
            if (!dropped.majorText.isNullOrBlank()) {
                val last = rows.last().localDate
                val day = if (!s.hasDates) last else if (separate) s.trackCalendar(s.majorSchedule).next(last) else s.calendar.next(last)
                val tail = blank(Tracks.MAJOR).withPart(item, dropped, fresh = false).copy(date = day.toEpochDay())
                moved.add(positionFor(moved, tail.date), tail)
            }
            moved
        }
        else -> {
            val j = after.indexOfFirst { isSession(item, it) }
            after.toMutableList().apply { set(j, withThisMinor(get(j))) }
        }
    }

    /** EXTRA_DAY: a new session on [day], or the sessions of other tracks already there take it too. */
    fun onExtraDay(item: Item, day: LocalDate): List<SessionEntity> {
        val out = after.toMutableList()
        val on = out.indexOfFirst { it.localDate == day }
        if (on >= 0) {
            val target = out[on]
            val withMinor = carriesMinor(item) && !target.has(Item.MINOR)
            out[on] = target.withPart(item, row, fresh = true, withMinor).copy(tracks = target.tracks or bits(item, withMinor))
        } else {
            val withMinor = carriesMinor(item)
            val extra = blank(bits(item, withMinor)).withPart(item, row, fresh = true, withMinor).copy(date = day.toEpochDay())
            out.add(positionFor(out, extra.date), extra)
        }
        return out
    }

    /**
     * This row without [item], its day off for it; null when nothing is left to do or written on it
     * (then the session goes).
     */
    fun removed(item: Item): SessionEntity? {
        val left = row.withoutPart(item, withMinor = false)
            .let { if (item == Item.MAJOR) it.copy(tracks = it.tracks and Tracks.MAJOR.inv()) else it }
        val keeps = listOf(Item.NEW, Item.MINOR, Item.MAJOR).any { recites(it, left) } || left.hasProgress()
        return left.takeIf { keeps }
    }

    /** The rows from this one on that are sessions of [item]. */
    private fun slotsFromHere(item: Item): List<Int> = rows.indices.filter { it >= at && isSession(item, rows[it]) }

    /** «تقديم» of [item], or null when this row is not its next session after today. */
    fun advanceOptions(item: Item): AdvanceOptions? {
        if (!s.hasDates || row.localDate <= today) return null
        if (rows.indexOfFirst { it.localDate > today && isSession(item, it) } != at) return null
        val isMain = item == main
        val slots = slotsFromHere(item)
        val blocked = when {
            s.calendar.vacationOn(today) != null -> "اليوم إجازة"
            isMain && missedCount(plan, rows, today) > 0 ->
                if (item == Item.NEW) "فاتتك دروس قبله، رحّلها أول" else "فاتتك مراجعات قبلها، رحّلها أول"
            slots.any { progress(item, rows[it], carriesMinor(item)) } ->
                if (item == Item.NEW) "سجّلت تقدّم في دروس بعده، فما أقدر أقدّمها" else "سجّلت تقدّم في مراجعات بعدها، فما أقدر أقدّمها"
            else -> null
        }
        return AdvanceOptions(
            item = item,
            main = isMain,
            joins = rows.any { it.localDate == today && !recites(item, it) },
            finishBefore = if (isMain) rows[slots.last()].localDate else null,
            finishAfter = if (isMain) slots.getOrNull(slots.size - 2)?.let { rows[it].localDate } ?: today else null,
            blocked = blocked,
        )
    }

    /**
     * The whole table after [item] of this row comes to today: each later session of it takes the one
     * after it, and the last one is left without it. A main item's last session then goes, unless
     * something was recorded or written on it.
     */
    fun advanced(item: Item): List<SessionEntity> {
        val withMinor = carriesMinor(item)
        val slots = slotsFromHere(item)
        val out = rows.toMutableList()
        for (k in 0 until slots.size - 1) {
            out[slots[k]] = rows[slots[k]].withPart(item, rows[slots[k + 1]], fresh = false, withMinor)
        }
        val last = slots.last()
        val emptied = rows[last].withoutPart(item, withMinor)
        if (item == main && !emptied.hasProgress() && emptied.majorText.isNullOrBlank()) out.removeAt(last) else out[last] = emptied
        // Today: a session there without the item takes it, or it gets a session of its own.
        val j = out.indexOfFirst { it.localDate == today && !recites(item, it) }
        if (j >= 0) {
            val target = out[j]
            val minor = withMinor && !target.has(Item.MINOR)
            out[j] = target.withPart(item, row, fresh = true, minor).copy(tracks = target.tracks or bits(item, minor))
        } else {
            val brought = blank(bits(item, withMinor)).withPart(item, row, fresh = true, withMinor).copy(date = today.toEpochDay())
            out.add(positionFor(out, brought.date), brought)
        }
        val base = rows.first().idx
        return out.mapIndexed { i, r -> r.copy(idx = base + i) }
    }

    /** A day free for an extra session: the day after the row (today at the earliest), before the item's next session. */
    private fun extraDay(item: Item): LocalDate? {
        if (!s.hasDates) return null
        val day = maxOf(row.localDate.plusDays(1), today)
        if (s.calendar.vacationOn(day) != null) return null
        val next = after.firstOrNull { isSession(item, it) }
        if (next != null && day >= next.localDate) return null
        return day
    }

    /** Whether [next]'s minor review already goes over this row's. */
    private fun covers(next: SessionEntity): Boolean =
        if (row.minorText != null || next.minorText != null) {
            row.minorText == next.minorText
        } else {
            Ranges.subtract(Ranges.decode(row.minorRanges), Ranges.decode(next.minorRanges)).isEmpty()
        }

    /** [next] with this row's minor review added to its own. */
    private fun withThisMinor(next: SessionEntity): SessionEntity {
        val ranges = Ranges.merge(Ranges.decode(row.minorRanges) + Ranges.decode(next.minorRanges))
        val text = if (row.minorText == null && next.minorText == null) {
            null
        } else {
            listOf(PlanText.minor(row), PlanText.minor(next)).filter { it.isNotBlank() && it != RangeFormatter.NONE }.distinct().joinToString("، ")
        }
        return next.copy(minorRanges = Ranges.encode(ranges), minorText = text)
    }

    /** Whether [r] has this row's [item] again: it was studied again there. */
    private fun same(item: Item, r: SessionEntity): Boolean = when (item) {
        Item.NEW -> r.lessonStart == row.lessonStart && r.lessonEnd == row.lessonEnd && r.newText == row.newText
        Item.MAJOR -> r.majorStart == row.majorStart && r.majorEnd == row.majorEnd && r.majorLead == row.majorLead && r.majorText == row.majorText
        else -> false
    }

    /** Ticks, grades or counts on [item] of [r]: a row that has them stays where it is. */
    private fun progress(item: Item, r: SessionEntity, withMinor: Boolean = false): Boolean = when (item) {
        Item.NEW -> r.newDone || r.newGrade != 0 || r.newMistakes != null || r.newAlerts != null ||
            r.prepDone || r.prepGrade != 0 || (withMinor && progress(Item.MINOR, r))
        Item.MINOR -> r.minorDone || r.minorGrade != 0 || r.minorMistakes != null || r.minorAlerts != null
        Item.MAJOR -> r.majorDone || r.majorGrade != 0 || r.majorMistakes != null || r.majorAlerts != null
        Item.PREP -> r.prepDone || r.prepGrade != 0
    }

    /** The tracks of a session that only has [item]. */
    private fun bits(item: Item, withMinor: Boolean): Int = when (item) {
        Item.NEW -> Tracks.NEW or Tracks.PREP or (if (withMinor) Tracks.MINOR else 0)
        else -> item.bit
    }

    /** An empty row of the plan with [tracks]. */
    private fun blank(tracks: Int) = SessionEntity(
        planId = plan.id,
        idx = row.idx,
        date = row.date,
        lessonStart = -1,
        lessonEnd = -1,
        minorRanges = "",
        tracks = tracks,
    )

    /** Where a row dated [date] goes among [list]: after the rows on or before its day. */
    private fun positionFor(list: List<SessionEntity>, date: Long): Int =
        list.indexOfFirst { it.date > date }.let { if (it < 0) list.size else it }

    /** This row without [item] (and what moves with it); a lesson's tracks go with it, a major review's day stays. */
    private fun SessionEntity.withoutPart(item: Item, withMinor: Boolean): SessionEntity = when (item) {
        Item.NEW -> copy(
            lessonStart = -1,
            lessonEnd = -1,
            newText = null,
            newDone = false,
            newGrade = 0,
            newMistakes = null,
            newAlerts = null,
            prepText = null,
            prepDone = false,
            prepGrade = 0,
            tracks = tracks and (Tracks.NEW or Tracks.PREP).inv(),
        ).let { if (withMinor) it.withoutPart(Item.MINOR, false) else it }
        Item.MINOR -> copy(
            minorRanges = "",
            minorText = null,
            minorDone = false,
            minorGrade = 0,
            minorMistakes = null,
            minorAlerts = null,
            tracks = tracks and Tracks.MINOR.inv(),
        )
        Item.MAJOR -> copy(
            majorStart = null,
            majorEnd = null,
            majorLead = "",
            majorText = null,
            majorDone = false,
            majorGrade = 0,
            majorMistakes = null,
            majorAlerts = null,
        )
        Item.PREP -> this
    }

    /** This row with [src]'s [item] (and what moves with it); [fresh] leaves its ticks, grades and counts behind. */
    private fun SessionEntity.withPart(item: Item, src: SessionEntity, fresh: Boolean, withMinor: Boolean = false): SessionEntity {
        fun <T> keep(value: T, empty: T) = if (fresh) empty else value
        return when (item) {
            Item.NEW -> copy(
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
            ).let { if (withMinor) it.withPart(Item.MINOR, src, fresh) else it }
            Item.MINOR -> copy(
                minorRanges = src.minorRanges,
                minorText = src.minorText,
                minorDone = keep(src.minorDone, false),
                minorGrade = keep(src.minorGrade, 0),
                minorMistakes = keep(src.minorMistakes, null),
                minorAlerts = keep(src.minorAlerts, null),
            )
            Item.MAJOR -> copy(
                majorStart = src.majorStart,
                majorEnd = src.majorEnd,
                majorLead = src.majorLead,
                majorText = src.majorText,
                majorDone = keep(src.majorDone, false),
                majorGrade = keep(src.majorGrade, 0),
                majorMistakes = keep(src.majorMistakes, null),
                majorAlerts = keep(src.majorAlerts, null),
            )
            Item.PREP -> this
        }
    }
}
