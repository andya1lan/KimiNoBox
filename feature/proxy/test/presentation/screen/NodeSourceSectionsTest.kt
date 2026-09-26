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

package com.github.yumeyucca.yumebox.presentation.screen

import com.github.yumeyucca.yumebox.core.model.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeSourceSectionsTest {
    private fun node(name: String, type: String = Proxy.Type.Shadowsocks) = Proxy(name, name, "", type, 0)

    @Test
    fun membersGoToTheirSourceTheFirstSourceWinsAndGroupsLead() {
        val members =
            listOf(
                node("自动选择", Proxy.Type.URLTest),
                node("DIRECT", Proxy.Type.Direct),
                node("🇭🇰 香港 01"),
                node("🇯🇵 日本 01"),
                node("本地测试"),
                node("🇺🇸 美国 01"),
            )
        val sections =
            NodeSourceSections.of(
                members,
                linkedMapOf(
                    "air" to listOf("🇭🇰 香港 01", "🇯🇵 日本 01"),
                    "sky" to listOf("🇯🇵 日本 01", "🇺🇸 美国 01"),
                ),
            )!!

        assertEquals(listOf(NodeSourceSections.GROUPS, "air", "sky", NodeSourceSections.OWN_NODES), sections.map { it.title })
        assertEquals(listOf("自动选择", "DIRECT"), sections[0].proxies.map { it.name })
        assertEquals(listOf("🇭🇰 香港 01", "🇯🇵 日本 01"), sections[1].proxies.map { it.name })
        assertEquals(listOf("🇺🇸 美国 01"), sections[2].proxies.map { it.name })
        assertEquals(listOf("本地测试"), sections[3].proxies.map { it.name })
    }

    @Test
    fun oneSourceKeepsThePlainList() {
        val members = listOf(node("自动选择", Proxy.Type.URLTest), node("a"), node("b"))

        assertNull(NodeSourceSections.of(members, mapOf("air" to listOf("a", "b"))))
        assertNull(NodeSourceSections.of(members, emptyMap()))
    }

    @Test
    fun positionCountsTheHeadersBeforeTheCard() {
        val sections =
            listOf(
                NodeSourceSections.Section("air", listOf(node("a"), node("b"))),
                NodeSourceSections.Section("sky", listOf(node("c"), node("b"))),
            )

        assertEquals(1, NodeSourceSections.position(sections, "a"))
        assertEquals(2, NodeSourceSections.position(sections, "b"))
        assertEquals(4, NodeSourceSections.position(sections, "c"))
        assertNull(NodeSourceSections.position(sections, "d"))
    }
}
