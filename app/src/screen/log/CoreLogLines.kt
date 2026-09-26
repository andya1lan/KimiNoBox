/*
 * This file is part of KimiNoBox, a modified version of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 KimiNoBox contributors
 *
 */

package com.github.yumeyucca.yumebox.screen.log

import com.github.yumeyucca.yumebox.core.model.LogMessage
import java.time.Instant
import java.util.Date

/**
 * KimiNoBox: the live stream carries no history, so the log page starts from `core.log`, where the
 * core writes its error-level lines (the file is recreated on every start). Lines that are not in
 * the core's `time=… level=… msg=…` form, such as a Go panic, are kept as they are.
 */
internal object CoreLogLines {
    private val structured = Regex("""time="([^"]+)"\s+level=(\w+)\s+msg="((?:[^"\\]|\\.)*)".*""")

    fun parse(log: String, limit: Int, fallbackTime: Date): List<LogMessage> =
        log.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> parseLine(line) ?: LogMessage(LogMessage.Level.Unknown, line, fallbackTime) }
            .toList()
            .takeLast(limit)

    internal fun parseLine(line: String): LogMessage? {
        val (time, level, message) = structured.matchEntire(line)?.destructured ?: return null
        val date = runCatching { Date.from(Instant.parse(time)) }.getOrNull() ?: return null
        return LogMessage(levelOf(level), unescape(message), date)
    }

    private fun levelOf(raw: String): LogMessage.Level =
        when (raw.lowercase()) {
            "debug" -> LogMessage.Level.Debug
            "info" -> LogMessage.Level.Info
            "warning", "warn" -> LogMessage.Level.Warning
            "error", "fatal", "panic" -> LogMessage.Level.Error
            else -> LogMessage.Level.Unknown
        }

    private fun unescape(text: String): String =
        buildString(text.length) {
            var escaped = false
            for (char in text) {
                when {
                    escaped -> {
                        append(if (char == 'n') '\n' else if (char == 't') '\t' else char)
                        escaped = false
                    }
                    char == '\\' -> escaped = true
                    else -> append(char)
                }
            }
        }
}
