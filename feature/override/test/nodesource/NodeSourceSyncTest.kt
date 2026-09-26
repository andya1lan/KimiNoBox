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

import com.github.yumeyucca.yumebox.core.util.NodeSourceCopies
import com.github.yumeyucca.yumebox.core.util.NodeSourceSync
import com.github.yumeyucca.yumebox.core.util.NodeSourceSync.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class NodeSourceSyncTest {
    @Test
    fun newestCopyGoesToMissingAndOlderOnes() {
        val plan =
            NodeSourceSync.plan(
                listOf(Copy("running", 300L), Copy("master", 100L), Copy("other", null), Copy("third", 300L))
            )!!

        assertEquals("running", plan.from)
        assertEquals(listOf("master", "other"), plan.to)
    }

    @Test
    fun aTieKeepsTheFirstCopy() {
        val plan = NodeSourceSync.plan(listOf(Copy("target", 200L), Copy("master", 200L)))!!

        assertEquals("target", plan.from)
        assertEquals(emptyList<String>(), plan.to)
    }

    @Test
    fun aNewlyBoundProfileGetsTheMasterCopy() {
        val plan = NodeSourceSync.plan(listOf(Copy("new profile", null), Copy("master", 100L)))!!

        assertEquals("master", plan.from)
        assertEquals(listOf("new profile"), plan.to)
    }

    @Test
    fun nothingToDoWithoutAnyCopy() {
        assertNull(NodeSourceSync.plan(listOf(Copy("a", null), Copy("b", null))))
    }

    /**
     * The compiler keeps the file name of a provider path and moves it under
     * `<profile>/providers/proxies/` (`lib/native/rust/src/compiler/patch/paths.rs`: container
     * directories such as `providers/` are trimmed, the extension kept). This mirrors those rules
     * for the path the template writes; the emulator check compares it with the final config.
     */
    @Test
    fun theProfileCopyIsWhereTheCompilerPointsTheProvider() {
        val pathId = "k3n8x2qa"
        val profileDir = File("/data/user/0/app/files/imported/0a2f645f")

        val templatePath = NodeSourceCopies.templatePath(pathId)
        val compilerTail = templatePath.removePrefix("./").split('/').dropWhile { it == "providers" }.joinToString("/")

        assertEquals("./providers/src-k3n8x2qa.yaml", templatePath)
        assertEquals(
            File(profileDir, "providers/proxies/$compilerTail"),
            NodeSourceCopies.profileCopy(profileDir, pathId),
        )
    }
}
