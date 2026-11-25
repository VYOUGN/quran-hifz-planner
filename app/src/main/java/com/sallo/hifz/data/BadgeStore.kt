package com.sallo.hifz.data

import android.content.Context
import com.sallo.hifz.plan.Badge
import com.sallo.hifz.plan.BadgeInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * «أوسمة الالتزام» earned, with their day, kept even if a session is unticked later. The badges earned
 * since the app last showed them wait in [pending], so one earned from a widget is celebrated on the
 * next open. The first check after installing records what is already earned without a celebration.
 */
class BadgeStore(context: Context) {
    private val sp = context.getSharedPreferences("badges", Context.MODE_PRIVATE)
    private val _earned = MutableStateFlow(readEarned())
    private val _pending = MutableStateFlow(readPending())

    val earned: StateFlow<Map<Badge, LocalDate>> = _earned.asStateFlow()
    val pending: StateFlow<List<Badge>> = _pending.asStateFlow()

    @Synchronized
    fun update(input: BadgeInput, today: LocalDate) {
        val had = _earned.value
        val fresh = Badge.entries.filter { it.earned(input) && it !in had }
        val first = !sp.getBoolean(READY, false)
        if (fresh.isEmpty() && !first) return
        val all = had + fresh.associateWith { today }
        val pending = if (first) _pending.value else _pending.value + fresh
        sp.edit()
            .putBoolean(READY, true)
            .putString(EARNED, all.entries.joinToString(";") { "${it.key.name}:${it.value.toEpochDay()}" })
            .putString(PENDING, pending.joinToString(";") { it.name })
            .apply()
        _earned.value = all
        _pending.value = pending
    }

    /** [badge] has been shown. */
    @Synchronized
    fun shown(badge: Badge) {
        val rest = _pending.value - badge
        sp.edit().putString(PENDING, rest.joinToString(";") { it.name }).apply()
        _pending.value = rest
    }

    private fun readEarned(): Map<Badge, LocalDate> =
        sp.getString(EARNED, "").orEmpty().split(';').mapNotNull { part ->
            val (name, day) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val badge = Badge.fromName(name) ?: return@mapNotNull null
            badge to LocalDate.ofEpochDay(day.toLongOrNull() ?: return@mapNotNull null)
        }.toMap()

    private fun readPending(): List<Badge> =
        sp.getString(PENDING, "").orEmpty().split(';').mapNotNull { Badge.fromName(it) }

    private companion object {
        const val READY = "ready"
        const val EARNED = "earned"
        const val PENDING = "pending"
    }
}
