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

package com.github.yumeyucca.yumebox.runtime.service.controller

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LastDelayTest {
    private val now = OffsetDateTime.parse("2026-11-17T21:00:00+08:00").toInstant().toEpochMilli()

    @Test
    fun theLastTestWithinTenMinutesCounts() {
        val history =
            listOf(
                RawDelay("2026-11-17T20:40:00.1+08:00", 300),
                RawDelay("2026-11-17T20:51:00.123456789+08:00", 120),
            )

        assertEquals(120, lastDelay(history, now))
    }

    @Test
    fun anOlderTestReadsAsUntested() {
        assertEquals(0, lastDelay(listOf(RawDelay("2026-11-17T20:49:59+08:00", 120)), now))
        // The core on a phone without a zone writes UTC
        assertEquals(0, lastDelay(listOf(RawDelay("2026-11-17T12:49:59Z", 120)), now))
        assertEquals(120, lastDelay(listOf(RawDelay("2026-11-17T12:50:01Z", 120)), now))
    }

    @Test
    fun aFailedTestReadsAsATimeoutUntilItIsOld() {
        assertEquals(-1, lastDelay(listOf(RawDelay("2026-11-17T20:59:00+08:00", 0)), now))
        assertEquals(0, lastDelay(listOf(RawDelay("2026-11-17T20:30:00+08:00", 0)), now))
    }

    @Test
    fun noTestOrAnUnreadableTime() {
        assertEquals(0, lastDelay(emptyList(), now))
        assertEquals(88, lastDelay(listOf(RawDelay(null, 88)), now))
        assertEquals(88, lastDelay(listOf(RawDelay("yesterday", 88)), now))
    }
}
