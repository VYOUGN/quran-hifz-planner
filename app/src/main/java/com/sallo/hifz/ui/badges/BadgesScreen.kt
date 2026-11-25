@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sallo.hifz.HifzApp
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.plan.Badge
import com.sallo.hifz.plan.BadgeInput
import com.sallo.hifz.plan.Badges
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.ProgressBar
import com.sallo.hifz.ui.theme.LocalDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** «الأوسمة»: the ones earned, with their day, and how far along the rest are. */
@Composable
fun BadgesScreen(app: HifzApp, onBack: () -> Unit) {
    val plans by app.repository.plans.collectAsStateWithLifecycle(emptyList())
    val sessions by app.repository.allSessions.collectAsStateWithLifecycle(emptyList())
    val earned by app.badges.earned.collectAsStateWithLifecycle()
    val input by produceState<BadgeInput?>(null, plans, sessions) {
        value = withContext(Dispatchers.Default) {
            val byPlan = sessions.groupBy { it.planId }
            Badges.input(plans.map { planFacts(it, byPlan[it.id].orEmpty()) }, LocalDate.now())
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("الأوسمة") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "أخذت ${earned.size} من ${Badge.entries.size} وسام",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            items(Badge.entries.sortedBy { if (it in earned) 0 else 1 }) { badge ->
                BadgeTile(badge, earned[badge], input)
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, earnedOn: LocalDate?, input: BadgeInput?) {
    val accent = LocalDesign.current.accent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (earnedOn != null) Modifier.border(1.5.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (earnedOn != null) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge.emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (earnedOn != null) 1f else 0.35f))
        }
        Text(badge.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        if (earnedOn != null) {
            Text(DateText.dayAndMonth(earnedOn), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(badge.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
            input?.let {
                val p = badge.progress(it)
                ProgressBar(p.toFloat() / badge.target, height = 4.dp)
                Text("$p من ${badge.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A new badge, celebrated once: shown over whatever screen is open. */
@Composable
fun NewBadgeDialog(badge: Badge, onShow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(badge.emoji, fontSize = 44.sp) },
        title = { Text("وسام جديد: ${badge.title}", textAlign = TextAlign.Center) },
        text = { Text("${badge.hint}. ما شاء الله، استمر!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("الحمد لله") } },
        dismissButton = { TextButton(onClick = onShow) { Text("الأوسمة") } },
    )
}
