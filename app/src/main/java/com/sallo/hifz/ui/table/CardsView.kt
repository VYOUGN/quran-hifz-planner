@file:OptIn(ExperimentalFoundationApi::class)

package com.sallo.hifz.ui.table

import com.sallo.hifz.ui.theme.LocalHideNumbers
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sallo.hifz.R
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.GradeLevel
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.LocalTracks
import com.sallo.hifz.ui.theme.gradeColor
import com.sallo.hifz.ui.theme.heroBackground

/** The motivating view: a hero header, today's session, and the whole plan week by week. */
@Composable
fun CardsView(
    state: TableState,
    onToggle: (SessionEntity, Item) -> Unit,
    onOpenRow: (Int) -> Unit,
    onStats: () -> Unit,
    onReschedule: () -> Unit,
) {
    val rows = state.rows
    // The journey starts at the current week; the weeks before it fold into one line.
    val focusId = state.focus?.let { rows.getOrNull(it)?.session?.id }
    val start = remember(state.entries, focusId) { currentStart(state.entries, focusId) }
    var showPast by rememberSaveable(state.plan?.id) { mutableStateOf(false) }
    val past = if (start > 0) state.entries.subList(0, start) else emptyList()
    val shown = if (showPast || past.isEmpty()) state.entries else state.entries.subList(start, state.entries.size)
    val edges = remember(shown) { groupEdges(shown) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)) {
        item(key = "hero") { HeroCard(state, onStats) }
        if (state.missed > 0) item(key = "missed") { MissedCard(state.missed, onReschedule) }
        state.focus?.let { f ->
            item(key = "focus") { FocusCard(rows[f], onToggle, onOpenRow) }
        }
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("رحلتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!LocalHideNumbers.current) {
                    Text(
                        "${state.doneRows} من ${rows.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (past.isNotEmpty()) {
            item(key = "past") { PastLine(past, expanded = showPast) { showPast = !showPast } }
        }
        items(shown, key = { it.key }, contentType = { it is WeekEntry }) { entry ->
            val edge = edges[entry.key] ?: Edge.MIDDLE
            GroupPart(edge) {
                when (entry) {
                    is WeekEntry -> WeekHeader(entry)
                    is SessionEntry -> SessionRow(entry.row, divider = edge != Edge.TOP && edge != Edge.ALONE, onToggle, onOpenRow)
                }
            }
        }
    }
}

// ---------- hero ----------

internal fun motivation(stats: PlanStats): String = when {
    stats.isComplete -> "تمّت الخطة، تقبّل الله منك"
    stats.done == 0 -> "بسم الله، ابدأ رحلتك"
    stats.progress < 0.25f -> "بداية مباركة، استمر"
    stats.progress < 0.5f -> "أحسنت! كل جلسة تقرّبك"
    stats.progress < 0.75f -> "تجاوزت النصف، ما شاء الله"
    else -> "اقتربت من الختام، لا توقف الحين"
}

@Composable
private fun HeroCard(state: TableState, onStats: () -> Unit) {
    val stats = state.stats ?: return
    val settings = state.settings ?: return
    val progress by animateFloatAsState(stats.progress, tween(900, easing = FastOutSlowInEasing), label = "progress")
    val design = LocalDesign.current
    val hide = LocalHideNumbers.current
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .heroBackground(design, 28.dp)
            .clickable(onClick = onStats)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, stats.progress, 78.dp, showText = !hide)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(motivation(stats), color = design.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!hide) {
                    Text(
                        "أنجزت ${stats.done} من ${Plurals.sessions(stats.total)}",
                        color = design.onHero.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (!hide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.hasDates) HeroStat(stats.currentStreak.toString(), "متتالية", Modifier.weight(1f))
            if (settings.includeNew) {
                HeroStat(stats.ayahs.toString(), "آية", Modifier.weight(1f))
            } else {
                HeroStat(Plurals.number(Math.round(stats.pages * 10.0) / 10.0), "وجه", Modifier.weight(1f))
            }
            val finish = stats.paceFinish ?: if (settings.hasDates) stats.scheduledFinish else null
            if (finish != null) {
                HeroStat(DateText.dayAndMonth(finish), "الختم", Modifier.weight(1.4f))
            } else {
                HeroStat("${stats.total - stats.done}", "جلسة باقية", Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun HeroStat(value: String, label: String, modifier: Modifier) {
    val onHero = LocalDesign.current.onHero
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(onHero.copy(alpha = 0.12f)).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** The plan's ring; [shown] is the animated value drawn, [progress] the real one written inside. */
@Composable
internal fun ProgressRing(shown: Float, progress: Float, size: Dp, showText: Boolean = true) {
    val design = LocalDesign.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(design.onHero.copy(alpha = 0.22f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (shown > 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(design.accent, design.accentBright, design.accent)),
                    -90f,
                    // A sliver at least, so the first sessions show.
                    maxOf(360f * shown, 4f),
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (showText) Text(Plurals.percent(progress), color = design.onHero, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MissedCard(count: Int, onMove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(Plurals.missed(count), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                Text("ولا يهمك، نرحّلها ونكمل", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onMove) { Text("ترحيل") }
        }
    }
}

// ---------- today's session ----------

@Composable
fun itemIcon(item: Item): ImageVector = when (item) {
    Item.NEW -> ImageVector.vectorResource(R.drawable.ic_book)
    Item.MINOR -> Icons.Default.Refresh
    Item.MAJOR -> Icons.Default.Star
    Item.PREP -> Icons.Default.PlayArrow
}

@Composable
private fun FocusCard(row: RowUi, onToggle: (SessionEntity, Item) -> Unit, onOpenRow: (Int) -> Unit) {
    val title = when {
        !row.hasDate -> "الجلسة الحالية"
        row.isToday -> "درس اليوم"
        else -> "الجلسة الجاية"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, if (row.allDone) MaterialTheme.colorScheme.primary else LocalDesign.current.accent),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (row.hasDate) row.label else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onOpenRow(row.index) },
                )
            }
            row.items.filter { !it.offDay && (!it.empty || it.writable) }.forEach { item ->
                BigItem(item, onToggle = { onToggle(row.session, item.item) }, onOpen = { onOpenRow(row.index) })
            }
            if (row.allDone) {
                val complete = row.items.none { it.writable }
                Text(
                    when {
                        row.isToday && complete -> "أتممت درس اليوم كاملاً، ما شاء الله ✨"
                        row.isToday -> "أتممت درس اليوم، ما شاء الله ✨"
                        else -> "أتممت هذي الجلسة، ما شاء الله ✨"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One item of today's session: the track's color, what to do, and a big check. */
@Composable
private fun BigItem(item: ItemUi, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LocalTracks.current.of(item.item)
    val bg by animateColorAsState(if (item.done) c.soft.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainerLow, label = "bg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(onClick = { if (item.empty) onOpen() else onToggle() }, onLongClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(50)).background(c.main))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.item.label, style = MaterialTheme.typography.labelMedium, color = c.main, fontWeight = FontWeight.Bold)
            Text(
                if (item.empty) "+ اكتبها" else item.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.empty) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            item.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            countsLine(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        item.grade?.let {
            GradeBadge(it)
            Spacer(Modifier.width(8.dp))
        }
        if (!item.empty) CheckCircle(item.done, c.main, 34.dp)
    }
}

/** «خطآن · تنبيه واحد» from reciting it, or null when none were counted. */
internal fun countsLine(item: ItemUi): String? {
    val m = item.mistakes ?: 0
    val a = item.alerts ?: 0
    if (m == 0 && a == 0) return null
    return listOfNotNull(Plurals.mistakes(m).takeIf { m > 0 }, Plurals.alerts(a).takeIf { a > 0 }).joinToString(" · ")
}

@Composable
fun CheckCircle(done: Boolean, color: Color, size: Dp) {
    val scale by animateFloatAsState(if (done) 1f else 0.85f, spring(dampingRatio = 0.4f), label = "check")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.dp, if (done) color else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Default.Check, contentDescription = "تم", tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun GradeBadge(grade: GradeLevel) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(gradeColor(grade.score))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(grade.short, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- the plan week by week ----------

/**
 * Where the journey starts: the week of today's (or the next) session, or that session itself in
 * a plan without dates. 0 when there is nothing before it to fold.
 */
private fun currentStart(entries: List<TimelineEntry>, focusId: Long?): Int {
    if (focusId == null) return 0
    val at = entries.indexOfFirst { it is SessionEntry && it.row.session.id == focusId }
    if (at <= 0) return 0
    return (at downTo 0).firstOrNull { entries[it] is WeekEntry } ?: at
}

/** The weeks before the current one, folded into a line that opens them. */
@Composable
private fun PastLine(past: List<TimelineEntry>, expanded: Boolean, onToggle: () -> Unit) {
    val weeks = past.count { it is WeekEntry }
    val sessions = past.filterIsInstance<SessionEntry>().map { it.row }
    val done = sessions.count { it.mainDone }
    val missed = sessions.count { it.missed }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "past")
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A check once every session before this week is done; a calendar while some are not.
        val allDone = done == sessions.size
        val tint = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (allDone) rememberVectorPainter(Icons.Default.Check) else painterResource(R.drawable.ic_calendar),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val what = if (weeks > 0) "الأسابيع السابقة" else "الجلسات السابقة"
            Text(
                if (expanded) "إخفاء $what" else what,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row {
                Text(
                    if (LocalHideNumbers.current) {
                        if (weeks > 0) Plurals.weeks(weeks) else Plurals.sessions(sessions.size)
                    } else {
                        "${if (weeks > 0) Plurals.weeks(weeks) else Plurals.sessions(sessions.size)} · أنجزت $done من ${sessions.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missed > 0) {
                    Text(
                        " · ${Plurals.missed(missed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "إخفاء" else "عرض",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn),
        )
    }
}

/** Where a line sits in its week's card, so the card's corners round at its top and bottom. */
private enum class Edge { TOP, MIDDLE, BOTTOM, ALONE }

private fun groupEdges(entries: List<TimelineEntry>): Map<String, Edge> = entries.withIndex().associate { (i, entry) ->
    val starts = i == 0 || entry is WeekEntry
    val ends = i == entries.lastIndex || entries[i + 1] is WeekEntry
    entry.key to when {
        starts && ends -> Edge.ALONE
        starts -> Edge.TOP
        ends -> Edge.BOTTOM
        else -> Edge.MIDDLE
    }
}

/** One line of a week's card: the lines of a week draw together as one rounded card. */
@Composable
private fun GroupPart(edge: Edge, content: @Composable () -> Unit) {
    val round = 22.dp
    val top = if (edge == Edge.TOP || edge == Edge.ALONE) round else 0.dp
    val bottom = if (edge == Edge.BOTTOM || edge == Edge.ALONE) round else 0.dp
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (bottom > 0.dp) 12.dp else 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

@Composable
private fun WeekHeader(entry: WeekEntry) {
    val complete = entry.done == entry.total
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("الأسبوع ${entry.number}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text(
            when {
                complete -> if (LocalHideNumbers.current) "✓" else "✓ ${entry.done}/${entry.total}"
                LocalHideNumbers.current -> ""
                else -> "${entry.done}/${entry.total}"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (complete) FontWeight.Bold else FontWeight.Normal,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A session: its day on the side, and its items with a check each. */
@Composable
private fun SessionRow(row: RowUi, divider: Boolean, onToggle: (SessionEntity, Item) -> Unit, onOpenRow: (Int) -> Unit) {
    val today = row.isToday && !row.allDone
    Column(Modifier.fillMaxWidth().background(if (today) LocalDesign.current.accent.copy(alpha = 0.10f) else Color.Transparent)) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DayBlock(row) { onOpenRow(row.index) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Nothing to do («—») is left out; a review still to be written shows as a small «+».
                row.items.filter { !it.offDay && (!it.empty || it.writable) }.forEach { item ->
                    LineItem(item, onToggle = { onToggle(row.session, item.item) }, onOpen = { onOpenRow(row.index) })
                }
                if (!row.session.note.isNullOrBlank()) {
                    Text(
                        "📝 ${row.session.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

/** The day of a session: «اليوم» or the weekday, the date, and the Hijri date or «فاتت». */
@Composable
private fun DayBlock(row: RowUi, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(58.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!row.hasDate) {
            Text("جلسة", style = MaterialTheme.typography.labelSmall, color = muted)
            Text("${row.index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            return@Column
        }
        when {
            row.isToday -> Text("اليوم", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LocalDesign.current.accentText)
            else -> Text(row.weekday, style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Text(
            row.dateShort,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                row.missed -> MaterialTheme.colorScheme.error
                row.allDone -> muted
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        when {
            row.missed -> Text("فاتت", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            row.hijriShort != null -> Text("${row.hijriShort} هـ", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/** An item of a session in the list: a dot of its track, the text, its grade and a check. */
@Composable
private fun LineItem(item: ItemUi, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LocalTracks.current.of(item.item)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { if (item.empty) onOpen() else onToggle() }, onLongClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (item.empty) c.main.copy(alpha = 0.35f) else c.main))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.empty) "+ ${item.item.label.removePrefix("المراجعة ")}" else item.text,
                style = if (item.empty) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                color = when {
                    item.empty -> MaterialTheme.colorScheme.outline
                    item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            item.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            countsLine(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        }
        item.grade?.let {
            GradeBadge(it)
            Spacer(Modifier.width(6.dp))
        }
        if (!item.empty) CheckCircle(item.done, c.main, 26.dp)
    }
}
