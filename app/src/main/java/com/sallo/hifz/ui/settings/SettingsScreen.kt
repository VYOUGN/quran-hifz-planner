@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.settings

import com.sallo.hifz.ui.components.OptionRow
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sallo.hifz.HifzApp
import com.sallo.hifz.R
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Backup
import com.sallo.hifz.data.PlanBackup
import com.sallo.hifz.data.TextSize
import com.sallo.hifz.data.ThemeMode
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.reminder.ReminderScheduler
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.Stepper
import com.sallo.hifz.ui.editor.Choices
import com.sallo.hifz.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** A backup file read and waiting for «إضافة» or «استبدال»; [existing]: plans already in the app. */
class PendingImport(val plans: List<PlanBackup>, val existing: Int)

/** Backups, which read and write files away from the main thread. */
class SettingsViewModel(private val app: HifzApp) : ViewModel() {
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun save(uri: Uri) = work {
        val plans = app.repository.exportPlans()
        val text = Backup.write(plans, System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("no stream")
        }
        app.prefs.update { it.copy(lastBackup = System.currentTimeMillis()) }
        "حفظنا نسخة فيها ${Plurals.plans(plans.size)}"
    }

    fun open(uri: Uri) = work {
        val text = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        when (val result = text?.let { Backup.read(it) } ?: Backup.Result.NotBackup) {
            Backup.Result.NotBackup -> "الملف هذا ما هو نسخة احتياطية من جدول الحفظ"
            Backup.Result.TooNew -> "النسخة هذي من إصدار أحدث من التطبيق، حدّث التطبيق أول"
            is Backup.Result.Plans -> if (result.plans.isEmpty()) {
                "ما فيه خطط في هذي النسخة"
            } else {
                _pending.value = PendingImport(result.plans, app.repository.allPlans().size)
                null
            }
        }
    }

    fun restore(replace: Boolean) {
        val pending = _pending.value ?: return
        _pending.value = null
        work {
            app.repository.importPlans(pending.plans, replace)
            "رجعت ${Plurals.plans(pending.plans.size)} من النسخة"
        }
    }

    fun cancelRestore() {
        _pending.value = null
    }

    private fun work(block: suspend () -> String?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: Exception) {
                "ما قدرنا نكمل، جرّب مرة ثانية"
            }
            _busy.value = false
            message?.let { _message.emit(it) }
        }
    }
}

/** [onDesigns] opens «التصاميم والودجت». */
@Composable
fun SettingsScreen(app: HifzApp, onBack: () -> Unit, onDesigns: () -> Unit = {}) {
    val prefs by app.prefs.values.collectAsStateWithLifecycle()
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pickingTime by remember { mutableStateOf<TimeSetting?>(null) }

    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }
    // What asked for the notification permission: the daily reminder, or the weekly report.
    var askingFor by remember { mutableStateOf("reminder") }
    var pickingDay by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && askingFor == "weekly") {
            app.prefs.update { it.copy(weeklyReport = true) }
            ReminderScheduler.schedule(app)
        } else if (granted) {
            setReminder(app, true)
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.save(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.open(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("الإعدادات") }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(rememberVectorPainter(Icons.Default.Notifications), "التذكير") {
                SwitchLine(
                    title = "تذكير يومي",
                    subtitle = "إشعار في الأيام اللي فيها جلسة ما خلصت، ويذكر الدروس الفائتة",
                    checked = prefs.reminderEnabled,
                ) { on ->
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (on && needsPermission) {
                        askingFor = "reminder"
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        setReminder(app, on)
                    }
                }
                if (prefs.reminderEnabled) {
                    ValueLine("وقت التذكير", PlanText.time(prefs.reminderHour, prefs.reminderMinute)) { pickingTime = TimeSetting.REMINDER }
                    Divider()
                    SwitchLine(
                        title = "تذكير ثاني إذا ما خلصت",
                        subtitle = "يذكرك مرة ثانية آخر اليوم إذا لسا ما خلصت جلسة اليوم",
                        checked = prefs.followUpEnabled,
                    ) { on ->
                        app.prefs.update { it.copy(followUpEnabled = on) }
                        ReminderScheduler.schedule(app)
                    }
                    if (prefs.followUpEnabled) {
                        ValueLine("وقت التذكير الثاني", PlanText.time(prefs.followUpHour, prefs.followUpMinute)) {
                            pickingTime = TimeSetting.FOLLOW_UP
                        }
                    }
                }
                Divider()
                SwitchLine(
                    title = "تقرير أسبوعي",
                    subtitle = "ملخص آخر 7 أيام: كم جلسة خلصت، ووش فاتك، والمتتالية، وكم جلسة في الأسبوع الجاي",
                    checked = prefs.weeklyReport,
                ) { on ->
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (on && needsPermission) {
                        askingFor = "weekly"
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        app.prefs.update { it.copy(weeklyReport = on) }
                        ReminderScheduler.schedule(app)
                    }
                }
                if (prefs.weeklyReport) {
                    ValueLine("يوم التقرير", DateText.weekday(prefs.weeklyDay)) { pickingDay = true }
                    ValueLine("وقت التقرير", PlanText.time(prefs.weeklyHour, prefs.weeklyMinute)) { pickingTime = TimeSetting.WEEKLY }
                }
            }

            Section(rememberVectorPainter(Icons.Default.Refresh), "الترحيل") {
                SwitchLine(
                    title = "رحّل الدروس الفائتة تلقائياً",
                    subtitle = "للخطط الجديدة: كل يوم الدروس اللي فاتتك تنتقل لأول يوم حفظ جاي، حتى لو ما فتحت التطبيق",
                    checked = prefs.autoCatchUp,
                ) { on -> app.prefs.update { it.copy(autoCatchUp = on) } }
                Hint(
                    "وإذا كان مطفّى، يطلع لك شريط «ترحيل» وتختار: تكمّلها من جديد، أو تعوّضها بجلسة كل يوم لين ترجع لجدولك، " +
                        "أو تعلّمها كمنجزة. ولكل خطة تغيّره من «تعديل الخطة».",
                )
            }

            Section(painterResource(R.drawable.ic_palette), "المظهر") {
                ActionLine(
                    title = "التصاميم والودجت",
                    subtitle = "${prefs.design.label} • خط ${prefs.font.label} • ودجت ${prefs.widgetLook.label}",
                    enabled = true,
                    onClick = onDesigns,
                )
                Divider()
                Label("الألوان")
                Choices(
                    options = ThemeMode.entries.map { it.label },
                    selected = prefs.theme.ordinal,
                    onSelect = { i ->
                        app.prefs.update { it.copy(theme = ThemeMode.entries[i]) }
                        // The widgets follow the app's light or dark choice too.
                        Widgets.refresh(app)
                    },
                )
                Label("حجم الخط")
                Choices(
                    options = TextSize.entries.map { it.label },
                    selected = prefs.textSize.ordinal,
                    onSelect = { i -> app.prefs.update { it.copy(textSize = TextSize.entries[i]) } },
                )
                Hint("يكبّر النصوص في كل التطبيق، فوق حجم الخط اللي في إعدادات الجوال.")
                Divider()
                SwitchLine(
                    title = "احتفال عند الإنجاز",
                    subtitle = "قصاصات ملوّنة وكلمة تشجيع لما تخلص جلسة",
                    checked = prefs.celebrate,
                ) { on -> app.prefs.update { it.copy(celebrate = on) } }
                SwitchLine(
                    title = "اهتزاز خفيف عند التأشير",
                    subtitle = "لما تحط ✓ أو تشيلها",
                    checked = prefs.haptics,
                ) { on -> app.prefs.update { it.copy(haptics = on) } }
                SwitchLine(
                    title = "إخفاء الأرقام",
                    subtitle = "بدون نسب ولا عدّ ولا متتالية في الرئيسية وصفحة الخطة والودجت، وتحذير الفائت يبقى",
                    checked = prefs.hideNumbers,
                ) { on ->
                    app.prefs.update { it.copy(hideNumbers = on) }
                    Widgets.refresh(app)
                }
            }

            Section(rememberVectorPainter(Icons.Default.DateRange), "التاريخ الهجري") {
                SwitchLine(
                    title = "إظهار التاريخ الهجري",
                    subtitle = "تحت التاريخ الميلادي، حسب تقويم أم القرى",
                    checked = prefs.showHijri,
                ) { on -> app.prefs.update { it.copy(showHijri = on) } }
                if (prefs.showHijri) {
                    Label("تعديل الهجري")
                    Stepper(
                        text = offsetLabel(prefs.hijriOffset),
                        canDecrease = prefs.hijriOffset > -2,
                        canIncrease = prefs.hijriOffset < 2,
                        onDecrease = { app.prefs.update { it.copy(hijriOffset = it.hijriOffset - 1) } },
                        onIncrease = { app.prefs.update { it.copy(hijriOffset = it.hijriOffset + 1) } },
                    )
                    // Read after the change: prefs carries the offset that Hijri now uses.
                    val today = remember(prefs.hijriOffset) { Hijri.of(LocalDate.now()).long }
                    Hint("اليوم $today. عدّله إذا كان الشهر عندكم يبدأ قبل تقويم أم القرى أو بعده.")
                }
            }

            Section(painterResource(R.drawable.ic_save), "النسخ الاحتياطي") {
                ActionLine(
                    title = "حفظ نسخة احتياطية",
                    subtitle = if (prefs.lastBackup == 0L) {
                        "ما حفظت نسخة للحين"
                    } else {
                        "آخر نسخة: ${DateText.long(Instant.ofEpochMilli(prefs.lastBackup).atZone(ZoneId.systemDefault()).toLocalDate())}"
                    },
                    enabled = !busy,
                ) { saveBackup.launch("جدول-الحفظ-${LocalDate.now()}.json") }
                ActionLine(
                    title = "استرجاع نسخة",
                    subtitle = "ترجّع الخطط وكل اللي سجلته فيها من ملف نسخة",
                    enabled = !busy,
                ) { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Hint("الخطط محفوظة في جوالك بس، فاحفظ نسخة قبل ما تغيّر الجوال أو تحذف التطبيق، وحطها في مكان آمن مثل Drive.")
            }

            Section(rememberVectorPainter(Icons.Default.Info), "عن التطبيق") { About() }
        }
    }

    pickingTime?.let { which ->
        TimeDialog(
            title = when (which) {
                TimeSetting.FOLLOW_UP -> "وقت التذكير الثاني"
                TimeSetting.WEEKLY -> "وقت التقرير الأسبوعي"
                TimeSetting.REMINDER -> "وقت التذكير"
            },
            hour = when (which) {
                TimeSetting.FOLLOW_UP -> prefs.followUpHour
                TimeSetting.WEEKLY -> prefs.weeklyHour
                TimeSetting.REMINDER -> prefs.reminderHour
            },
            minute = when (which) {
                TimeSetting.FOLLOW_UP -> prefs.followUpMinute
                TimeSetting.WEEKLY -> prefs.weeklyMinute
                TimeSetting.REMINDER -> prefs.reminderMinute
            },
            onDismiss = { pickingTime = null },
        ) { h, m ->
            app.prefs.update {
                when (which) {
                    TimeSetting.FOLLOW_UP -> it.copy(followUpHour = h, followUpMinute = m)
                    TimeSetting.WEEKLY -> it.copy(weeklyHour = h, weeklyMinute = m)
                    TimeSetting.REMINDER -> it.copy(reminderHour = h, reminderMinute = m)
                }
            }
            ReminderScheduler.schedule(app)
            pickingTime = null
        }
    }

    if (pickingDay) {
        AlertDialog(
            onDismissRequest = { pickingDay = false },
            title = { Text("يوم التقرير") },
            text = {
                Column {
                    DateText.WEEK_ORDER.forEach { day ->
                        OptionRow(DateText.weekday(day), "", prefs.weeklyDay == day) {
                            app.prefs.update { it.copy(weeklyDay = day) }
                            ReminderScheduler.schedule(app)
                            pickingDay = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingDay = false }) { Text("إلغاء") } },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("استرجاع نسخة احتياطية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("في النسخة ${Plurals.plans(p.plans.size)}: ${p.plans.joinToString("، ") { it.plan.name }}.")
                    if (p.existing > 0) {
                        Text("تضيفها لخططك، أو تستبدل فيها خططك الحالية؟ الاستبدال يحذف ${Plurals.plans(p.existing)} موجودة الحين.")
                    }
                }
            },
            confirmButton = {
                Row {
                    if (p.existing > 0) TextButton(onClick = { vm.restore(replace = true) }) { Text("استبدال") }
                    TextButton(onClick = { vm.restore(replace = false) }) { Text(if (p.existing > 0) "إضافة" else "استرجاع") }
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("إلغاء") } },
        )
    }
}

private enum class TimeSetting { REMINDER, FOLLOW_UP, WEEKLY }

private fun setReminder(app: HifzApp, on: Boolean) {
    app.prefs.update { it.copy(reminderEnabled = on) }
    ReminderScheduler.schedule(app)
}

private fun offsetLabel(days: Int): String = when {
    days == 0 -> "بدون تعديل"
    days > 0 -> "+$days ${if (days == 1) "يوم" else "يومين"}"
    else -> "−${abs(days)} ${if (days == -1) "يوم" else "يومين"}"
}

/** A group of settings under its icon and name. */
@Composable
private fun Section(icon: Painter, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueLine(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                .padding(PaddingValues(horizontal = 14.dp, vertical = 6.dp)),
        )
    }
}

@Composable
private fun ActionLine(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TimeDialog(title: String, hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun About() {
    val uri = LocalUriHandler.current
    Text("جدول الحفظ، الإصدار 3.4")
    Text(
        "بيانات السور والآيات والأجزاء وصفحات مصحف المدينة من مشروع تنزيل، برخصة المشاع الإبداعي CC BY 3.0.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { uri.openUri("https://tanzil.net") }) { Text("tanzil.net") }
    Text(
        "تقسيم «مقاطع التفسير» من موسوعة التفسير في الدرر السنية.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { uri.openUri("https://dorar.net/tafseer") }) { Text("dorar.net") }
    Text(
        "الخطان «المراعي» و«نوتو نسخ عربي» برخصة الخطوط المفتوحة SIL Open Font License 1.1.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { uri.openUri("https://openfontlicense.org") }) { Text("openfontlicense.org") }
}
