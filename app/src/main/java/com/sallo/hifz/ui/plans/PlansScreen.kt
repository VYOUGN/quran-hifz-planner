@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.plans

import com.sallo.hifz.ui.theme.LocalHideNumbers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.clickable
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sallo.hifz.HifzApp
import com.sallo.hifz.R
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.PlanRepository
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.mainRange
import com.sallo.hifz.data.missedCount
import com.sallo.hifz.data.planStats
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.ui.components.ProgressBar
import com.sallo.hifz.ui.table.CheckCircle
import com.sallo.hifz.ui.table.ItemUi
import com.sallo.hifz.ui.table.RowUi
import com.sallo.hifz.ui.table.buildRows
import com.sallo.hifz.ui.table.focusOf
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.LocalTracks
import com.sallo.hifz.ui.theme.heroBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanSummary(
    val plan: PlanEntity,
    val description: String,
    val stats: PlanStats,
    /** Today's session, the next one still to do, or the current one without dates; null once complete. */
    val focus: RowUi?,
    /** «درس اليوم», «الجلسة الجاية · الخميس 19/11», «الجلسة الحالية». */
    val focusTitle: String,
    val missed: Int,
    val hasDates: Boolean,
) {
    val dueToday: Boolean get() = focus?.isToday == true
}

class PlansViewModel(private val repo: PlanRepository) : ViewModel() {
    val summaries: StateFlow<List<PlanSummary>?> =
        combine(repo.plans, repo.allSessions) { plans, sessions ->
            val today = LocalDate.now()
            val byPlan = sessions.groupBy { it.planId }
            plans.map { summarize(it, byPlan[it.id].orEmpty(), today) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) {
        viewModelScope.launch { repo.deletePlan(id) }
    }

    /** Ticks or unticks one item of a session, right from the home screen. */
    fun toggle(row: SessionEntity, item: Item) {
        viewModelScope.launch { repo.updateSession(row.withDone(item, !row.done(item))) }
    }

    private fun summarize(plan: PlanEntity, sessions: List<SessionEntity>, today: LocalDate): PlanSummary {
        val settings = plan.toSettings()
        val stats = planStats(plan, sessions, today)
        val rows = buildRows(plan, sessions, today, hijri = null)
        val focus = if (stats.isComplete) null else focusOf(rows, settings.hasDates)?.let { rows[it] }
        val title = when {
            focus == null -> ""
            !focus.hasDate -> "الجلسة الحالية"
            focus.isToday -> "${PlanText.dayWord(focus.session, plan)} اليوم"
            else -> "الجلسة الجاية · ${focus.label}"
        }
        val missed = if (settings.hasDates) missedCount(plan, sessions, today) else 0
        return PlanSummary(plan, PlanText.describe(settings), stats, focus, title, missed, settings.hasDates)
    }
}

@Composable
fun PlansScreen(
    app: HifzApp,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onCopy: (Long) -> Unit = {},
    onOverview: () -> Unit = {},
    onBadges: () -> Unit = {},
    onNotes: () -> Unit = {},
) {
    val vm: PlansViewModel = viewModel(factory = viewModelFactory { initializer { PlansViewModel(app.repository) } })
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val prefs by app.prefs.values.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<PlanSummary?>(null) }
    val view = LocalView.current
    val haptics = prefs.haptics
    val toggle: (SessionEntity, Item) -> Unit = remember(vm, view, haptics) {
        { row, item ->
            if (haptics) {
                view.performHapticFeedback(
                    when {
                        row.done(item) -> HapticFeedbackConstants.CLOCK_TICK
                        Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
                        else -> HapticFeedbackConstants.VIRTUAL_KEY
                    },
                )
            }
            vm.toggle(row, item)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!summaries.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("خطة جديدة") },
                )
            }
        },
    ) { padding ->
        val list = summaries
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(prefs.showHijri, onSettings) }
            when {
                list == null -> Unit
                list.isEmpty() -> item(key = "empty") { EmptyState(onNew) }
                else -> {
                    item(key = "links") { Links(onOverview, onBadges, onNotes) }
                    todayLine(list)?.let { (icon, text) -> item(key = "today") { TodayBanner(icon, text) } }
                    items(list, key = { it.plan.id }) { summary ->
                        PlanCard(
                            summary = summary,
                            onOpen = { onOpen(summary.plan.id) },
                            onEdit = { onEdit(summary.plan.id) },
                            onDelete = { deleting = summary },
                            onCopy = { onCopy(summary.plan.id) },
                            onToggle = toggle,
                        )
                    }
                }
            }
        }
    }

    deleting?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف الخطة؟") },
            text = { Text("بتنحذف «${summary.plan.name}» مع كل اللي سجلته فيها، وما تقدر ترجعها بعد الحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(summary.plan.id)
                    deleting = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}

/** «إحصائياتي», «الأوسمة», «الملاحظات». */
@Composable
private fun Links(onOverview: () -> Unit, onBadges: () -> Unit, onNotes: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkChip("إحصائياتي", painterResource(R.drawable.ic_stats), Modifier.weight(1f), onOverview)
        LinkChip("الأوسمة", rememberVectorPainter(Icons.Default.Star), Modifier.weight(1f), onBadges)
        LinkChip("الملاحظات", rememberVectorPainter(Icons.AutoMirrored.Filled.List), Modifier.weight(1f), onNotes)
    }
}

@Composable
private fun LinkChip(text: String, icon: Painter, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** What today holds across all plans; null when no plan has dates. */
private fun todayLine(list: List<PlanSummary>): Pair<ImageVector, String>? {
    if (list.none { it.hasDates }) return null
    val due = list.filter { it.dueToday }
    val pending = due.count { it.focus?.allDone == false }
    return when {
        due.isEmpty() -> Icons.Default.DateRange to "ما عندك دروس اليوم"
        pending == 0 -> Icons.Default.CheckCircle to "خلّصت دروس اليوم، ما شاء الله"
        else -> Icons.Default.PlayArrow to "باقي لك ${Plurals.lessons(pending)} اليوم"
    }
}

/** The app's name with today's date, Gregorian and Hijri. */
@Composable
private fun Header(showHijri: Boolean, onSettings: () -> Unit) {
    val today = remember { LocalDate.now() }
    val date = remember(today, showHijri, Hijri.offsetDays) {
        val gregorian = "${DateText.weekday(today)} ${DateText.dayAndMonth(today)}"
        if (!showHijri) {
            gregorian
        } else {
            val h = Hijri.of(today)
            "$gregorian · ${h.day} ${DateText.HIJRI_MONTHS[h.month - 1]}"
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("جدول الحفظ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") }
    }
}

@Composable
private fun TodayBanner(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LocalDesign.current.accentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun PlanCard(
    summary: PlanSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onToggle: (SessionEntity, Item) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val stats = summary.stats
    val hide = LocalHideNumbers.current
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(summary.plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        summary.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("نسخ الخطة") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                menu = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            summary.focus?.let { row -> SessionBox(summary.focusTitle, row, onToggle, onOpen) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!hide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${stats.done} من ${Plurals.sessions(stats.total)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Plurals.percent(stats.progress), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                ProgressBar(stats.progress)
            }
            // «إخفاء الأرقام» keeps what needs doing (missed lessons) and drops the counting.
            val finish = if (hide) null else stats.paceFinish ?: if (summary.hasDates) stats.scheduledFinish else null
            val streak = if (summary.hasDates && !hide) stats.currentStreak else 0
            if (stats.isComplete || streak > 0 || finish != null || summary.missed > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stats.isComplete) Tag("✓ اكتملت الخطة، ما شاء الله", MaterialTheme.colorScheme.primary)
                    if (summary.missed > 0) Tag(Plurals.missed(summary.missed), MaterialTheme.colorScheme.error)
                    if (streak > 0) Tag("🔥 $streak متتالية", LocalDesign.current.accentText)
                    if (!stats.isComplete) finish?.let { Tag("🏁 ${DateText.dayAndMonth(it)}", MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

/** A session's items with a check for each, so it can be ticked without opening the plan. */
@Composable
private fun SessionBox(title: String, row: RowUi, onToggle: (SessionEntity, Item) -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (row.isToday) LocalDesign.current.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.items.filter { !it.offDay && (!it.empty || it.writable) }.forEach { item ->
            SessionItem(item, onToggle = { onToggle(row.session, item.item) }, onOpen = onOpen)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(item: ItemUi, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LocalTracks.current.of(item.item)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { if (item.empty) onOpen() else onToggle() }, onLongClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(50)).background(c.main))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.empty) "+ اكتبها" else item.text,
                style = if (item.empty) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    item.empty -> MaterialTheme.colorScheme.outline
                    item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                listOfNotNull(item.item.label, item.sub).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = c.main,
            )
        }
        if (!item.empty) CheckCircle(item.done, c.main, 30.dp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(120.dp).heroBackground(LocalDesign.current, 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_book), contentDescription = null, modifier = Modifier.size(64.dp), tint = LocalDesign.current.accent)
        }
        Spacer(Modifier.height(20.dp))
        Text("ابدأ رحلتك مع القرآن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "حدد نيتك ومقدار الجلسة وأيامها، والتطبيق يرتب لك الجدول ويتابع معك.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNew) { Text("خطة جديدة") }
    }
}
