package com.sallo.hifz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sallo.hifz.HifzApp
import com.sallo.hifz.ui.badges.BadgesScreen
import com.sallo.hifz.ui.badges.NewBadgeDialog
import com.sallo.hifz.ui.design.DesignScreen
import com.sallo.hifz.ui.notes.NotesScreen
import com.sallo.hifz.ui.stats.OverviewScreen
import com.sallo.hifz.ui.editor.PlanEditorScreen
import com.sallo.hifz.ui.plans.PlansScreen
import com.sallo.hifz.ui.settings.SettingsScreen
import com.sallo.hifz.ui.table.PlanTableScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AppNav(app: HifzApp, openRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val pending by openRoute.collectAsStateWithLifecycle()

    // A plan from a widget or a reminder, «إحصائياتي» from the weekly report, «خطة جديدة» from a shortcut.
    LaunchedEffect(pending) {
        val route = pending ?: return@LaunchedEffect
        openRoute.value = null
        nav.navigate(route) {
            popUpTo("plans")
            launchSingleTop = true
        }
    }

    NavHost(navController = nav, startDestination = "plans") {
        composable("plans") {
            PlansScreen(
                app = app,
                onOpen = { nav.navigate("table/$it") },
                onNew = { nav.navigate("editor/0") },
                onEdit = { nav.navigate("editor/$it") },
                onSettings = { nav.navigate("settings") },
                onCopy = { nav.navigate("editor/0?copy=$it") },
                onOverview = { nav.navigate("overview") },
                onBadges = { nav.navigate("badges") },
                onNotes = { nav.navigate("notes") },
            )
        }
        composable(
            "editor/{planId}?copy={copy}",
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("copy") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("planId") ?: 0L
            val copy = entry.arguments?.getLong("copy") ?: 0L
            PlanEditorScreen(
                app = app,
                planId = id.takeIf { it > 0 },
                onSaved = { savedId ->
                    nav.popBackStack()
                    if (id == 0L) nav.navigate("table/$savedId")
                },
                onBack = { nav.popBackStack() },
                copyFrom = copy.takeIf { it > 0 },
            )
        }
        composable(
            "table/{planId}?row={row}",
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("row") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("planId") ?: 0L
            val row = entry.arguments?.getInt("row") ?: -1
            PlanTableScreen(
                app = app,
                planId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("editor/$id") },
                initialRow = row.takeIf { it >= 0 },
                onNotes = { nav.navigate("notes?plan=$id") },
                onCopy = { nav.navigate("editor/0?copy=$id") },
            )
        }
        composable("settings") {
            SettingsScreen(app = app, onBack = { nav.popBackStack() }, onDesigns = { nav.navigate("design") })
        }
        composable("design") {
            DesignScreen(app = app, onBack = { nav.popBackStack() })
        }
        composable("overview") {
            OverviewScreen(app = app, onBack = { nav.popBackStack() }, onBadges = { nav.navigate("badges") }, onOpenRow = { planId, row -> nav.navigate("table/$planId?row=$row") })
        }
        composable("badges") {
            BadgesScreen(app = app, onBack = { nav.popBackStack() })
        }
        composable(
            "notes?plan={plan}",
            arguments = listOf(
                navArgument("plan") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { entry ->
            val plan = entry.arguments?.getLong("plan") ?: 0L
            NotesScreen(
                app = app,
                planId = plan.takeIf { it > 0 },
                onBack = { nav.popBackStack() },
                onOpenRow = { planId, row -> nav.navigate("table/$planId?row=$row") },
            )
        }
    }

    // A badge earned since it was last shown (from a widget too): celebrated once, over any screen.
    val pendingBadges by app.badges.pending.collectAsStateWithLifecycle()
    val prefs by app.prefs.values.collectAsStateWithLifecycle()
    pendingBadges.firstOrNull()?.let { badge ->
        if (prefs.celebrate) {
            NewBadgeDialog(
                badge = badge,
                onShow = {
                    app.badges.shown(badge)
                    nav.navigate("badges")
                },
                onDismiss = { app.badges.shown(badge) },
            )
        } else {
            LaunchedEffect(badge) { app.badges.shown(badge) }
        }
    }
}
