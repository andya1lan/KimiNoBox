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

package com.github.yumeyucca.yumebox.nodesource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionHeadersTest {
    private fun meta(vararg headers: Pair<String, String>): SubscriptionMeta {
        val byName = headers.associate { (name, value) -> name.lowercase() to value }
        return SubscriptionHeaders.parse { byName[it.lowercase()] }
    }

    @Test
    fun nameFromBothFileNameForms() {
        assertEquals("air", meta("Content-Disposition" to "attachment; filename=air.yaml").name)
        assertEquals("air", meta("Content-Disposition" to "attachment; filename=\"air.yml\"").name)
        assertEquals(
            "机场 A",
            meta("Content-Disposition" to "attachment; filename*=UTF-8''%E6%9C%BA%E5%9C%BA%20A.txt").name,
        )
        // The extended form wins when both are sent.
        assertEquals(
            "机场",
            meta("Content-Disposition" to "attachment; filename=\"fallback.yaml\"; filename*=utf-8''%E6%9C%BA%E5%9C%BA.yaml")
                .name,
        )
        assertEquals("air.conf", meta("content-disposition" to "inline; filename=air.conf").name)
    }

    @Test
    fun noNameWithoutAFileName() {
        assertNull(meta().name)
        assertNull(meta("Content-Disposition" to "attachment").name)
        assertNull(meta("Content-Disposition" to "attachment; filename=\".yaml\"").name)
    }

    @Test
    fun theUpdateIntervalHeaderIsNotRead() {
        val disposition = "Content-Disposition" to "attachment; filename=air.yaml"
        assertEquals(meta(disposition), meta(disposition, "Profile-Update-Interval" to "12"))
    }

    @Test
    fun usageFromSubscriptionUserinfo() {
        val usage =
            meta("subscription-userinfo" to "upload=1073741824; download=2147483648; total=107374182400; expire=1893456000")
                .usage!!
        assertEquals(1_073_741_824L, usage.upload)
        assertEquals(2_147_483_648L, usage.download)
        assertEquals(107_374_182_400L, usage.total)
        assertEquals(1_893_456_000L, usage.expire)
        assertNull(meta("subscription-userinfo" to "nonsense").usage)
        assertNull(meta().usage)
        assertNull(SubscriptionHeaders.parseUsage("upload=1; expire=0")!!.expire)
    }
}
