@file:OptIn(ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.Consistency
import com.sallo.hifz.plan.DayState
import com.sallo.hifz.plan.PlanFacts
import com.sallo.hifz.ui.theme.LocalDesign
import java.time.LocalDate
import java.time.YearMonth

private val WEEK_LETTERS = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")

/** «تقويم الالتزام»: the plan's days month by month, Sunday first, with a legend and the month's tally. */
@Composable
fun DaysCalendar(facts: PlanFacts, today: LocalDate) {
    val states = remember(facts, today) { Consistency.days(facts.days, facts.calendar, today) }
    val first = remember(facts) { YearMonth.from(facts.days.minOfOrNull { it.date } ?: today) }
    val last = remember(facts) { YearMonth.from(facts.days.maxOfOrNull { it.date } ?: today) }
    var shown by rememberSaveable(facts.planId) { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(shown).let { if (it < first) first else if (it > last) last else it }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shown = month.minusMonths(1).toString() }, enabled = month > first) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الشهر اللي قبل")
            }
            Text(
                DateText.monthYear(month),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { shown = month.plusMonths(1).toString() }, enabled = month < last) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الشهر اللي بعد")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WEEK_LETTERS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val lead = month.atDay(1).dayOfWeek.value % 7
        val cells: List<Int?> = List(lead) { null } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val date = month.atDay(day)
                            DayCell(day, states[date], date == today)
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(MaterialTheme.colorScheme.primary, "منجز")
            Legend(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), "جزئي")
            Legend(MaterialTheme.colorScheme.errorContainer, "فائت")
            Legend(MaterialTheme.colorScheme.tertiaryContainer, "إجازة")
        }
        val (done, due) = Consistency.tally(states, month, today)
        if (due > 0) {
            Text(
                "أيام هذا الشهر المنجزة: $done من $due",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(day: Int, state: DayState?, today: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (fill, text) = when (state) {
        DayState.DONE -> scheme.primary to scheme.onPrimary
        DayState.PARTIAL -> scheme.primary.copy(alpha = 0.35f) to scheme.onSurface
        DayState.MISSED -> scheme.errorContainer to scheme.onErrorContainer
        DayState.VACATION -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DayState.TODAY, DayState.UPCOMING -> Color.Transparent to scheme.onSurface
        null -> Color.Transparent to scheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(fill)
            .then(if (today) Modifier.border(2.dp, LocalDesign.current.accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$day", style = MaterialTheme.typography.labelLarge, color = text, fontWeight = if (state != null) FontWeight.Bold else FontWeight.Normal)
            if (state == DayState.UPCOMING) Box(Modifier.size(4.dp).clip(CircleShape).background(scheme.outline))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
