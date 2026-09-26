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
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfNodesTest {
    @Test
    fun pastedYamlInThreeShapes() {
        val list = "- {name: a, type: ss, server: s, port: 1}\n- name: b\n  type: trojan\n  server: t\n  port: 2\n"
        val config = "mixed-port: 7890\nproxies:\n  - {name: a, type: ss, server: s, port: 1}\nrules: []\n"
        val single = "name: a\ntype: ss\nserver: s\nport: 1\n"

        assertEquals(listOf("a", "b"), NodeListYaml.parse(list).getOrThrow().map { it["name"]?.scalarText })
        assertEquals(listOf("a"), NodeListYaml.parse(config).getOrThrow().map { it["name"]?.scalarText })
        assertEquals(listOf("a"), NodeListYaml.parse(single).getOrThrow().map { it["name"]?.scalarText })
    }

    @Test
    fun invalidPastesAreRejectedWithAReason() {
        fun message(yaml: String) = NodeListYaml.parse(yaml).exceptionOrNull()?.message

        assertTrue(message("- {name: a, type: ss")!!.startsWith("YAML 格式错误："))
        assertEquals(NodeText.UNKNOWN_SHAPE, message("just a string"))
        assertEquals(NodeText.UNKNOWN_SHAPE, message("mixed-port: 7890\n"))
        assertEquals(NodeText.UNKNOWN_SHAPE, message("proxies: nope\n"))
        assertEquals(NodeText.notANode(2), message("- {name: a, type: ss, server: s, port: 1}\n- plain\n"))

        fun problems(yaml: String) = NodeListYaml.problems(NodeListYaml.parse(yaml).getOrThrow())
        assertEquals(listOf(NodeText.NO_NODES), problems(""))
        assertEquals(
            listOf(NodeText.missingKeys("a", listOf("server", "port"))),
            problems("- {name: a, type: ss}"),
        )
        assertEquals(
            listOf(NodeText.missingKeys(NodeText.nthNode(1), listOf("name"))),
            problems("- {type: ss, server: s, port: 1}"),
        )
        assertEquals(listOf("a：${NodeText.PORT_INVALID}"), problems("- {name: a, type: ss, server: s, port: 0}"))
        assertEquals(
            listOf(NodeText.duplicateName("a")),
            problems("- {name: a, type: ss, server: s, port: 1}\n- {name: a, type: ss, server: t, port: 2}"),
        )
    }

    @Test
    fun pastedValuesKeepTheirText() {
        val yaml =
            "- {name: \"\\U0001F1F8\\U0001F1EC SG\", type: vless, server: s, port: 443, uuid: u, " +
                "reality-opts: {short-id: 0123}}"
        val nodes = NodeListYaml.parse(yaml).getOrThrow()

        val text = NodeListYaml.render(nodes)

        assertTrue(text, text.contains("\"🇸🇬 SG\""))
        assertTrue(text, text.contains("short-id: 0123"))
    }

    @Test
    fun selfNodesRoundTripWithEmoji() {
        val nodes =
            NodeListYaml.parse(
                "- {name: \"🇸🇬 SG\", type: ss, server: 192.0.2.22, port: 8388, cipher: aes-128-gcm, password: \"test\"}\n" +
                    "- {name: \"🧪 本机 SOCKS\", type: socks5, server: 10.0.2.2, port: 1080}\n"
            ).getOrThrow()
        val form = SelfNodesForm("self", "self ", nodes)

        val text = SelfNodesTemplate.render(form)

        assertEquals(
            """
            # kiminobox:self-nodes v1
            proxy-providers-merge:
              self:
                type: inline
                override:
                  additional-prefix: "self "
                payload:
                  - {name: "🇸🇬 SG", type: ss, server: 192.0.2.22, port: 8388, cipher: aes-128-gcm, password: "test"}
                  - {name: "🧪 本机 SOCKS", type: socks5, server: 10.0.2.2, port: 1080}

            """.trimIndent(),
            text,
        )
        val parsed = requireNotNull(SelfNodesTemplate.parse(text))
        assertEquals("self", parsed.name)
        assertEquals("self ", parsed.prefix)
        assertEquals(text, SelfNodesTemplate.render(parsed))
    }

    @Test
    fun selfNodesWithoutPrefixAndHandEdits() {
        val nodes = NodeListYaml.parse("- {name: a, type: ss, server: s, port: 1}").getOrThrow()
        val text = SelfNodesTemplate.render(SelfNodesForm("自建", "", nodes))

        assertTrue(text, !text.contains("override"))
        assertEquals("", SelfNodesTemplate.parse(text)?.prefix)

        listOf(
            text.replace("type: inline", "type: http"),
            text.replace("payload:", "proxies:"),
            text.replace("  - {name: a", "  - plain\n  - {name: a"),
            text.replaceFirst("# kiminobox:self-nodes v1", "# kiminobox:node-source v1"),
        ).forEach { edited -> assertNull(edited, SelfNodesTemplate.parse(edited)) }
    }

    @Test
    fun selfNodesNamesShareTheNodeSourceRules() {
        assertEquals(NodeText.NAME_TAKEN, SelfNodesTemplate.problems("self", "", setOf("self")).name)
        assertTrue(SelfNodesTemplate.problems("self", "", setOf("air")).isEmpty)
        assertEquals(NodeText.CONTROL_CHARS, SelfNodesTemplate.problems("self", "a\nb", emptySet()).prefix)
    }

    @Test
    fun theBlankTemplateHasNoNodesToSave() {
        val nodes = NodeListYaml.parse(NodeText.BLANK_NODES).getOrThrow()

        assertEquals(emptyList<Any>(), nodes)
        assertEquals(listOf(NodeText.NO_NODES), NodeListYaml.problems(nodes))
    }

    @Test
    fun nodesPastedUnderTheTemplateAreRead() {
        val text = NodeText.BLANK_NODES + "- {name: a, type: ss, server: s, port: 1, cipher: aes-128-gcm, password: p}\n"

        val nodes = NodeListYaml.parse(text).getOrThrow()

        assertEquals(listOf("a"), nodes.map { it["name"]?.scalarText })
        assertEquals(emptyList<String>(), NodeListYaml.problems(nodes))
    }
}
