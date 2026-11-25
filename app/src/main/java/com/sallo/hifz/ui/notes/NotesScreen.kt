@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.sallo.hifz.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.ui.components.BackButton

/** A note written on a session, with what it was about. */
private data class NoteLine(
    val planId: Long,
    val planName: String,
    val idx: Int,
    /** «الأحد 11/8», or «الجلسة 3» without dates. */
    val day: String,
    val sortKey: Long,
    val lesson: String,
    val note: String,
    val mistakes: Int?,
)

/** «دفتر الملاحظات»: every note on a session, newest first, by plan; [planId] shows one plan's only. */
@Composable
fun NotesScreen(app: HifzApp, planId: Long?, onBack: () -> Unit, onOpenRow: (Long, Int) -> Unit) {
    val plans by app.repository.plans.collectAsStateWithLifecycle(emptyList())
    val sessions by app.repository.allSessions.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(plans, sessions, planId) {
        val byId = plans.associateBy { it.id }
        sessions.mapNotNull { s ->
            val plan = byId[s.planId] ?: return@mapNotNull null
            if (planId != null && plan.id != planId) return@mapNotNull null
            val note = s.note?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            val dated = plan.toSettings().hasDates
            NoteLine(
                planId = plan.id,
                planName = plan.name,
                idx = s.idx,
                day = if (dated) DateText.withWeekday(s.localDate) else "الجلسة ${s.idx + 1}",
                sortKey = if (dated) s.date else s.idx.toLong(),
                lesson = PlanText.main(s, plan),
                note = note,
                mistakes = s.totalMistakes,
            )
        }.sortedByDescending { it.sortKey }
    }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.note.contains(q) || it.lesson.contains(q) || it.planName.contains(q) }
    }
    val title = if (planId != null) "ملاحظات ${plans.firstOrNull { it.id == planId }?.name.orEmpty()}" else "دفتر الملاحظات"

    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث في الملاحظات") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (all.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "ما عندك ملاحظات للحين. تكتبها من نافذة الجلسة: اضغط على تاريخ الجلسة، وتحت فيه «ملاحظة».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "none") {
                    Text("ما فيه ملاحظة فيها «${query.trim()}».", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
            val grouped = planId == null && shown.map { it.planId }.distinct().size > 1
            if (grouped) {
                shown.groupBy { it.planId }.forEach { (id, list) ->
                    stickyHeader(key = "plan$id") {
                        Text(
                            list.first().planName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp),
                        )
                    }
                    items(list, key = { "${it.planId}-${it.idx}" }) { NoteCard(it) { onOpenRow(it.planId, it.idx) } }
                }
            } else {
                items(shown, key = { "${it.planId}-${it.idx}" }) { NoteCard(it) { onOpenRow(it.planId, it.idx) } }
            }
        }
    }
}

@Composable
private fun NoteCard(line: NoteLine, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            line.mistakes?.takeIf { it > 0 }?.let {
                Text(Plurals.mistakes(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(line.lesson, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(line.note, style = MaterialTheme.typography.bodyLarge)
    }
}
