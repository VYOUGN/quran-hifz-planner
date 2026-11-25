@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.sallo.hifz.R
import com.sallo.hifz.ui.theme.LocalDesign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Item
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.BehindPolicy
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.plan.MajorMode
import com.sallo.hifz.plan.MinorMode
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.TrackSchedule
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.DropdownPill
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.InfoCard
import com.sallo.hifz.ui.components.LabeledRow
import com.sallo.hifz.ui.components.Loading
import com.sallo.hifz.ui.components.NumberDialog
import com.sallo.hifz.ui.components.PickDateDialog
import com.sallo.hifz.ui.components.Pill
import com.sallo.hifz.ui.components.Section
import com.sallo.hifz.ui.components.Stepper
import com.sallo.hifz.ui.components.Word
import com.sallo.hifz.ui.theme.LocalTracks
import com.sallo.hifz.ui.theme.gradeColor
import java.time.DayOfWeek
import java.time.LocalDate

private typealias Update = ((PlanSettings) -> PlanSettings) -> Unit

@Composable
fun PlanEditorScreen(app: HifzApp, planId: Long?, onSaved: (Long) -> Unit, onBack: () -> Unit, copyFrom: Long? = null) {
    val vm: EditorViewModel = viewModel(
        factory = viewModelFactory { initializer { EditorViewModel(app.repository, planId, app.prefs.values.value.autoCatchUp, copyFrom) } },
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val s = state.settings
    val tracks = LocalTracks.current
    val error = remember(s) { s.validate() }
    // The preview is worked out in the background; until it catches up with the settings it is shown faded.
    val previewResult by vm.preview.collectAsStateWithLifecycle()
    val upToDate = previewResult?.key == PlanPreview.key(s)
    val preview = previewResult?.preview
    // «عاين» opens the sessions over the editor; the editor stays underneath with its scroll position.
    var sessionsOpen by remember { mutableStateOf(false) }
    var shownPreview by remember { mutableStateOf<PlanPreview?>(null) }
    // One part of the plan open at a time; the rest show a line saying how they are set.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val toggleOpen: (String) -> Unit = { key -> open = if (open == key) null else key }
    val scroll = rememberScrollState()
    val canSave = !state.loading && !state.saving &&
        if (state.completed) s.name.isNotBlank() else error == null && upToDate && preview != null
    val byDate = s.scheduleMode == ScheduleMode.BY_DATE

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (state.isEdit) "تعديل الخطة" else "خطة جديدة") },
                    navigationIcon = { BackButton(onBack) },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!state.loading && !state.completed) {
                            PlanSummaryLine(preview, error, computing = error == null && !upToDate) {
                                shownPreview = preview
                                sessionsOpen = true
                            }
                        }
                        Button(
                            onClick = { vm.save(onSaved) },
                            enabled = canSave,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(if (state.isEdit) "حفظ التعديل" else "أنشئ الجدول", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            },
        ) { padding ->
            if (state.loading) {
                Loading(Modifier.padding(padding))
                return@Scaffold
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    state.completed -> InfoCard("الخطة مكتملة. تقدر تغيّر اسمها وتقييمها بس.")
                    state.keptCount > 0 -> InfoCard(
                        "أول ${Plurals.lessons(state.keptCount)} في الجدول فيها إنجاز وبتبقى زي ما هي، والتعديل يبدأ من الجلسة اللي بعدها.",
                    )
                }
                if (!state.completed) TemplateGrid(state.template, vm::applyTemplate)
                OutlinedTextField(
                    value = s.name,
                    onValueChange = { v -> vm.update { it.copy(name = v) } },
                    label = { Text("اسم الخطة") },
                    placeholder = { Text("مثلاً: خطة محمد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FoldGroup {
                    if (!state.completed) {
                        TrackFold(
                            item = Item.NEW,
                            title = "الحفظ",
                            summary = if (s.includeNew) PlanText.intent("", s.newPortion, byDate).trim() else "بدون حفظ جديد، الجدول للمراجعة",
                            included = s.includeNew,
                            canSwitch = true,
                            expanded = open == "new",
                            onExpand = { toggleOpen("new") },
                            onSwitch = { on ->
                                vm.update { if (on) it.copy(includeNew = true) else it.copy(includeNew = false, includeMajor = true, majorMode = MajorMode.RANGE) }
                                if (on) open = "new"
                            },
                        ) {
                            PortionEditor("أحفظ", s.newPortion, byDate, tracks.new.main) { p -> vm.update { it.withNewPortion(p) } }
                        }
                        FoldDivider()
                        TrackFold(
                            item = Item.MINOR,
                            title = "المراجعة الصغرى",
                            summary = minorSummary(s),
                            included = s.hasMinor,
                            canSwitch = s.includeNew,
                            expanded = open == "minor",
                            onExpand = { toggleOpen("minor") },
                            onSwitch = { on ->
                                vm.update { it.copy(includeMinor = on) }
                                if (on) open = "minor"
                            },
                        ) { MinorEditor(s, vm::update) }
                        FoldDivider()
                        TrackFold(
                            item = Item.MAJOR,
                            title = "المراجعة الكبرى",
                            summary = majorSummary(s),
                            included = s.includeMajor,
                            canSwitch = s.includeNew,
                            expanded = open == "major",
                            onExpand = { toggleOpen("major") },
                            onSwitch = { on ->
                                vm.update { it.copy(includeMajor = on) }
                                if (on) open = "major"
                            },
                        ) { MajorEditor(s, vm::update) }
                        FoldDivider()
                        TrackFold(
                            item = Item.PREP,
                            title = "التحضير",
                            summary = if (s.hasPrep) "الدرس الجاي، تسمعه أو تقراه قبل ما تحفظه" else "مطفي",
                            included = s.hasPrep,
                            canSwitch = s.includeNew,
                            expanded = false,
                            onExpand = {},
                            onSwitch = { on -> vm.update { it.copy(prepEnabled = on) } },
                            content = null,
                        )
                        FoldDivider()
                        Fold(
                            icon = { Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(20.dp)) },
                            title = "الأيام",
                            summary = scheduleSummary(s, continuing = state.keptCount > 0),
                            expanded = open == "days",
                            onExpand = { toggleOpen("days") },
                        ) { ScheduleControls(s, continuing = state.keptCount > 0, update = vm::update) }
                        FoldDivider()
                    }
                    Fold(
                        icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        title = "التقييم",
                        summary = s.gradeScale.levels.joinToString("، ") { it.label },
                        expanded = open == "grades",
                        onExpand = { toggleOpen("grades") },
                    ) { GradingControls(s, vm::update) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        AnimatedVisibility(
            visible = sessionsOpen,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            shownPreview?.let { SessionsScreen(it, s) { sessionsOpen = false } }
        }
    }
}

private fun minorSummary(s: PlanSettings): String = when {
    !s.hasMinor -> "مطفية"
    s.minorMode == MinorMode.CUMULATIVE_JUZ -> "تراكمي من بداية الجزء"
    s.minorCount == 0 -> "بدون مراجعة صغرى"
    else -> lastLessons(s.minorCount)
}

private fun majorSummary(s: PlanSettings): String = when {
    !s.includeMajor -> "مطفية"
    s.majorMode == MajorMode.MANUAL -> "تكتبها بنفسك"
    s.hasMemorizedMajor -> "${Plurals.amount(s.majorPortion.unit, s.majorPortion.amount)} من محفوظك"
    else -> PlanText.intent("", s.majorPortion, s.scheduleMode == ScheduleMode.BY_DATE && !s.includeNew).trim()
}

private fun scheduleSummary(s: PlanSettings, continuing: Boolean): String {
    val days = if (s.hasSeparateSchedules) "لكل مسار أيامه" else PlanText.schedule(s)
    val start = if (s.startDate == LocalDate.now()) "اليوم" else DateText.long(s.startDate)
    return "$days · ${if (continuing) "التعديل من" else "تبدأ"} $start"
}

private val TEMPLATE_SHORT = mapOf(
    PlanTemplate.MY_TABLE to "مثل ورقتك",
    PlanTemplate.PASSAGES to "مقطع لكل جلسة",
    PlanTemplate.FIVE_FORTS to "وجه وتحضير ومراجعتين",
    PlanTemplate.CUMULATIVE to "من بداية الجزء",
    PlanTemplate.REVIEW to "بدون حفظ جديد",
    PlanTemplate.CUSTOM to "ترتّبه بنفسك",
)

@Composable
private fun templateIcon(t: PlanTemplate): Painter = when (t) {
    PlanTemplate.MY_TABLE -> painterResource(R.drawable.ic_grid)
    PlanTemplate.PASSAGES -> painterResource(R.drawable.ic_book)
    PlanTemplate.FIVE_FORTS -> painterResource(R.drawable.ic_shield)
    PlanTemplate.CUMULATIVE -> rememberVectorPainter(Icons.AutoMirrored.Filled.List)
    PlanTemplate.REVIEW -> rememberVectorPainter(Icons.Default.Refresh)
    PlanTemplate.CUSTOM -> rememberVectorPainter(Icons.Default.Build)
}

/** The ready-made plans as cards, two to a row; picking one fills in everything below. */
@Composable
private fun TemplateGrid(selected: PlanTemplate, onSelect: (PlanTemplate) -> Unit) {
    val accent = LocalDesign.current.accent
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ابدأ من قالب", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlanTemplate.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t ->
                    val on = t == selected
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (on) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(if (on) 1.5.dp else 1.dp, if (on) accent else MaterialTheme.colorScheme.outlineVariant, shape)
                            .selectable(selected = on, role = Role.RadioButton) { onSelect(t) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                templateIcon(t),
                                contentDescription = null,
                                tint = if (on) LocalDesign.current.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(
                            TEMPLATE_SHORT[t].orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Hint(selected.hint)
    }
}

/** The parts of the plan, one card with a line for each. */
@Composable
private fun FoldGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column(content = content) }
}

@Composable
private fun FoldDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A part of the plan: its name and a line saying how it is set; tapping it opens its settings. */
@Composable
private fun Fold(
    icon: @Composable () -> Unit,
    title: String,
    summary: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (content != null) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "إخفاء" else "تعديل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(turn),
                )
            }
            trailing()
        }
        AnimatedVisibility(visible = expanded && content != null) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content?.invoke(this) }
        }
    }
}

/** A track of the plan: its color, a switch to include it, and its settings when opened. */
@Composable
private fun TrackFold(
    item: Item,
    title: String,
    summary: String,
    included: Boolean,
    canSwitch: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    val c = LocalTracks.current.of(item)
    Fold(
        icon = { Box(Modifier.size(12.dp).clip(CircleShape).background(if (included) c.main else c.main.copy(alpha = 0.3f))) },
        title = title,
        summary = summary,
        expanded = expanded && included,
        onExpand = { if (included) onExpand() else if (canSwitch) onSwitch(true) },
        trailing = {
            Switch(
                checked = included,
                onCheckedChange = onSwitch,
                enabled = canSwitch,
                modifier = Modifier.padding(start = 4.dp),
                colors = SwitchDefaults.colors(checkedTrackColor = c.main, checkedThumbColor = Color.White, checkedBorderColor = c.main),
            )
        },
        content = if (included) content else null,
    )
}

/** Segmented choice; with more than two options the check mark is left out to make room. */
@Composable
internal fun Choices(options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: (Int) -> Boolean = { true }) {
    val compact = options.size > 2
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                enabled = enabled(i),
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = { if (!compact) SegmentedButtonDefaults.Icon(i == selected) },
            ) {
                Text(label, maxLines = 1, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun lastLessons(n: Int): String = when {
    n == 1 -> "آخر درس"
    n == 2 -> "آخر درسين"
    n in 3..10 -> "آخر $n دروس"
    else -> "آخر $n درساً"
}

@Composable
private fun MinorEditor(s: PlanSettings, update: Update) {
    val minorModes = listOf(MinorMode.LAST_N, MinorMode.CUMULATIVE_JUZ)
    Choices(
        options = listOf("آخر الدروس", "تراكمي من بداية الجزء"),
        selected = minorModes.indexOf(s.minorMode),
        onSelect = { i -> update { it.copy(minorMode = minorModes[i]) } },
    )
    if (s.minorMode == MinorMode.LAST_N) {
        Stepper(
            text = if (s.minorCount == 0) "بدون مراجعة صغرى" else lastLessons(s.minorCount),
            canDecrease = s.minorCount > 0,
            canIncrease = s.minorCount < 60,
            onDecrease = { update { it.copy(minorCount = it.minorCount - 1) } },
            onIncrease = { update { it.copy(minorCount = it.minorCount + 1) } },
        )
        Hint("تراجع في كل جلسة آخر الدروس اللي حفظتها قبلها.")
    } else {
        Hint("تراجع كل الدروس من أول الجزء الحالي، وتبدأ من جديد مع كل جزء.")
    }
}

@Composable
private fun MajorEditor(s: PlanSettings, update: Update) {
    val color = LocalTracks.current.major.main
    val modes = listOf(MajorMode.RANGE, MajorMode.MEMORIZED, MajorMode.MANUAL)
    Choices(
        options = listOf("مدى محدد", "من محفوظي", "أكتبها بنفسي"),
        selected = modes.indexOf(s.majorMode),
        enabled = { i -> s.includeNew || modes[i] == MajorMode.RANGE },
        onSelect = { i ->
            update {
                val mode = modes[i]
                when {
                    it.majorMode == mode -> it
                    mode == MajorMode.MEMORIZED -> it.copy(majorMode = mode).withSuggestedPrior()
                    else -> it.copy(majorMode = mode)
                }
            }
        },
    )
    when {
        s.majorMode == MajorMode.MANUAL -> Hint("تكتبها بنفسك في كل جلسة، أو تختارها من السور.")
        s.hasMemorizedMajor -> MemorizedEditor(s.majorPortion, s.majorPrior, color) { p, prior ->
            update { it.copy(majorPortion = p, majorPrior = prior) }
        }
        else -> {
            PortionEditor(
                verb = "أراجع",
                portion = s.majorPortion,
                byDate = s.scheduleMode == ScheduleMode.BY_DATE && !s.includeNew,
                color = color,
            ) { p -> update { it.copy(majorPortion = p) } }
            if (s.includeNew) Hint("تمشي على المدى بالترتيب، ولما يخلص تبدأ من أوله، طول مدة الخطة.")
        }
    }
    if (s.canWrapMajor) {
        val amount = Plurals.amount(s.majorPortion.unit, s.majorPortion.amount)
        SwitchRow(
            title = "كمّل آخر جلسة من الدورة الجاية",
            hint = "إذا بقي من الدورة أقل من $amount، تراجع اللي بقي وتكمّل المقدار من بداية الدورة الجديدة، " +
                "وتمشي الدورة الجديدة من بعده. مثلاً بخمسة أوجه وبقي وجهان: تراجعهما وثلاثة أوجه من الدورة الجديدة.",
            checked = s.majorWrap,
            color = color,
        ) { on -> update { it.copy(majorWrap = on) } }
    }
}

private fun modeLabel(m: ScheduleMode): String = when (m) {
    ScheduleMode.WEEKDAYS -> "أيام محددة"
    ScheduleMode.INTERVAL -> "كل عدد من الأيام"
    ScheduleMode.WEEKS -> "كل عدد من الأسابيع"
    ScheduleMode.MONTHS -> "كل عدد من الأشهر"
    ScheduleMode.ANYTIME -> "أي وقت"
    ScheduleMode.BY_DATE -> "حسب تاريخ الختم"
}

private fun everyWord(m: ScheduleMode, n: Int): String {
    val few = n in 3..10
    return when (m) {
        ScheduleMode.WEEKS -> if (few) "أسابيع" else "أسبوع"
        ScheduleMode.MONTHS -> if (few) "أشهر" else "شهر"
        else -> if (few) "أيام" else "يوم"
    }
}

private fun behindLabel(b: BehindPolicy): String = when (b) {
    BehindPolicy.ASK -> "نبهني وأختار بنفسي"
    BehindPolicy.AUTO -> "رحّلها تلقائياً"
}

@Composable
private fun DatePill(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Pill(DateText.long(date), onClick = { open = true }, dropdown = true)
    if (open) {
        PickDateDialog(initial = date, onDismiss = { open = false }) {
            onPick(it)
            open = false
        }
    }
}

@Composable
private fun ScheduleControls(s: PlanSettings, continuing: Boolean, update: Update) {
    run {
        if (s.canSeparateSchedules) {
            Choices(
                options = listOf("نفس الأيام للكل", "لكل مسار أيامه"),
                selected = if (s.separateSchedules) 1 else 0,
                onSelect = { i -> update { it.withSeparateSchedules(i == 1) } },
            )
        }
        val separate = s.hasSeparateSchedules
        if (separate) TrackLabel(Item.NEW, if (s.hasPrep) "الحفظ والتحضير" else "الحفظ")
        LabeledRow("التكرار") {
            DropdownPill(
                text = modeLabel(s.scheduleMode),
                options = ScheduleMode.entries,
                label = ::modeLabel,
                selected = s.scheduleMode,
                onSelect = { m ->
                    update {
                        it.copy(
                            scheduleMode = m,
                            endDate = if (m == ScheduleMode.BY_DATE) it.endDate ?: it.startDate.plusDays(29) else it.endDate,
                        )
                    }
                },
            )
        }
        if (s.scheduleMode == ScheduleMode.ANYTIME) {
            Hint("الجلسات بدون تواريخ، تمشي عليها بالترتيب على راحتك.")
        } else {
            DaysControls(s.scheduleMode, s.weekdays, s.every) { days, every -> update { it.copy(weekdays = days, every = every) } }
        }
        if (s.scheduleMode == ScheduleMode.BY_DATE) {
            LabeledRow("الختم") { DatePill(s.endDate ?: s.startDate) { d -> update { it.copy(endDate = d) } } }
            Hint("المقدار ينحسب تلقائياً عشان تختم قبل هذا التاريخ.")
        }
        if (separate) {
            if (s.hasMinor) {
                TrackLabel(Item.MINOR, "المراجعة الصغرى")
                TrackScheduleControls(s.minorSchedule) { t -> update { it.copy(minorSchedule = t) } }
            }
            if (s.includeMajor) {
                TrackLabel(Item.MAJOR, "المراجعة الكبرى")
                TrackScheduleControls(s.majorSchedule) { t -> update { it.copy(majorSchedule = t) } }
            }
            Hint("كل مسار يمشي على أيامه، والجدول يجمعها حسب التاريخ. المراجعات تستمر لين آخر درس، ومراجعة اليوم الصغرى هي آخر الدروس اللي حفظتها قبله.")
        }
        LabeledRow(if (continuing) "أول جلسة جديدة" else "البداية") {
            DatePill(s.startDate) { d -> update { it.copy(startDate = d) } }
        }
        if (s.hasDates) {
            LabeledRow("إذا تأخرت") {
                DropdownPill(
                    text = behindLabel(s.behind),
                    options = BehindPolicy.entries,
                    label = ::behindLabel,
                    selected = s.behind,
                    onSelect = { b -> update { it.copy(behind = b) } },
                )
            }
            Hint(
                if (s.behind == BehindPolicy.AUTO) {
                    "كل يوم الدروس اللي فاتتك تنتقل لأول يوم حفظ جاي واللي بعدها يتأخر معها، حتى لو ما فتحت التطبيق."
                } else {
                    "يطلع لك شريط فيه الدروس الفائتة، وتختار: تكمّلها من جديد، أو تعوّضها بجلسة كل يوم، أو تعلّمها كمنجزة."
                },
            )
        }
    }
}

/** A track's name in its color, heading its days. */
@Composable
private fun TrackLabel(item: Item, title: String) {
    val c = LocalTracks.current.of(item)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.main))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.main)
    }
}

/** A review's own days: chosen weekdays, or every N days / weeks / months. */
@Composable
private fun TrackScheduleControls(t: TrackSchedule, onChange: (TrackSchedule) -> Unit) {
    LabeledRow("التكرار") {
        DropdownPill(
            text = modeLabel(t.mode),
            options = TrackSchedule.MODES,
            label = ::modeLabel,
            selected = t.mode,
            onSelect = { m -> onChange(t.copy(mode = m)) },
        )
    }
    DaysControls(t.mode, t.weekdays, t.every) { days, every -> onChange(t.copy(weekdays = days, every = every)) }
}

/** Weekday chips, or «كل [N] أيام» with a number dialog. */
@Composable
private fun DaysControls(mode: ScheduleMode, weekdays: Set<DayOfWeek>, every: Int, onChange: (Set<DayOfWeek>, Int) -> Unit) {
    var everyDialog by remember { mutableStateOf(false) }
    when (mode) {
        ScheduleMode.INTERVAL, ScheduleMode.WEEKS, ScheduleMode.MONTHS -> LabeledRow("كل") {
            Pill(every.toString(), onClick = { everyDialog = true })
            Word(everyWord(mode, every))
        }
        ScheduleMode.WEEKDAYS, ScheduleMode.BY_DATE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DateText.WEEK_ORDER.forEach { day ->
                FilterChip(
                    selected = day in weekdays,
                    onClick = { onChange(if (day in weekdays) weekdays - day else weekdays + day, every) },
                    label = { Text(DateText.weekday(day)) },
                )
            }
        }
        ScheduleMode.ANYTIME -> Unit
    }
    if (everyDialog) {
        NumberDialog(
            title = "كل كم ${everyWord(mode, 3)}؟",
            value = every.toDouble(),
            min = 1.0,
            max = 30.0,
            step = 1.0,
            onDismiss = { everyDialog = false },
        ) { v ->
            onChange(weekdays, v.toInt())
            everyDialog = false
        }
    }
}

@Composable
private fun GradingControls(s: PlanSettings, update: Update) {
    LabeledRow("النظام") {
        DropdownPill(
            text = s.gradeScale.label,
            options = GradeScale.entries,
            label = { it.label },
            selected = s.gradeScale,
            onSelect = { g -> update { it.copy(gradeScale = g) } },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.gradeScale.levels.forEach { level ->
            Surface(shape = RoundedCornerShape(8.dp), color = gradeColor(level.score)) {
                Text(
                    level.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
    Hint("تقيّم من نافذة الجلسة. التقدير اللي لونه أحمر معناه يُعاد، فما يعلّم الدرس كمنجز. وإذا غيّرت النظام، التقديرات القديمة تتحول لأقرب تقدير فيه.")
}

/** Above the button: how many sessions the plan makes and when it ends, with «عاين» to see them. */
@Composable
private fun PlanSummaryLine(preview: PlanPreview?, error: String?, computing: Boolean, onShow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).alpha(if (computing) 0.45f else 1f)) {
            when {
                error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                preview == null -> Text(
                    if (computing) "نرتّب الجدول…" else "ما فيه جلسات في هذا المدى.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (computing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                else -> {
                    Text(
                        listOfNotNull(
                            Plurals.sessions(preview.sessions),
                            preview.lastDate?.let { "تختم ${DateText.long(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val perDay = preview.pagesPerDay
                    Text(
                        listOfNotNull(
                            "${Plurals.number(round1(perDay ?: preview.pagesPerSession))} ${if (perDay != null) "وجه في اليوم" else "وجه في الجلسة"}",
                            preview.days?.let { Plurals.days(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onShow, enabled = preview != null && error == null && !computing) { Text("عاين") }
    }
}

private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
