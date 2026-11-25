@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.table

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sallo.hifz.HifzApp
import com.sallo.hifz.calendar.CalendarStyle
import com.sallo.hifz.calendar.CalendarSync
import com.sallo.hifz.calendar.DeviceCalendar
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.CalendarLinkEntity
import com.sallo.hifz.ui.components.DropdownPill
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.InfoCard
import com.sallo.hifz.ui.components.LabeledRow
import com.sallo.hifz.ui.components.Pill
import com.sallo.hifz.ui.editor.Choices
import com.sallo.hifz.ui.settings.TimeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The plan's link to a calendar once it has been read: [link] null means not in a calendar. */
data class LinkState(val link: CalendarLinkEntity?)

class CalendarViewModel(private val app: HifzApp, private val planId: Long) : ViewModel() {
    private val dao = app.database.calendarDao()

    /** Null until read. */
    val link: StateFlow<LinkState?> = dao.linkFlow(planId).map { LinkState(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val count: StateFlow<Int> = dao.countFlow(planId).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _calendars = MutableStateFlow<List<DeviceCalendar>?>(null)
    val calendars: StateFlow<List<DeviceCalendar>?> = _calendars.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadCalendars() {
        viewModelScope.launch { _calendars.value = withContext(Dispatchers.IO) { app.calendar.calendars() } }
    }

    /** A result from the last time the sheet was open is not news any more. */
    fun clearMessage() {
        if (!_busy.value) _message.value = null
    }

    /** Makes (or finds) the phone-only calendar «جدول الحفظ» and hands its id to [onReady]. */
    fun useLocalCalendar(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { app.calendar.localCalendar() }.getOrNull() }
            _calendars.value = withContext(Dispatchers.IO) { app.calendar.calendars() }
            if (id != null) onReady(id) else _message.value = "ما قدر التطبيق يسوي التقويم"
        }
    }

    fun save(calendarId: Long, style: CalendarStyle) {
        work {
            val n = app.calendar.link(planId, calendarId, style)
            "صار الجدول في التقويم: ${Plurals.sessions(n)}"
        }
    }

    fun remove() {
        work {
            app.calendar.unlink(planId)
            "انشالت جلسات الخطة من التقويم"
        }
    }

    private fun work(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            _message.value = try {
                block()
            } catch (e: Exception) {
                "ما قدر التطبيق يكتب في التقويم، جرّب مرة ثانية"
            }
            _busy.value = false
        }
    }
}

/**
 * «نقل إلى التقويم»: puts every session of the plan in a calendar on the phone, where it follows the
 * table from then on. [sessions]: how many rows the plan has.
 */
@Composable
fun CalendarSheet(app: HifzApp, planId: Long, sessions: Int, onDismiss: () -> Unit) {
    val vm: CalendarViewModel = viewModel(key = "calendar-$planId", factory = viewModelFactory { initializer { CalendarViewModel(app, planId) } })
    val linkState by vm.link.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var granted by remember { mutableStateOf(CalendarSync.hasPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        granted = CalendarSync.hasPermission(context)
    }
    LaunchedEffect(granted) { if (granted) vm.loadCalendars() }
    LaunchedEffect(Unit) { vm.clearMessage() }

    val link = linkState?.link
    var chosen by remember(link) { mutableStateOf(link?.calendarId) }
    var style by remember(link) {
        val prefs = app.prefs.values.value
        mutableStateOf(link?.style ?: CalendarStyle(minuteOfDay = prefs.reminderHour * 60 + prefs.reminderMinute))
    }
    LaunchedEffect(calendars, linkState) {
        // The app's own calendar if it was made before, otherwise the account's main calendar.
        val list = calendars
        if (linkState != null && chosen == null && list != null) {
            chosen = (list.firstOrNull { it.local && it.account == CalendarSync.LOCAL_ACCOUNT } ?: list.firstOrNull())?.id
        }
    }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("نقل الجدول إلى التقويم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint(
                "كل جلسة من ${Plurals.sessions(sessions)} تصير موعد في تقويم جوالك. ولما تحط ✓ أو ترحّل أو تأجّل " +
                    "أو تعدّل الخطة، يتحدّث التقويم تلقائياً.",
            )
            if (link != null) {
                val name = calendars?.firstOrNull { it.id == link.calendarId }?.name
                InfoCard(
                    if (name != null) "الجدول في تقويم «$name»: ${Plurals.sessions(count)}" else "الجدول في التقويم: ${Plurals.sessions(count)}",
                )
            }
            if (!granted) {
                Text("عشان يكتب الجلسات في تقويمك، يحتاج التطبيق إذن الوصول للتقويم.")
                Button(onClick = { ask.launch(CalendarSync.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("السماح بالوصول للتقويم")
                }
                if (asked) {
                    Hint("إذا ما طلع طلب الإذن، فعّله من إعدادات الجوال: التطبيقات ثم جدول الحفظ ثم الأذونات ثم التقويم.")
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("فتح إعدادات التطبيق") }
                }
            } else {
                Heading("التقويم")
                val list = calendars
                when {
                    list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    list.isEmpty() -> Hint("ما فيه تقويم في الجوال يقدر التطبيق يكتب فيه، فسوّ تقويم على الجوال:")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.forEach { c -> CalendarRow(c, c.id == chosen) { chosen = c.id } }
                    }
                }
                if (list != null && list.none { it.local && it.account == CalendarSync.LOCAL_ACCOUNT }) {
                    OutlinedButton(onClick = { vm.useLocalCalendar { chosen = it } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("+ تقويم جديد على الجوال: «${CalendarSync.LOCAL_ACCOUNT}»")
                    }
                    Hint("يكون على هذا الجوال بس، وتقدر تخفيه أو تظهره من تطبيق التقويم.")
                }

                Heading("وقت الجلسة")
                Choices(
                    options = listOf("طول اليوم", "وقت محدد"),
                    selected = if (style.allDay) 0 else 1,
                    onSelect = { style = style.copy(allDay = it == 0) },
                )
                if (style.allDay) {
                    Hint("موعد طول اليوم في كل يوم فيه جلسة.")
                } else {
                    LabeledRow("الساعة") {
                        Pill(PlanText.time(style.minuteOfDay / 60, style.minuteOfDay % 60), onClick = { pickingTime = true })
                    }
                    LabeledRow("المدة") {
                        DropdownPill(
                            text = durationLabel(style.durationMinutes),
                            options = CalendarStyle.DURATIONS,
                            label = ::durationLabel,
                            selected = style.durationMinutes,
                            onSelect = { style = style.copy(durationMinutes = it) },
                        )
                    }
                    LabeledRow("التنبيه") {
                        DropdownPill(
                            text = alertLabel(style.alertMinutes),
                            options = CalendarStyle.ALERTS,
                            label = ::alertLabel,
                            selected = style.alertMinutes,
                            onSelect = { style = style.copy(alertMinutes = it) },
                        )
                    }
                    Hint("التنبيه من تطبيق التقويم، غير التذكير اليومي اللي في إعدادات جدول الحفظ.")
                }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (link != null) {
                        OutlinedButton(onClick = { confirmRemove = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text("إزالة من التقويم")
                        }
                    }
                    val target = chosen
                    Button(
                        onClick = { if (target != null) vm.save(target, style) },
                        enabled = !busy && target != null && linkState != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (link == null) "نقل إلى التقويم" else "حفظ التغييرات") }
                }
            }
        }
    }

    if (pickingTime) {
        TimeDialog(
            title = "وقت الجلسة",
            hour = style.minuteOfDay / 60,
            minute = style.minuteOfDay % 60,
            onDismiss = { pickingTime = false },
        ) { h, m ->
            style = style.copy(minuteOfDay = h * 60 + m)
            pickingTime = false
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("إزالة من التقويم") },
            text = { Text("تنشال جلسات هذي الخطة من التقويم، والجدول نفسه يبقى زي ما هو.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.remove()
                }) { Text("إزالة") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun CalendarRow(c: DeviceCalendar, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (c.local) "على هذا الجوال بس" else c.account,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

private fun durationLabel(minutes: Int): String = when (minutes) {
    15 -> "ربع ساعة"
    30 -> "نص ساعة"
    60 -> "ساعة"
    90 -> "ساعة ونص"
    120 -> "ساعتان"
    else -> "$minutes دقيقة"
}

private fun alertLabel(minutes: Int): String = when (minutes) {
    CalendarStyle.NO_ALERT -> "بدون تنبيه"
    0 -> "وقت الجلسة"
    30 -> "قبل نص ساعة"
    60 -> "قبل ساعة"
    else -> "قبل $minutes دقائق"
}
