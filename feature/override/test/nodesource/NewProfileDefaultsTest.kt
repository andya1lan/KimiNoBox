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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewProfileDefaultsTest {
    private val overrides = listOf("builtin-prevent-dns-leak", "builtin-add-direct-rules", "builtin-acl4ssr-online-full", "kiminobox-default-config")

    @Test
    fun aNewProfileStartsWithTheDefaultConfigAlone() {
        assertEquals(listOf("kiminobox-default-config"), NewProfileDefaults.checked(NewProfileDefaults.INITIAL_OVERRIDES, overrides))
    }

    @Test
    fun theSavedOrderStaysAndDeletedOverridesAreSkipped() {
        val saved = listOf("builtin-acl4ssr-online-full", "cfg-deleted", "builtin-add-direct-rules")
        assertEquals(listOf("builtin-acl4ssr-online-full", "builtin-add-direct-rules"), NewProfileDefaults.checked(saved, overrides))
        // 默认配置 deleted: nothing is checked until the user checks something
        assertEquals(emptyList<String>(), NewProfileDefaults.checked(NewProfileDefaults.INITIAL_OVERRIDES, overrides - "kiminobox-default-config"))
    }

    @Test
    fun theDefaultConfigIsMadeOnceAndNotAgainAfterADelete() {
        assertTrue(NewProfileDefaults.makeDefaultConfig(madeBefore = false, exists = false))
        assertFalse(NewProfileDefaults.makeDefaultConfig(madeBefore = false, exists = true))
        assertFalse(NewProfileDefaults.makeDefaultConfig(madeBefore = true, exists = false))
    }

    @Test
    fun theDefaultConfigIsTheBaseFieldsThenAcl4ssr() {
        val acl4ssr = "proxy-groups:\n  - {name: 节点选择, type: select, include-all: true}\nrule-providers: {}\nrules:\n  - MATCH,节点选择\n\n"
        val lines = NewProfileDefaults.defaultConfig(acl4ssr).lines()
        assertTrue(lines.take(2).all { it.startsWith("#") })
        assertEquals(
            listOf("mixed-port: 7890", "mode: rule", "log-level: info", "ipv6: false") + acl4ssr.trimEnd().lines() + "",
            lines.drop(2),
        )
        assertEquals(listOf("{}"), NewProfileDefaults.EMPTY_PROFILE.lines().filter { it.isNotBlank() && !it.startsWith("#") })
    }

    @Test
    fun withoutAcl4ssrTheDefaultConfigKeepsOneGroupAndRule() {
        for (acl4ssr in listOf(null, "", "\n")) {
            val lines = NewProfileDefaults.defaultConfig(acl4ssr).lines()
            assertEquals(1, lines.count { it.startsWith("#") })
            assertEquals(
                listOf("mixed-port: 7890", "mode: rule", "log-level: info", "ipv6: false", "proxy-groups:", "  - {name: 节点选择, type: select, include-all: true}", "rules:", "  - MATCH,节点选择", ""),
                lines.drop(1),
            )
        }
    }
}
