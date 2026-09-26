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
import org.junit.Test

class NodeNameClashTest {
    @Test
    fun eachSourceNamesTheOneItSharesMostWith() {
        val clashes =
            NodeNameClash.find(
                mapOf(
                    "air" to listOf("🇭🇰 香港 01", "🇯🇵 日本 01", "🇺🇸 美国 01"),
                    "sky" to listOf("🇭🇰 香港 01", "🇯🇵 日本 01"),
                    "self" to listOf("🇺🇸 美国 01"),
                    "clean" to listOf("🇸🇬 新加坡 01"),
                )
            )

        assertEquals(NodeNameClash.Clash("sky", 2), clashes["air"])
        assertEquals(NodeNameClash.Clash("air", 2), clashes["sky"])
        assertEquals(NodeNameClash.Clash("air", 1), clashes["self"])
        assertEquals(null, clashes["clean"])
    }
}
