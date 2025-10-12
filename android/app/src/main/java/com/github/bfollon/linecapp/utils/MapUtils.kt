/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.linecapp.utils

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
