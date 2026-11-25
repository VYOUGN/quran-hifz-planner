@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.RowDraft
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.ui.theme.LocalTracks
import com.sallo.hifz.ui.theme.TrackColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * «اعرض الجلسات»: every generated session with its range, size and pages. A full screen over the
 * editor rather than a bottom sheet, so scrolling back up never closes it; ✕ or the back button does.
 */
@Composable
fun SessionsScreen(preview: PlanPreview, s: PlanSettings, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("خطتك جلسة جلسة", fontWeight = FontWeight.Bold)
                        Text(
                            "${Plurals.sessions(preview.sessions)} • ${Plurals.number(Math.round(preview.totalPages * 10.0) / 10.0)} وجه",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(scrolled, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "إلى أول جلسة")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(preview.rows, key = { i, _ -> i }) { i, row -> SessionPreview(i, row, s) }
        }
    }
}

@Composable
private fun SessionPreview(i: Int, row: RowDraft, s: PlanSettings) {
    val tracks = LocalTracks.current
    val label = when {
        !s.hasDates -> "الجلسة ${i + 1}"
        row.date == LocalDate.now() -> "اليوم"
        else -> DateText.withWeekday(row.date)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.lesson?.let { RangeLines("حفظ", it, tracks.new) }
                if (row.minor.isNotEmpty()) {
                    Text(
                        "مراجعة صغرى: ${RangeFormatter.formatAll(row.minor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tracks.minor.main,
                    )
                }
                if (row.majorParts.isNotEmpty()) RangeLines("مراجعة", row.majorParts, tracks.major)
            }
        }
    }
}

@Composable
private fun RangeLines(label: String, range: IntRange, color: TrackColor) = RangeLines(label, listOf(range), color)

/** The ranges of a session (a round's end and the next round's start when it goes on into it). */
@Composable
private fun RangeLines(label: String, ranges: List<IntRange>, color: TrackColor) {
    Text(RangeFormatter.formatAll(ranges), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val pages = Math.round(ranges.sumOf { Quran.weight(it) } * 10.0) / 10.0
    Text(
        "$label • ${Plurals.number(pages)} وجه (${ranges.joinToString("، ") { RangeFormatter.pageLabel(it, false) }})",
        style = MaterialTheme.typography.bodySmall,
        color = color.main,
    )
}
