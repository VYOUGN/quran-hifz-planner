@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.table

import com.sallo.hifz.data.Repeats
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.HifzApp
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.catchUpOptions
import com.sallo.hifz.data.hasMain
import java.time.LocalDate
import com.sallo.hifz.R
import com.sallo.hifz.findActivity
import com.sallo.hifz.print.TablePrinter
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.Loading

@Composable
fun PlanTableScreen(
    app: HifzApp,
    planId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    /** A row to open on arrival, by its number in the plan (from a note or a mistake elsewhere). */
    initialRow: Int? = null,
    onNotes: () -> Unit = {},
    onCopy: () -> Unit = {},
) {
    val vm: TableViewModel = viewModel(factory = viewModelFactory { initializer { TableViewModel(app, planId) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    var openRow by remember { mutableStateOf<Int?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var catchingUp by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showVacation by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showCertificate by remember { mutableStateOf(false) }
    var certificateFor by remember { mutableStateOf<Achievement?>(null) }
    var arrived by rememberSaveable { mutableStateOf(false) }
    val inCalendar by remember(planId) { app.database.calendarDao().linkFlow(planId) }.collectAsStateWithLifecycle(null)
    val prefs by app.prefs.values.collectAsStateWithLifecycle()
    val celebration = rememberCelebration()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.loaded, initialRow) {
        if (state.loaded && initialRow != null && !arrived) {
            arrived = true
            state.rows.indexOfFirst { it.session.idx == initialRow }.takeIf { it >= 0 }?.let { openRow = it }
        }
    }
    if (prefs.celebrate) CelebrationTrigger(state, celebration)
    LaunchedEffect(vm) {
        vm.undo.collect { change ->
            val result = snackbar.showSnackbar(change.message, actionLabel = "تراجع", withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undo(change)
        }
    }
    // A light tap of vibration when ticking, a different one when unticking. Remembered, so the rows
    // that did not change are not drawn again.
    val haptics = prefs.haptics
    val toggle: (SessionEntity, Item) -> Unit = remember(vm, view, haptics) {
        { row, item ->
            if (haptics) {
                view.performHapticFeedback(
                    when {
                        row.done(item) -> HapticFeedbackConstants.CLOCK_TICK
                        Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
                        else -> HapticFeedbackConstants.VIRTUAL_KEY
                    },
                )
            }
            vm.toggle(row, item)
        }
    }
    val openRowAt: (Int) -> Unit = remember { { openRow = it } }
    val openStats: () -> Unit = remember { { showStats = true } }
    val openCatchUp: () -> Unit = remember { { catchingUp = true } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.plan?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل الخطة") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("إحصائيات الخطة") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_stats), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    showStats = true
                                },
                            )
                            if (state.settings?.hasDates == true) {
                                DropdownMenuItem(
                                    text = { Text(if (inCalendar != null) "في التقويم" else "نقل إلى التقويم") },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(if (inCalendar != null) R.drawable.ic_calendar_done else R.drawable.ic_calendar),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menu = false
                                        showCalendar = true
                                    },
                                )
                            }
                            if (state.settings?.hasDates == true) {
                                DropdownMenuItem(
                                    text = { Text("إجازة") },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        showVacation = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("ملاحظات الخطة") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onNotes()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("شارك تقدمك") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    showShare = true
                                },
                            )
                            if (achievementsOf(state).isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("شهادة إتمام") },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        certificateFor = null
                                        showCertificate = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("نسخ الخطة") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onCopy()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("طباعة أو حفظ PDF") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_print), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    val plan = state.plan
                                    if (plan != null && activity != null) {
                                        TablePrinter.print(activity, plan.name, TablePrinter.html(plan, state.description, state.rows, state.showHijri))
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loaded || state.plan == null) {
                Loading()
            } else {
                Column(Modifier.fillMaxSize()) {
                    ViewSwitch(state.cardsView, vm::setCardsView)
                    Box(Modifier.weight(1f)) {
                        if (state.cardsView) {
                            CardsView(state, toggle, openRowAt, openStats, openCatchUp)
                        } else {
                            GridView(state, toggle, openRowAt, openStats, openCatchUp)
                        }
                    }
                }
            }
            CelebrationOverlay(celebration)
        }
    }

    openRow?.let { idx ->
        state.rows.getOrNull(idx)?.let { row ->
            RowSheet(
                row = row,
                scale = state.settings?.gradeScale ?: GradeScale.WORDS,
                showHijri = state.showHijri,
                separateDays = state.settings?.hasSeparateSchedules == true,
                onDismiss = { openRow = null },
                onSave = {
                    vm.save(it)
                    openRow = null
                },
                onPostpone = { date ->
                    vm.postpone(row.index, date)
                    openRow = null
                },
                repeatsFor = { edited ->
                    state.plan?.let { plan ->
                        val rows = state.rows.map { it.session }.toMutableList().apply { set(row.index, edited) }
                        Repeats.options(plan, rows, row.index, LocalDate.now())
                    }.orEmpty()
                },
                extraSession = state.plan?.let { plan -> state.settings?.hasSeparateSchedules != true && !row.session.hasMain(plan) } == true,
                onRepeat = { edited, option, way ->
                    vm.repeat(edited, option, way)
                    openRow = null
                },
            )
        }
    }
    if (showStats) {
        StatsSheet(
            state = state,
            onCertificate = {
                showStats = false
                certificateFor = it
                showCertificate = true
            },
            onOpenRow = {
                showStats = false
                openRow = it
            },
            onDismiss = { showStats = false },
        )
    }
    if (showVacation) VacationSheet(app, planId) { showVacation = false }
    if (showShare) ShareSheet(state) { showShare = false }
    if (showCertificate) CertificateSheet(app, state, certificateFor) { showCertificate = false }
    if (showCalendar) CalendarSheet(app, planId, state.rows.size) { showCalendar = false }
    if (catchingUp) {
        val plan = state.plan
        val options = remember(state.rows) { plan?.let { catchUpOptions(it, state.rows.map { r -> r.session }, LocalDate.now()) } }
        if (plan == null || options == null) {
            // Nothing missed (any more).
            LaunchedEffect(Unit) { catchingUp = false }
        } else {
            CatchUpSheet(
                plan = plan,
                options = options,
                separateDays = state.settings?.hasSeparateSchedules == true,
                onApply = { method, start ->
                    catchingUp = false
                    vm.catchUp(method, start)
                },
                onDismiss = { catchingUp = false },
            )
        }
    }
}

/** Fires a celebration when a session is completed, and a bigger one at each quarter of the plan. */
@Composable
private fun CelebrationTrigger(state: TableState, celebration: CelebrationState) {
    val done = state.stats?.done
    val total = state.stats?.total ?: 0
    val focusDone = state.focus?.let { state.rows.getOrNull(it)?.allDone }
    val juz = state.juzDone.map { it.juz }
    var last by remember { mutableStateOf<Pair<Int, Boolean?>?>(null) }
    var lastJuz by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(done, focusDone, juz) {
        if (done == null) return@LaunchedEffect
        val previous = last
        val previousJuz = lastJuz
        last = done to focusDone
        lastJuz = juz
        if (previous == null || total == 0) return@LaunchedEffect
        val (prevDone, prevFocus) = previous
        val milestone = listOf(1.0, 0.75, 0.5, 0.25).firstOrNull { m -> prevDone < m * total && done >= m * total }
        val newJuz = juz.firstOrNull { previousJuz != null && it !in previousJuz }
        when {
            milestone == 1.0 -> celebration.fire(milestoneMessage(milestone), big = true)
            newJuz != null -> celebration.fire("أتممت حفظ ${com.sallo.hifz.print.Certificate.juz(newJuz)}، ما شاء الله 🎉", big = true)
            milestone != null -> celebration.fire(milestoneMessage(milestone), big = true)
            focusDone == true && prevFocus == false -> celebration.fire("أتممت جلسة اليوم كاملة، ما شاء الله", big = false)
            done > prevDone -> celebration.fire(encouragements.random(), big = false)
        }
    }
}

/** «البطاقات | الجدول»: the two ways of showing the plan. */
@Composable
private fun ViewSwitch(cards: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        listOf(true to "البطاقات", false to "الجدول").forEach { (value, label) ->
            val selected = value == cards
            val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tab")
            val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .selectable(selected = selected, role = Role.Tab) { onChange(value) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (value) R.drawable.ic_cards else R.drawable.ic_grid),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content)
            }
        }
    }
}
