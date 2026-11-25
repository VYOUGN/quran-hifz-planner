@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.table

import com.sallo.hifz.ui.theme.LocalHideNumbers
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.ui.components.ProgressBar
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.LocalTableColors
import com.sallo.hifz.ui.theme.LocalTracks

private val DATE_WIDTH: Dp = 64.dp
private val CORNER: Dp = 16.dp

private fun shortLabel(item: Item): String = when (item) {
    Item.NEW -> "الحفظ"
    Item.MINOR -> "الصغرى"
    Item.MAJOR -> "الكبرى"
    Item.PREP -> "التحضير"
}

/** The paper-style table, with only the columns the plan includes. */
@Composable
fun GridView(
    state: TableState,
    onToggle: (SessionEntity, Item) -> Unit,
    onOpenRow: (Int) -> Unit,
    onStats: () -> Unit,
    onReschedule: () -> Unit,
) {
    val columns = state.rows.firstOrNull()?.items?.map { it.item }.orEmpty()
    // With two columns or fewer there is room for the check beside the text.
    val roomy = columns.size <= 2
    val listState = rememberLazyListState()
    LaunchedEffect(state.plan?.id) {
        val target = state.focus ?: state.rows.lastIndex
        if (target > 0) listState.scrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        state.stats?.let { ProgressStrip(it, LocalHideNumbers.current, onStats) }
        if (state.missed > 0) MissedStrip(state.missed, onReschedule)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            stickyHeader { HeaderRow(columns) }
            itemsIndexed(state.rows, key = { _, row -> row.session.id }) { i, row ->
                GridRow(row, odd = i % 2 == 1, last = i == state.rows.lastIndex, roomy = roomy, onToggle = onToggle, onOpenRow = onOpenRow)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ProgressStrip(stats: PlanStats, hide: Boolean, onStats: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onStats)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!hide) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "المنجز ${stats.done} من ${Plurals.sessions(stats.total)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(Plurals.percent(stats.progress), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        ProgressBar(stats.progress)
    }
}

@Composable
private fun MissedStrip(count: Int, onMove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        Text(Plurals.missed(count), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        TextButton(onClick = onMove) { Text("ترحيل") }
    }
}

/** Light lines: the bottom and end edge of every cell, plus the start edge where asked. */
private fun Modifier.gridLines(color: Color, start: Boolean = false): Modifier = drawBehind {
    val w = 1.dp.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    val endX = if (rtl) w / 2 else size.width - w / 2
    val startX = if (rtl) size.width - w / 2 else w / 2
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
    drawLine(color, Offset(endX, 0f), Offset(endX, size.height), w)
    if (start) drawLine(color, Offset(startX, 0f), Offset(startX, size.height), w)
}

/** Today's bar along the start edge of the row. */
private fun Modifier.todayBar(color: Color): Modifier = drawBehind {
    val w = 4.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
    drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
}

@Composable
private fun lineColor(): Color = LocalTableColors.current.grid.copy(alpha = 0.55f)

@Composable
private fun HeaderRow(columns: List<Item>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topStart = CORNER, topEnd = CORNER))
            .background(LocalTableColors.current.header),
    ) {
        HeaderCell("التاريخ", null, Modifier.width(DATE_WIDTH), start = true)
        columns.forEach { HeaderCell(shortLabel(it), LocalTracks.current.of(it).main, Modifier.weight(1f)) }
    }
}

@Composable
private fun HeaderCell(text: String, dot: Color?, modifier: Modifier, start: Boolean = false) {
    Row(
        modifier
            .fillMaxHeight()
            .gridLines(lineColor(), start = start)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun GridRow(
    row: RowUi,
    odd: Boolean,
    last: Boolean,
    roomy: Boolean,
    onToggle: (SessionEntity, Item) -> Unit,
    onOpenRow: (Int) -> Unit,
) {
    val open = { onOpenRow(row.index) }
    val accent = LocalDesign.current.accent
    val background = when {
        row.isToday -> accent.copy(alpha = 0.08f)
        odd -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (last) Modifier.clip(RoundedCornerShape(bottomStart = CORNER, bottomEnd = CORNER)) else Modifier)
            .background(background)
            .then(if (row.isToday) Modifier.todayBar(accent) else Modifier),
    ) {
        DateCell(row, Modifier.width(DATE_WIDTH).fillMaxHeight(), open)
        row.items.forEach { item ->
            ItemCell(
                item = item,
                missed = row.isPast && !item.empty && !item.done,
                roomy = roomy,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { if (item.empty) open() else onToggle(row.session, item.item) },
                onLongClick = open,
            )
        }
    }
}

@Composable
private fun DateCell(row: RowUi, modifier: Modifier, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .gridLines(lineColor(), start = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!row.hasDate) {
            Text("جلسة", style = MaterialTheme.typography.labelMedium, color = muted)
            Text("${row.index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            return@Column
        }
        if (row.isToday) {
            Text("اليوم", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LocalDesign.current.accentText)
        } else {
            Text(row.weekday, style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Text(
            row.dateShort,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (row.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        row.hijriShort?.let { Text("$it هـ", fontSize = 10.sp, color = muted) }
        if (!row.session.note.isNullOrBlank()) Text("📝", fontSize = 10.sp)
    }
}

@Composable
private fun ItemCell(item: ItemUi, missed: Boolean, roomy: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalTracks.current.of(item.item)
    val background = when {
        item.offDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val text: @Composable () -> Unit = {
        Column {
            Text(
                when {
                    item.offDay -> ""
                    !item.empty -> item.text
                    item.writable -> "+ اكتبها"
                    else -> "—"
                },
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = when {
                    item.empty -> MaterialTheme.colorScheme.outline
                    item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            item.sub?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    Box(
        modifier
            .background(background)
            .gridLines(lineColor())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        if (item.empty) {
            text()
        } else if (roomy) {
            Row(verticalAlignment = Alignment.Top) {
                CheckCircle(item.done, c.main, 20.dp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    text()
                    item.grade?.let { GradeBadge(it) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CheckCircle(item.done, c.main, 18.dp)
                    item.grade?.let { GradeBadge(it) }
                }
                text()
            }
        }
    }
}
