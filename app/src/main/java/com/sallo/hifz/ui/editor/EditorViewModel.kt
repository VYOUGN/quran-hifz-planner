package com.sallo.hifz.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.PlanRepository
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.plan.BehindPolicy
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.RowDraft
import com.sallo.hifz.quran.Quran
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class EditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val template: PlanTemplate = PlanTemplate.MY_TABLE,
    val settings: PlanSettings = PlanSettings(),
    /** Settings as first shown, to tell whether the rows need rebuilding. */
    val base: PlanSettings? = null,
    /** Rows with progress at the top of the table; they are kept as they are. */
    val keptCount: Int = 0,
    /** Nothing is left to cover in the plan's range. */
    val completed: Boolean = false,
    val saving: Boolean = false,
)

/** «عاين الخطة»: the generated sessions and their totals. */
data class PlanPreview(
    val rows: List<RowDraft>,
    val totalPages: Double,
    /** Days from the first to the last session, or null without dates. */
    val days: Int?,
    val lastDate: LocalDate?,
    /** Sessions of the main track (lessons, or reviews in a review-only plan); review-only days are not counted. */
    val sessions: Int = rows.size,
) {
    val pagesPerSession: Double get() = if (sessions == 0) 0.0 else totalPages / sessions
    val pagesPerDay: Double? get() = days?.let { totalPages / it }

    companion object {
        /** Settings that generate the same rows: the name and how the plan is shown do not matter. */
        fun key(s: PlanSettings): PlanSettings =
            s.copy(name = "", gradeScale = GradeScale.WORDS, behind = BehindPolicy.ASK, prepEnabled = false)

        fun of(s: PlanSettings): PlanPreview? {
            val rows = PlanBuilder.build(s)
            if (rows.isEmpty()) return null
            val main = rows.mapNotNull { r -> if (s.includeNew) r.lesson else r.major }
            val total = main.sumOf { Quran.weight(it) }
            val days = if (s.hasDates) (ChronoUnit.DAYS.between(rows.first().date, rows.last().date) + 1).toInt() else null
            return PlanPreview(rows, total, days, if (s.hasDates) rows.last().date else null, sessions = main.size)
        }
    }
}

/** A preview and the settings it was made from, so the screen can tell when it is out of date. */
data class PreviewResult(val key: PlanSettings, val preview: PlanPreview?)

/** [autoCatchUp]: new plans move missed lessons by themselves («الترحيل» in the settings). */
/** [copyFrom]: «نسخ الخطة», a new plan with that plan's settings. */
class EditorViewModel(
    private val repo: PlanRepository,
    planId: Long?,
    autoCatchUp: Boolean = false,
    copyFrom: Long? = null,
) : ViewModel() {
    private val state = MutableStateFlow(EditorState())
    val ui: StateFlow<EditorState> = state.asStateFlow()
    private var plan: PlanEntity? = null

    /**
     * «عاين الخطة», worked out away from the main thread a moment after the settings stop changing,
     * so typing and tapping in the editor stay smooth even for long plans.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val preview: StateFlow<PreviewResult?> = state
        .map { PlanPreview.key(it.settings) }
        .distinctUntilChanged()
        .mapLatest { key ->
            delay(PREVIEW_DELAY_MS)
            val valid = key.copy(name = "-").validate() == null
            PreviewResult(key, if (valid) PlanPreview.of(key) else null)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (planId == null && copyFrom != null) {
            viewModelScope.launch { copy(copyFrom, autoCatchUp) }
        } else if (planId == null) {
            state.value = EditorState(
                loading = false,
                template = PlanTemplate.MY_TABLE,
                settings = PlanTemplate.MY_TABLE.applyTo(
                    PlanSettings(startDate = LocalDate.now(), behind = if (autoCatchUp) BehindPolicy.AUTO else BehindPolicy.ASK),
                ),
            )
        } else {
            viewModelScope.launch { load(planId) }
        }
    }

    /** A new plan with [id]'s settings, from the start of its range and from today. */
    private suspend fun copy(id: Long, autoCatchUp: Boolean) {
        val p = repo.getPlan(id)
        if (p == null) {
            state.value = EditorState(
                loading = false,
                settings = PlanTemplate.MY_TABLE.applyTo(
                    PlanSettings(startDate = LocalDate.now(), behind = if (autoCatchUp) BehindPolicy.AUTO else BehindPolicy.ASK),
                ),
            )
            return
        }
        val stored = p.toSettings()
        state.value = EditorState(
            loading = false,
            template = PlanTemplate.fromName(p.template),
            settings = stored.copy(
                name = "${stored.name} (نسخة)",
                startDate = LocalDate.now(),
                vacations = emptyList(),
                newPortion = stored.newPortion.copy(resume = null),
                majorPortion = stored.majorPortion.copy(resume = null),
            ),
        )
    }

    private suspend fun load(id: Long) {
        val p = repo.getPlan(id)
        if (p == null) {
            state.value = EditorState(loading = false)
            return
        }
        val rows = repo.getSessions(id)
        val stored = p.toSettings()
        val last = rows.indexOfLast { it.hasProgress() }
        var completed = false
        val base: PlanSettings = if (last < 0) {
            // Nothing done yet: offer the whole plan again, from its first session.
            val first = rows.firstOrNull()
            val s = when {
                first == null -> stored
                stored.includeNew && first.hasLesson ->
                    stored.copy(newPortion = stored.newPortion.copy(resume = stored.newPortion.resumeAt(first.lesson)))
                !stored.includeNew && first.majorRange != null ->
                    stored.copy(majorPortion = stored.majorPortion.copy(resume = stored.majorPortion.resumeAt(first.majorRange!!)))
                else -> stored
            }
            s.copy(startDate = first?.localDate ?: stored.startDate)
        } else {
            // Continue after the last row with progress.
            val kept = rows.take(last + 1)
            val continued = PlanBuilder.continuation(
                stored,
                kept.lastOrNull { it.hasLesson }?.lesson,
                kept.lastOrNull { it.majorRange != null }?.majorRange,
            )
            if (continued == null) {
                completed = true
                stored
            } else if (!stored.hasDates) {
                continued
            } else {
                val earliest = maxOf(LocalDate.now(), rows[last].localDate.plusDays(1))
                // With separate days each track finds its own first day, so the reviews are not skipped.
                var start = if (stored.hasSeparateSchedules) earliest else stored.calendar.firstOnOrAfter(earliest)
                val following = rows.getOrNull(last + 1)?.localDate
                if (following != null && following > start) start = following
                continued.copy(startDate = start)
            }
        }
        plan = p
        state.value = EditorState(
            loading = false,
            isEdit = true,
            template = PlanTemplate.fromName(p.template),
            settings = base,
            base = base,
            keptCount = last + 1,
            completed = completed,
        )
    }

    fun update(transform: (PlanSettings) -> PlanSettings) {
        state.update { it.copy(settings = transform(it.settings)) }
    }

    fun applyTemplate(template: PlanTemplate) {
        state.update { it.copy(template = template, settings = template.applyTo(it.settings)) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val st = state.value
        if (st.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val p = plan
            val id = when {
                p == null -> repo.createPlan(st.settings, st.template)
                st.completed || !rowsChange(st.base ?: st.settings, st.settings) -> {
                    repo.updatePlanInfo(
                        p.copy(
                            name = st.settings.name.trim(),
                            prepEnabled = st.settings.prepEnabled,
                            behind = st.settings.behind.name,
                            gradeScale = st.settings.gradeScale.name,
                            template = st.template.name,
                        ),
                    )
                    p.id
                }
                else -> {
                    repo.regenerate(p, st.settings, st.template, st.keptCount)
                    p.id
                }
            }
            onSaved(id)
        }
    }

    /** The name, the prep column, the missed-lessons policy and the grading system do not change the stored rows. */
    private fun rowsChange(before: PlanSettings, after: PlanSettings): Boolean =
        PlanPreview.key(before) != PlanPreview.key(after)

    private companion object {
        const val PREVIEW_DELAY_MS = 150L
    }
}
