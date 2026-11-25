@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sallo.hifz.HifzApp
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.Overview
import com.sallo.hifz.plan.Overviews
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.Loading
import com.sallo.hifz.ui.components.ProgressBar
import com.sallo.hifz.ui.table.HeroStat
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.heroBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** «إحصائياتي»: every plan's numbers together. */
@Composable
fun OverviewScreen(app: HifzApp, onBack: () -> Unit, onBadges: () -> Unit, onOpenRow: (Long, Int) -> Unit) {
    val plans by app.repository.plans.collectAsStateWithLifecycle(emptyList())
    val sessions by app.repository.allSessions.collectAsStateWithLifecycle(emptyList())
    val overview by produceState<Overview?>(null, plans, sessions) {
        value = withContext(Dispatchers.Default) {
            val byPlan = sessions.groupBy { it.planId }
            Overviews.compute(plans.map { planFacts(it, byPlan[it.id].orEmpty()) }, LocalDate.now())
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("إحصائياتي") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        val o = overview
        if (o == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Memorized(o)
            Section("الالتزام") {
                Line("الجلسات المنجزة", "${o.sessionsDone} من ${o.sessionsTotal}")
                Line("أطول سلسلة", o.bestStreak.toString())
                Line("السلسلة الحالية", o.currentStreak.toString())
                o.firstDay?.let { Line("بدأت", DateText.long(it)) }
            }
            Section("آخر 7 أيام") { LastWeek(o) }
            if (o.weekdays.any { it.second != null }) Section("أكثر أيامك التزاماً") { Weekdays(o) }
            if (o.topMistakes.isNotEmpty() || o.mistakesAverage != null) {
                Section("الأخطاء") {
                    o.mistakesAverage?.let { Line("متوسط الأخطاء في التسميع (آخر 30 يوم)", String.format(Locale.US, "%.1f", it)) }
                    if (o.topMistakes.isNotEmpty()) {
                        Text("أكثر الدروس أخطاءً", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                        o.topMistakes.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenRow(m.planId, m.idx) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.lesson, fontWeight = FontWeight.Bold)
                                    Text("${m.planName} · ${DateText.withWeekday(m.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Plurals.mistakes(m.mistakes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            FilledTonalButton(onClick = onBadges, modifier = Modifier.fillMaxWidth()) { Text("🏅 الأوسمة") }
        }
    }
}

/** What has been memorized, across plans: the hero of the page. */
@Composable
private fun Memorized(o: Overview) {
    val design = LocalDesign.current
    Column(
        Modifier.fillMaxWidth().heroBackground(design, 28.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("حفظت", style = MaterialTheme.typography.titleMedium, color = design.onHero.copy(alpha = 0.85f))
        Text(
            when {
                o.juz > 0 -> Plurals.juz(o.juz)
                o.pages >= 1 -> "${Plurals.number(Math.round(o.pages * 10.0) / 10.0)} وجه"
                else -> Plurals.ayahs(o.ayahs)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = design.onHero,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat(o.ayahs.toString(), "آية", Modifier.weight(1f))
            HeroStat(Plurals.number(Math.round(o.pages * 10.0) / 10.0), "وجه", Modifier.weight(1f))
            HeroStat(o.juz.toString(), "جزء كامل", Modifier.weight(1f))
        }
    }
}

/** Seven columns, oldest first: how much of each day's sessions was done. */
@Composable
private fun LastWeek(o: Overview) {
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        o.last7.forEach { day ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.due == 0) "" else "${day.done}/${day.due}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.weight(1f).width(22.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val share = if (day.due == 0) 0f else day.done.toFloat() / day.due
                    if (share > 0f) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(share).clip(RoundedCornerShape(8.dp))
                                .background(if (share >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    DateText.weekday(day.date).removePrefix("ال").take(2),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (day.date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Each weekday's share of its due days done, over the last 90 days. */
@Composable
private fun Weekdays(o: Overview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        o.weekdays.forEach { (day, rate) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateText.weekday(day), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                ProgressBar(rate ?: 0f, Modifier.weight(1f), height = 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    rate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}
