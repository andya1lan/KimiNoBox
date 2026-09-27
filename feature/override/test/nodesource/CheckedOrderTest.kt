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
    fun aCheckedRowGoesLastAndAnUncheckedOneLeaves() {
        assertEquals(listOf("a", "b", "c"), CheckedOrder.toggle(listOf("a", "b"), "c", on = true))
        assertEquals(listOf("a", "c"), CheckedOrder.toggle(listOf("a", "b", "c"), "b", on = false))
        // Checked again after unchecking: last, not back in its old place
        assertEquals(listOf("a", "c", "b"), CheckedOrder.toggle(listOf("a", "c"), "b", on = true))
        assertEquals(listOf("a", "b"), CheckedOrder.toggle(listOf("a", "b"), "a", on = true))
    }

    @Test
    fun aDragMovesOneIdToTheOthersPlace() {
        assertEquals(listOf("c", "a", "b"), CheckedOrder.moved(listOf("a", "b", "c"), "c", "a"))
        assertEquals(listOf("b", "c", "a"), CheckedOrder.moved(listOf("a", "b", "c"), "a", "c"))
        assertEquals(listOf("a", "b"), CheckedOrder.moved(listOf("a", "b"), "x", "a"))
    }

    @Test
    fun checkedRowsComeFirstInTheirOrderAndTheRestKeepTheListOrder() {
        val all = listOf("s1", "s2", "s3", "s4")
        // "s9" was deleted meanwhile
        val (on, off) = CheckedOrder.split(all, listOf("s3", "s9", "s1")) { it }
        assertEquals(listOf("s3", "s1"), on)
        assertEquals(listOf("s2", "s4"), off)
    }

    @Test
    fun aSourceMadeMeanwhileIsCheckedLast() {
        val known = listOf("a", "b")
        assertEquals(listOf("b", "c"), CheckedOrder.withAdded(listOf("b"), known, listOf("a", "b", "c")))
        // Backed out of the editor: nothing new
        assertEquals(listOf("b"), CheckedOrder.withAdded(listOf("b"), known, known))
        assertEquals(listOf("c"), CheckedOrder.withAdded(listOf("c"), known, listOf("a", "b", "c")))
    }

    @Test
    fun aNewProfileBindsItsSourcesAsShownThenItsOverridesAsShown() {
        var sources = CheckedOrder.toggle(CheckedOrder.toggle(emptyList(), "A", on = true), "C", on = true)
        sources = CheckedOrder.moved(sources, "C", "A")
        var overrides = CheckedOrder.toggle(listOf("acl4ssr"), "direct", on = true)
        overrides = CheckedOrder.moved(overrides, "direct", "acl4ssr")
        val shownSources = CheckedOrder.split(listOf("A", "B", "C"), sources) { it }.first
        val shownOverrides = CheckedOrder.split(listOf("dns", "direct", "acl4ssr"), overrides) { it }.first
        assertEquals(listOf("C", "A", "direct", "acl4ssr"), CheckedOrder.chain(shownSources, shownOverrides))
        assertEquals(listOf("A"), CheckedOrder.chain(listOf("A"), emptyList()))
    }
}
