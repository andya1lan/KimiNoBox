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

package com.github.yumeyucca.yumebox.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NodeSourceEmptyHintTest {
    private val log =
        """
        time="2026-09-26T12:40:00.000000000Z" level=error msg="initial proxy provider old error: timeout"
        time="2026-09-26T12:44:48.178987422Z" level=error msg="initial proxy provider links error: 404 Not Found"
        time="2026-09-26T12:44:48.300000000Z" level=error msg="initial proxy provider ✈️ air B error: Get "https://e.com/s": EOF"
        time="2026-09-26T12:44:49.042457173Z" level=error msg="[Provider] links pull error: 404 Not Found"
        """.trimIndent()

    @Test
    fun initialErrorsSinceTheStartAreReadFromTheCoreLog() {
        val since = Instant.parse("2026-09-26T12:44:00Z").toEpochMilli()

        assertEquals(
            mapOf("links" to "404 Not Found", "✈️ air B" to "Get \"https://e.com/s\": EOF"),
            initialProviderErrors(log, since),
        )
    }

    @Test
    fun theSummaryNamesEverySourceAndItsReason() {
        val text = summaryOf(linkedMapOf("links" to "404 Not Found", "air" to null))

        assertTrue(text, text.startsWith("「links」：404 Not Found\n「air」\n"))
        assertTrue(text, text.contains("COMPATIBLE"))
    }
}
