@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.CatchUpOptions
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.CatchUp
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.LabeledRow
import com.sallo.hifz.ui.components.PickDateDialog
import com.sallo.hifz.ui.components.Pill
import java.time.LocalDate

/**
 * «ترحيل الدروس الفائتة»: what was missed, and three ways to carry on, each with what it does to the
 * plan's dates. [separateDays]: the reviews have their own days, so they are planned again too.
 */
@Composable
fun CatchUpSheet(
    plan: PlanEntity,
    options: CatchUpOptions,
    separateDays: Boolean,
    onApply: (CatchUp, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var method by remember { mutableStateOf(CatchUp.SHIFT) }
    var start by remember { mutableStateOf(options.shiftStart) }
    var picking by remember { mutableStateOf(false) }
    val missed = options.missed

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ترحيل الدروس الفائتة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(Plurals.missed(missed.size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                missed.take(SHOWN).forEach { row ->
                    Text(
                        "${DateText.withWeekday(row.localDate)}: ${PlanText.main(row, plan)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (missed.size > SHOWN) Hint("وفيه ${Plurals.lessons(missed.size - SHOWN)} غيرها")
            }

            val reviews = if (separateDays) " والمراجعات تترتب من جديد على أيامها." else ""
            Choice(
                selected = method == CatchUp.SHIFT,
                title = "كمّلها من جديد",
                text = "الدروس تمشي على أيامك ابتداءً من ${DateText.withWeekday(start)}، واللي بعدها يتأخر معها.$reviews " +
                    "الختم يصير ${DateText.long(options.shiftEnd(start))} بدل ${DateText.long(options.end)}.",
                onClick = { method = CatchUp.SHIFT },
            ) {
                LabeledRow("تبدأ") {
                    Pill(DateText.withWeekday(start), onClick = { picking = true }, dropdown = true)
                }
            }
            options.dailyUntil?.let { until ->
                Choice(
                    selected = method == CatchUp.DAILY,
                    title = "عوّضها بجلسة كل يوم",
                    text = "جلسة كل يوم من اليوم لين ${DateText.withWeekday(until)}، وبعدها ترجع لجدولك.$reviews " +
                        "والختم يبقى ${DateText.long(options.end)}.",
                    onClick = { method = CatchUp.DAILY },
                )
            }
            Choice(
                selected = method == CatchUp.MARK_DONE,
                title = "حفظتها بس ما علّمتها",
                text = "تتعلّم كمنجزة في أيامها، وما يتغيّر شي في الجدول.",
                onClick = { method = CatchUp.MARK_DONE },
            )

            Button(
                onClick = { onApply(method, start.takeIf { method == CatchUp.SHIFT }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (method == CatchUp.MARK_DONE) "علّمها كمنجزة" else "رحّل الدروس") }
            Hint("وإذا غيّرت رأيك، تقدر تتراجع من الشريط اللي يطلع تحت.")
        }
    }

    if (picking) {
        PickDateDialog(
            initial = start,
            onDismiss = { picking = false },
            onPick = {
                start = maxOf(it, today)
                picking = false
            },
        )
    }
}

private const val SHOWN = 4

/** A way to carry on: a radio choice with what it does, and its own controls once chosen. */
@Composable
private fun Choice(
    selected: Boolean,
    title: String,
    text: String,
    onClick: () -> Unit,
    controls: (@Composable () -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected && controls != null) controls()
    }
}
