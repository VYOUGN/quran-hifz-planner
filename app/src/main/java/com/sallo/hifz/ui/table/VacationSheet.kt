@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.table

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.hasMain
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.Vacation
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.LabeledRow
import com.sallo.hifz.ui.components.Pill
import com.sallo.hifz.ui.components.PickDateDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

/** «إجازة»: days with no sessions; the lessons wait until after them. */
@Composable
fun VacationSheet(app: HifzApp, planId: Long, onDismiss: () -> Unit) {
    val plan by remember(planId) { app.repository.plan(planId) }.collectAsStateWithLifecycle(null)
    val sessions by remember(planId) { app.repository.sessions(planId) }.collectAsStateWithLifecycle(emptyList())
    val plans by app.repository.plans.collectAsStateWithLifecycle(emptyList())
    val today = remember { LocalDate.now() }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today.plusDays(2)) }
    var forAll by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val p = plan ?: return
    val others = plans.filter { it.id != p.id && it.scheduleMode != ScheduleMode.ANYTIME.name }
    val vacations = Vacation.decode(p.vacations)
    val preview = remember(p, sessions, from, to) { preview(p, sessions, Vacation(from, to), today) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("إجازة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint("أيام الإجازة ما فيها جلسات ولا تذكير: الدروس تتأجل لبعدها، وما تنحسب فائتة، ولا تقطع «متتالية». وتقدر تسجّل أيام فاتت.")
            LabeledRow("من") { Pill(DateText.long(from), onClick = { picking = "from" }, dropdown = true) }
            LabeledRow("إلى") { Pill(DateText.long(to), onClick = { picking = "to" }, dropdown = true) }
            if (others.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable { forAll = !forAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                    Text("لكل خططي (${Plurals.plans(others.size + 1)})")
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                )
            }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val v = Vacation(from, to)
                        app.repository.addVacation(p, v, today)
                        if (forAll) others.forEach { app.repository.addVacation(it, v, today) }
                        onDismiss()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("أضف الإجازة") }
            if (vacations.isNotEmpty()) {
                Text("إجازات هذي الخطة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                vacations.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (v.start == v.end) DateText.long(v.start) else "${DateText.long(v.start)} – ${DateText.long(v.end)}")
                            Hint(Plurals.days(v.days))
                        }
                        IconButton(onClick = { scope.launch { app.repository.removeVacation(p, v, today) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الإجازة")
                        }
                    }
                }
                Hint("إذا حذفت إجازة، الدروس اللي ما خلصتها ترجع لأيامها.")
            }
        }
    }

    picking?.let { which ->
        PickDateDialog(
            initial = if (which == "from") from else to,
            onDismiss = { picking = null },
            onPick = { date ->
                if (which == "from") {
                    from = date
                    if (to < date) to = date
                } else {
                    to = date
                    if (from > date) from = date
                }
                picking = null
            },
        )
    }
}

/** What adding [v] would do to [plan]: which lessons wait, the first session after it, and the new end. */
private fun preview(plan: PlanEntity, sessions: List<SessionEntity>, v: Vacation, today: LocalDate): String {
    val settings = plan.toSettings()
    if (!settings.hasDates) return ""
    val calendar = settings.calendar.copy(off = settings.vacations + v)
    val main = sessions.filter { it.hasMain(plan) }
    val dates = main.map { it.localDate }
    val (first, start) = Missed.afterVacation(dates, main.map { it.mainDone(plan) }, v.start, today, calendar, pullBack = false)
        ?: return "ما فيه دروس تتأجل، والأيام تنحسب إجازة."
    val moved = Missed.shift(dates, first, start, calendar)
    return "الدروس من ${DateText.withWeekday(dates[first])} تتأجل، وأول جلسة بعد الإجازة ${DateText.withWeekday(start)}، " +
        "والختم يصير ${DateText.long(moved.last())}."
}
