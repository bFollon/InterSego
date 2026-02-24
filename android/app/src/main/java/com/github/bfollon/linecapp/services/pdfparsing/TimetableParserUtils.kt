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

package com.github.bfollon.linecapp.services.pdfparsing

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Markers that can appear adjacent to a time token in a PDF timetable line.
 *
 * Longer symbols must come before shorter ones in the enum so that the
 * auto-generated regex alternation matches greedily (e.g. "**" before "*").
 */
enum class TimeModifier(val symbol: String) {
    DOUBLE_ASTERISK("**"),
    ARROW("→"),
    POUND("#"),
}

/**
 * A time extracted from a timetable line, together with any modifier symbol
 * that appeared directly before or after it (e.g. →07:45, 7:40**).
 */
data class AnnotatedTime(
    val time: LocalTime,
    val modifier: TimeModifier? = null,
)

object TimetableParserUtils {

    // Regex pattern to match time format HH:MM or H:MM (e.g., "7:40", "14:30")
    val TIME_PATTERN = Regex("""\d{1,2}:\d{2}""")

    // Alternation built from enum entries. Enum is already ordered longest-first,
    // which prevents shorter symbols from shadowing longer ones.
    private val modifierAlternatives =
        TimeModifier.entries.joinToString("|") { Regex.escape(it.symbol) }

    // Matches an optional modifier prefix, a time token, and an optional modifier suffix.
    // Group 1 = prefix modifier, group 2 = time, group 3 = suffix modifier.
    // Prefix allows trailing whitespace (→ 07:45); suffix must be immediately adjacent
    // (7:40**) so no \s* — otherwise a spaced prefix on the next token is mis-consumed.
    private val ANNOTATED_TIME_PATTERN = Regex(
        """(?:($modifierAlternatives)\s*)?(\d{1,2}:\d{2})(?:($modifierAlternatives))?"""
    )

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm")

    fun hasTimes(line: String): Boolean = TIME_PATTERN.containsMatchIn(line)

    /**
     * Extract all times from a line, ignoring everything else.
     * Example: "JULIO Y AGOSTO 7:40* 7:43 14:30" -> [LocalTime(7,40), LocalTime(7,43), LocalTime(14,30)]
     */
    fun extractTimes(line: String): List<LocalTime> =
        TIME_PATTERN.findAll(line)
            .map { LocalTime.parse(it.value, TIME_FORMATTER) }
            .toList()

    /**
     * Extract all times from a line together with any adjacent modifier symbol.
     *
     * Modifiers are recognised both as prefixes (→07:45) and suffixes (7:40**).
     * If both a prefix and a suffix are present, the prefix takes priority.
     *
     * Example:
     *   "7:20 7:30 →07:45 →07:50 7:40**"
     *   -> [AnnotatedTime(07:20, null), AnnotatedTime(07:30, null),
     *       AnnotatedTime(07:45, ARROW), AnnotatedTime(07:50, ARROW),
     *       AnnotatedTime(07:40, DOUBLE_ASTERISK)]
     */
    fun extractAnnotatedTimes(line: String): List<AnnotatedTime> =
        ANNOTATED_TIME_PATTERN.findAll(line).map { match ->
            val modifierSymbol = match.groupValues[1].ifEmpty { match.groupValues[3] }.ifEmpty { null }
            val modifier = modifierSymbol?.let { sym ->
                TimeModifier.entries.firstOrNull { it.symbol == sym }
            }
            AnnotatedTime(
                time = LocalTime.parse(match.groupValues[2], TIME_FORMATTER),
                modifier = modifier,
            )
        }.toList()

    /**
     * Sort a list of LocalTime objects chronologically.
     * Example: [LocalTime(14,30), LocalTime(7,40)] -> [LocalTime(7,40), LocalTime(14,30)]
     */
    fun sortTimes(times: List<LocalTime>): List<LocalTime> = times.sorted()
}
