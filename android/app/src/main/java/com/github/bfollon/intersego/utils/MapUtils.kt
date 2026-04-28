/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.utils

import kotlin.collections.plus

object MapUtils {
    fun <K, V> Map<K, V>.mergeWith(other: Map<K, V>, combine: (va: V, vb: V) -> V): Map<K, V> =
        other.entries.fold(this) { acc, entry ->
            acc + (entry.key to (
                    acc[entry.key]
                        ?.let { existingEntry -> combine(existingEntry, entry.value) }
                        ?: entry.value))
        }

    fun <K, V> Map<K, List<V>>.accumulateWith(other: Map<K, V>): Map<K, List<V>> =
        other.entries.fold(this) { acc, entry ->
            acc + (entry.key to (
                    acc[entry.key]
                        ?.let { existingEntry -> existingEntry + entry.value }
                        ?: listOf(entry.value)))
        }
}
