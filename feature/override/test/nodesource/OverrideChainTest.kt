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

class OverrideChainTest {
    // Node sources n1, n2 and overrides o1, o2, o3, interleaved as a real chain can be.
    private val chain = listOf("n1", "o1", "o2", "n2", "o3")
    private val isOverride = { id: String -> id.startsWith("o") }
    private val isSource = { id: String -> id.startsWith("n") }

    @Test
    fun overridesMoveOnlyThroughTheirOwnPlaces() {
        assertEquals(listOf("n1", "o3", "o1", "n2", "o2"), OverrideChain.reorder(chain, listOf("o3", "o1", "o2"), isOverride))
    }

    @Test
    fun nodeSourcesMoveOnlyThroughTheirOwnPlaces() {
        assertEquals(listOf("n2", "o1", "o2", "n1", "o3"), OverrideChain.reorder(chain, listOf("n2", "n1"), isSource))
    }

    @Test
    fun theSameOrderGivesTheSameChain() {
        assertEquals(chain, OverrideChain.reorder(chain, listOf("o1", "o2", "o3"), isOverride))
    }

    @Test
    fun aStaleOrderKeepsWhatItMissesAndDropsWhatIsGone() {
        // "o9" was unbound meanwhile; "o3" was bound after the order was taken.
        assertEquals(listOf("n1", "o2", "o1", "n2", "o3"), OverrideChain.reorder(chain, listOf("o9", "o2", "o1"), isOverride))
    }
}
