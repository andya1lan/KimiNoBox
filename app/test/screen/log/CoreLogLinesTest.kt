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
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLogLinesTest {
    private val fallback = Date(0)

    @Test
    fun parsesTheCoreLineFormat() {
        val line =
            """time="2026-09-26T17:36:15.119175222Z" level=error msg="initial proxy provider slow error: Get \"http://10.0.2.2:8000/slow\": EOF""""

        val message = CoreLogLines.parseLine(line)!!

        assertEquals(LogMessage.Level.Error, message.level)
        assertEquals(
            "initial proxy provider slow error: Get \"http://10.0.2.2:8000/slow\": EOF",
            message.message,
        )
        assertEquals(Instant.parse("2026-09-26T17:36:15.119Z").toEpochMilli(), message.time.time)
    }

    @Test
    fun keepsOtherLinesAndTheLastOnes() {
        val log =
            """
            time="2026-09-26T17:36:15Z" level=warning msg="first"

            panic: runtime error
            time="2026-09-26T17:36:16Z" level=error msg="last"
            """.trimIndent()

        val messages = CoreLogLines.parse(log, limit = 2, fallbackTime = fallback)

        assertEquals(listOf("panic: runtime error", "last"), messages.map { it.message })
        assertEquals(LogMessage.Level.Unknown, messages[0].level)
        assertEquals(fallback, messages[0].time)
    }

    @Test
    fun emptyLogGivesNothing() {
        assertEquals(emptyList<LogMessage>(), CoreLogLines.parse("", limit = 200, fallbackTime = fallback))
    }
}
