/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import android.content.SharedPreferences

/**
 * The pool of actions that can be pinned to one of Landing's 4 configurable slots
 * (see [LandingSlot]) or, if not pinned anywhere, fall back to the "Más opciones" hub.
 */
enum class MainLandingAction(val id: String, val label: String, val subtitle: String) {
    BOARD_BUS("board_bus", "Estoy en el autobús", "Confirma tu viaje"),
    ROUTES("routes", "Líneas de bus", "Horarios y paradas"),
    ROUTE_PLANNER("route_planner", "Planificar viaje", "Encuentra tu ruta"),
    REMINDERS("reminders", "Mis recordatorios", "Avisos programados"),
    ANOTHER_DAY("another_day", "Consultar otro día", "Horarios de otro día"),
    FAVORITES("favorites", "Favoritos", "Paradas guardadas");

    companion object {
        fun fromId(id: String?): MainLandingAction? = entries.find { it.id == id }
    }
}

/**
 * Landing's 4 configurable slots: 2 square "main" cards + 2 compact "extra" pills.
 * ("Parada más cercana" and "Más opciones" are fixed and not part of this pool.)
 */
enum class LandingSlot(val id: String, val label: String, val default: MainLandingAction) {
    MAIN_1("main_1", "Acción principal 1", MainLandingAction.BOARD_BUS),
    MAIN_2("main_2", "Acción principal 2", MainLandingAction.ROUTES),
    EXTRA_1("extra_1", "Acción adicional 1", MainLandingAction.FAVORITES),
    EXTRA_2("extra_2", "Acción adicional 2", MainLandingAction.REMINDERS);

    companion object {
        /** Square slots render as the 2-up icon+title+subtitle cards; extra slots render as compact pills. */
        val squareSlots = listOf(MAIN_1, MAIN_2)
        val pillSlots = listOf(EXTRA_1, EXTRA_2)
    }
}

/**
 * Manages the user's assignment of [MainLandingAction]s to Landing's 4 [LandingSlot]s.
 * Guarantees the 4 returned actions are always distinct — [getSlots] self-heals any
 * duplicate/invalid/missing stored value by falling back to the next unused pool action.
 * Singleton pattern matching GuidedModePrefs.
 */
object LandingLayoutPrefs {
    private const val PREFS_NAME = "landing_layout"
    private const val LEGACY_PREFS_NAME = "main_action"
    private const val LEGACY_KEY_ACTION = "main_action_id"

    private lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyIfNeeded(context)
        DebugConfig.debugPrint("✅ LandingLayoutPrefs initialized")
    }

    /**
     * The old single "Acción principal" setting occupied the square slot next to the
     * (then hardcoded) "Estoy en el autobús" card — migrate it into MAIN_2 once.
     */
    private fun migrateLegacyIfNeeded(context: Context) {
        val alreadyConfigured = LandingSlot.entries.any { sharedPreferences.contains(it.id) }
        if (alreadyConfigured) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyAction = MainLandingAction.fromId(legacyPrefs.getString(LEGACY_KEY_ACTION, null))
        if (legacyAction != null) {
            sharedPreferences.edit().putString(LandingSlot.MAIN_2.id, legacyAction.id).apply()
        }
    }

    /** Always returns 4 distinct actions, one per slot, self-healing bad/duplicate stored data. */
    fun getSlots(): Map<LandingSlot, MainLandingAction> {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ LandingLayoutPrefs not initialized")
            return LandingSlot.entries.associateWith { it.default }
        }

        val used = mutableSetOf<MainLandingAction>()
        val resolved = LinkedHashMap<LandingSlot, MainLandingAction>()
        var needsPersist = false

        for (slot in LandingSlot.entries) {
            val stored = MainLandingAction.fromId(sharedPreferences.getString(slot.id, null))
            val chosen = if (stored != null && stored !in used) {
                stored
            } else {
                needsPersist = true
                (listOf(slot.default) + MainLandingAction.entries).first { it !in used }
            }
            used += chosen
            resolved[slot] = chosen
        }

        if (needsPersist) {
            val editor = sharedPreferences.edit()
            resolved.forEach { (slot, action) -> editor.putString(slot.id, action.id) }
            editor.apply()
        }

        return resolved
    }

    fun getAction(slot: LandingSlot): MainLandingAction = getSlots().getValue(slot)

    /** Swaps whichever slot currently holds [action] with [slot]'s previous value, so no duplicates occur. */
    fun setAction(slot: LandingSlot, action: MainLandingAction) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ LandingLayoutPrefs not initialized")
            return
        }
        val current = getSlots().toMutableMap()
        val previousOwner = current.entries.find { it.value == action && it.key != slot }?.key
        val previousValue = current[slot]

        current[slot] = action
        if (previousOwner != null && previousValue != null) {
            current[previousOwner] = previousValue
        }

        val editor = sharedPreferences.edit()
        current.forEach { (s, a) -> editor.putString(s.id, a.id) }
        editor.apply()
        DebugConfig.debugPrint("🎯 Landing layout updated: ${current.mapValues { it.value.id }}")
    }
}
