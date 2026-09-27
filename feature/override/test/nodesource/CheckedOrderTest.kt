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

class CheckedOrderTest {
    @Test
    fun aDragMovesOneIdToTheOthersPlace() {
        assertEquals(listOf("c", "a", "b"), CheckedOrder.moved(listOf("a", "b", "c"), "c", "a"))
        assertEquals(listOf("b", "c", "a"), CheckedOrder.moved(listOf("a", "b", "c"), "a", "c"))
        assertEquals(listOf("a", "b"), CheckedOrder.moved(listOf("a", "b"), "x", "a"))
    }

    @Test
    fun aSourceMadeMeanwhileIsCheckedLast() {
        val known = listOf("a", "b")
        assertEquals(listOf("b", "c"), CheckedOrder.withAdded(listOf("b"), known, listOf("a", "b", "c")))
        // Backed out of the editor: nothing new
        assertEquals(listOf("b"), CheckedOrder.withAdded(listOf("b"), known, known))
        assertEquals(listOf("c"), CheckedOrder.withAdded(listOf("c"), known, listOf("a", "b", "c")))
    }
}
