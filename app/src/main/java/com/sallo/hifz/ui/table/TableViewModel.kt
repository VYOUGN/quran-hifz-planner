package com.sallo.hifz.ui.table

import com.sallo.hifz.data.AdvanceOptions
import com.sallo.hifz.data.RemoveOptions
import com.sallo.hifz.data.RepeatOptions
import com.sallo.hifz.data.RepeatWay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.PlanText
import androidx.compose.runtime.Immutable
import com.sallo.hifz.plan.GradeLevel
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.hasMain
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.mainRange
import com.sallo.hifz.data.missedCount
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.plan.Achievements
import com.sallo.hifz.plan.JuzDone
import com.sallo.hifz.plan.PlanFacts
import com.sallo.hifz.data.planStats
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.quran.RangeParser
import com.sallo.hifz.quran.Ranges
import com.sallo.hifz.plan.CatchUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One cell of a row, ready to draw. Immutable, so rows that did not change are skipped when redrawing. */
@Immutable
data class ItemUi(
    val item: Item,
    val text: String,
    val generated: String,
    /** Page numbers under the text, or null. */
    val sub: String?,
    val done: Boolean,
    val grade: GradeLevel?,
    /** Nothing to do: no minor review yet, or a hand-written major review not written yet. */
    val empty: Boolean,
    /** Empty and waiting to be written by hand (a major review the user writes). */
    val writable: Boolean = false,
    /** The track does not fall on this day (its own days differ); drawn as a blank cell. */
    val offDay: Boolean = false,
    /** Mistakes and alerts recorded while reciting it, or null. */
    val mistakes: Int? = null,
    val alerts: Int? = null,
)

/** A change to a plan's rows and how they were before it, so it can be undone. */
class Undoable(val message: String, val planId: Long, val rows: List<SessionEntity>)

/** A table row ready to draw. */
@Immutable
data class RowUi(
    val session: SessionEntity,
    val index: Int,
    val date: LocalDate,
    val hasDate: Boolean,
    val weekday: String,
    val dateShort: String,
    /** «الأحد 11/8», or «الجلسة 3» without dates. */
    val label: String,
    val hijriShort: String?,
    val items: List<ItemUi>,
    val isToday: Boolean,
    val isPast: Boolean,
    val mainDone: Boolean,
    val allDone: Boolean,
    val doneFraction: Float,
) {
    fun item(i: Item): ItemUi? = items.firstOrNull { it.item == i }
    val missed: Boolean get() = hasDate && isPast && !mainDone
}

/** A line of the cards view's timeline: a week header or a session. */
@Immutable
sealed interface TimelineEntry {
    val key: String
}

@Immutable
data class WeekEntry(val number: Int, val done: Int, val total: Int) : TimelineEntry {
    override val key: String get() = "week$number"
}

@Immutable
data class SessionEntry(val row: RowUi) : TimelineEntry {
    override val key: String get() = "row${row.session.id}"
}

@Immutable
data class TableState(
    val loaded: Boolean = false,
    val deleted: Boolean = false,
    val plan: PlanEntity? = null,
    val settings: PlanSettings? = null,
    val description: String = "",
    val rows: List<RowUi> = emptyList(),
    /** The rows grouped by week, worked out away from the main thread. */
    val entries: List<TimelineEntry> = emptyList(),
    /** Rows whose main item (or, on a review-only day, every review) is done. */
    val doneRows: Int = 0,
    val stats: PlanStats? = null,
    /** Average grade of the main track, in the plan's grading system. */
    val averageGrade: GradeLevel? = null,
    val missed: Int = 0,
    /** Today's session, or the next one still to do. */
    val focus: Int? = null,
    val showHijri: Boolean = true,
    val cardsView: Boolean = true,
    /** What the calendar of days, the mistakes and the badges need ([PlanFacts]). */
    val facts: PlanFacts? = null,
    /** The juz memorized whole in this plan. */
    val juzDone: List<JuzDone> = emptyList(),
)

class TableViewModel(private val app: HifzApp, planId: Long) : ViewModel() {
    private val repo = app.repository

    val state: StateFlow<TableState> =
        combine(repo.plan(planId), repo.sessions(planId), app.prefs.values) { plan, sessions, prefs ->
            if (plan == null) {
                TableState(loaded = true, deleted = true)
            } else {
                build(plan, sessions, prefs.showHijri).copy(cardsView = prefs.cardsView)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TableState())

    fun toggle(row: SessionEntity, item: Item) {
        viewModelScope.launch { repo.updateSession(row.withDone(item, !row.done(item))) }
    }

    fun save(row: SessionEntity) {
        viewModelScope.launch { repo.updateSession(row) }
    }

    fun postpone(fromIdx: Int, date: LocalDate) {
        val plan = state.value.plan ?: return
        viewModelScope.launch { repo.postpone(plan, fromIdx, date) }
    }

    private val _undo = MutableSharedFlow<Undoable>(extraBufferCapacity = 1)

    /** Changes that can be taken back from the snackbar. */
    val undo: SharedFlow<Undoable> = _undo.asSharedFlow()

    /** «ترحيل»: puts the missed lessons back by [method] ([start]: where «كمّلها من جديد» starts). */
    fun catchUp(method: CatchUp, start: LocalDate?) {
        val plan = state.value.plan ?: return
        viewModelScope.launch {
            val before = repo.catchUp(plan, method, LocalDate.now(), start) ?: return@launch
            val message = when (method) {
                CatchUp.SHIFT -> "رحّلنا الدروس الفائتة"
                CatchUp.DAILY -> "صارت جلسة كل يوم لين ترجع لجدولك"
                CatchUp.MARK_DONE -> "تعلّمت الدروس كمنجزة"
            }
            _undo.emit(Undoable(message, plan.id, before))
        }
    }

    /** «إعادة»: saves [edited] and studies the item of [option] again by [way]. */
    fun repeat(edited: SessionEntity, option: RepeatOptions, way: RepeatWay) {
        val plan = state.value.plan ?: return
        viewModelScope.launch {
            val before = repo.repeat(plan, edited, option.item, way, LocalDate.now()) ?: return@launch
            val extra = option.extraDay
            val message = when {
                way == RepeatWay.EXTRA_DAY && option.extraJoins && extra != null -> "أضفنا إعادة ${option.noun} على جلسة ${DateText.weekday(extra)}"
                way == RepeatWay.EXTRA_DAY -> "أضفنا جلسة لإعادة ${option.noun}"
                option.item == Item.NEW -> "صار الدرس في الجلسة الجاية، والدروس بعده تأخّرت جلسة"
                option.item == Item.MINOR -> "زدناها على المراجعة الصغرى في الجلسة الجاية"
                else -> "صارت ${option.noun} في الجلسة الجاية، واللي بعدها تأخّرت جلسة"
            }
            _undo.emit(Undoable(message, plan.id, before))
        }
    }

    /** «تقديم»: saves [edited] (the next session) and brings the item of [option] to today. */
    fun advance(edited: SessionEntity, option: AdvanceOptions) {
        val plan = state.value.plan ?: return
        viewModelScope.launch {
            val before = repo.advance(plan, edited, option.item, LocalDate.now()) ?: return@launch
            val message = when {
                option.item == Item.NEW -> "صار الدرس الجاي اليوم، والدروس بعده تقدّمت جلسة"
                else -> "صارت ${option.noun} الجاية اليوم، واللي بعدها تقدّمت جلسة"
            }
            _undo.emit(Undoable(message, plan.id, before))
        }
    }

    /** «احذف من اليوم»: takes the item of [option] out of today's session [edited]. */
    fun remove(edited: SessionEntity, option: RemoveOptions) {
        val plan = state.value.plan ?: return
        viewModelScope.launch {
            val before = repo.remove(plan, edited, option.item, LocalDate.now()) ?: return@launch
            val message = if (option.sessionGoes) "حذفنا جلسة اليوم" else "شلنا ${option.noun} من جلسة اليوم"
            _undo.emit(Undoable(message, plan.id, before))
        }
    }

    fun undo(change: Undoable) {
        viewModelScope.launch { repo.restore(change.planId, change.rows) }
    }

    fun setCardsView(on: Boolean) {
        app.prefs.update { it.copy(cardsView = on) }
    }

    /** Hijri dates are slow to work out, and the same for every redraw. Only used on the flow's thread. */
    private val hijriCache = HashMap<LocalDate, String>()
    private var hijriOffset = Hijri.offsetDays

    private fun build(plan: PlanEntity, sessions: List<SessionEntity>, showHijri: Boolean): TableState {
        if (hijriOffset != Hijri.offsetDays) {
            // «تعديل التاريخ الهجري» changed.
            hijriCache.clear()
            hijriOffset = Hijri.offsetDays
        }
        val s = plan.toSettings()
        val today = LocalDate.now()
        val hijri = if (showHijri && s.hasDates) { date: LocalDate -> hijriCache.getOrPut(date) { Hijri.of(date).short } } else null
        val rows = buildRows(plan, sessions, today, hijri)
        val stats = planStats(plan, sessions, today)
        val missed = if (s.hasDates) missedCount(plan, sessions, today) else 0
        val focus = focusOf(rows, s.hasDates)
        val facts = planFacts(plan, sessions)
        val mainItem = if (s.includeNew) Item.NEW else Item.MAJOR
        return TableState(
            loaded = true,
            plan = plan,
            settings = s,
            description = PlanText.describe(s),
            rows = rows,
            entries = timeline(rows),
            doneRows = rows.count { it.mainDone },
            stats = stats,
            averageGrade = GradeScale.average(sessions.map { it.score(mainItem) })?.let { s.gradeScale.levelOf(it) },
            missed = missed,
            focus = focus,
            showHijri = showHijri,
            facts = facts,
            juzDone = if (plan.includeNew) Achievements.juzDone(facts.stats) else emptyList(),
        )
    }

    /** Sessions under week headers; without dates, just the sessions. */
    private fun timeline(rows: List<RowUi>): List<TimelineEntry> {
        if (rows.isEmpty()) return emptyList()
        if (!rows.first().hasDate) return rows.map { SessionEntry(it) }
        val first = rows.first().date
        val byWeek = rows.groupBy { (ChronoUnit.DAYS.between(first, it.date) / 7).toInt() + 1 }
        return byWeek.flatMap { (week, list) ->
            listOf(WeekEntry(week, list.count { it.mainDone }, list.size)) + list.map { SessionEntry(it) }
        }
    }
}

/**
 * A plan's table rows, ready to draw; shared by the plan's page and the home screen. [hijri] gives a
 * date's short Hijri date, or is null to leave Hijri dates out.
 */
internal fun buildRows(plan: PlanEntity, sessions: List<SessionEntity>, today: LocalDate, hijri: ((LocalDate) -> String)?): List<RowUi> {
    val s = plan.toSettings()
    fun pages(range: IntRange, unit: MemUnit, amount: Double): String? =
        if (unit == MemUnit.AYAH) null else RangeFormatter.pageLabel(range, unit == MemUnit.PAGE && amount < 1.0)
    // A session that goes on into the next round has a label per part; each stays on one line.
    fun pages(parts: List<IntRange>, unit: MemUnit, amount: Double): String? {
        val labels = parts.mapNotNull { pages(it, unit, amount) }
        return if (labels.size > 1) labels.joinToString("، ") { it.replaceFirst(' ', ' ') } else labels.firstOrNull()
    }
    fun grade(se: SessionEntity, item: Item): GradeLevel? = s.gradeScale.levelOf(se.score(item))

    // The next lesson after each row, for the preparation column (a review-only day has none of its own).
    val nextLesson = arrayOfNulls<SessionEntity>(sessions.size)
    var upcoming: SessionEntity? = null
    for (i in sessions.indices.reversed()) {
        nextLesson[i] = upcoming
        if (sessions[i].hasLesson) upcoming = sessions[i]
    }

    return sessions.mapIndexed { i, se ->
        val date = se.localDate
        fun offDay(item: Item) = ItemUi(item, "", "", null, false, null, empty = true, offDay = true)
        val items = buildList {
            if (s.includeNew && !se.has(Item.NEW)) {
                add(offDay(Item.NEW))
            } else if (s.includeNew) {
                val gen = se.lessonOrNull?.let { RangeFormatter.format(it) } ?: RangeFormatter.NONE
                // Typed by hand: its pages when it reads as a passage.
                val shown = if (se.newText == null) listOfNotNull(se.lessonOrNull) else RangeParser.parse(se.newText).orEmpty()
                val sub = pages(shown, s.newPortion.unit, s.newPortion.amount)
                add(ItemUi(Item.NEW, se.newText ?: gen, gen, sub, se.newDone, grade(se, Item.NEW), empty = !se.hasLesson, mistakes = se.newMistakes, alerts = se.newAlerts))
            }
            if (s.hasMinor && !se.has(Item.MINOR)) {
                add(offDay(Item.MINOR))
            } else if (s.hasMinor) {
                val gen = RangeFormatter.formatAll(Ranges.decode(se.minorRanges))
                val text = se.minorText ?: gen
                add(ItemUi(Item.MINOR, text, gen, null, se.minorDone, grade(se, Item.MINOR), empty = text == RangeFormatter.NONE, mistakes = se.minorMistakes, alerts = se.minorAlerts))
            }
            if (s.includeMajor && !se.has(Item.MAJOR)) {
                add(offDay(Item.MAJOR))
            } else if (s.includeMajor) {
                val parts = se.majorParts
                val gen = PlanText.generatedMajor(parts)
                val text = se.majorText ?: gen
                val sub = pages(if (se.majorText == null) parts else RangeParser.parse(se.majorText).orEmpty(), s.majorPortion.unit, s.majorPortion.amount)
                val empty = text.isBlank()
                add(ItemUi(Item.MAJOR, text, gen, sub, se.majorDone, grade(se, Item.MAJOR), empty, writable = empty && !s.hasAutoMajor, mistakes = se.majorMistakes, alerts = se.majorAlerts))
            }
            if (s.hasPrep && !se.has(Item.PREP)) {
                add(offDay(Item.PREP))
            } else if (s.hasPrep) {
                val gen = nextLesson[i]?.let { PlanText.lesson(it) } ?: RangeFormatter.NONE
                val text = se.prepText ?: gen
                add(ItemUi(Item.PREP, text, gen, null, se.prepDone, grade(se, Item.PREP), empty = text == RangeFormatter.NONE))
            }
        }
        val active = items.filter { !it.empty }
        val doneCount = active.count { it.done }
        val allDone = active.isNotEmpty() && doneCount == active.size
        RowUi(
            session = se,
            index = i,
            date = date,
            hasDate = s.hasDates,
            weekday = DateText.weekday(date),
            dateShort = DateText.short(date),
            label = if (s.hasDates) DateText.withWeekday(date) else "الجلسة ${i + 1}",
            hijriShort = hijri?.invoke(date),
            items = items,
            isToday = s.hasDates && date == today,
            isPast = s.hasDates && date < today,
            // A review-only day (tracks on their own days) is done when its reviews are.
            mainDone = if (se.hasMain(plan)) se.mainDone(plan) else allDone,
            allDone = allDone,
            doneFraction = if (active.isEmpty()) 0f else doneCount.toFloat() / active.size,
        )
    }
}

/** Today's row, or the next day with something still to do; without dates, the first session not done. */
internal fun focusOf(rows: List<RowUi>, hasDates: Boolean): Int? = if (hasDates) {
    rows.indexOfFirst { it.isToday }.takeIf { it >= 0 }
        ?: rows.indexOfFirst { !it.isPast && !it.allDone }.takeIf { it >= 0 }
} else {
    rows.indexOfFirst { !it.mainDone }.takeIf { it >= 0 }
}
