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

class NodeSourceTemplateTest {
    @Test
    fun rendersTheAppendixTemplate() {
        val form = NodeSourceForm("air", "https://example.com/sub?token=xxx", 86_400, "air ", "k3n8x2qa")

        assertEquals(
            """
            # kiminobox:node-source v1
            proxy-providers-merge:
              air:
                type: http
                url: "https://example.com/sub?token=xxx"
                interval: 86400
                path: ./providers/src-k3n8x2qa.yaml
                override:
                  additional-prefix: "air "

            """.trimIndent(),
            NodeSourceTemplate.render(form),
        )
    }

    @Test
    fun emojiAndUrlSpecialCharactersRoundTrip() {
        val form =
            NodeSourceForm(
                name = "✈️ 机场 A",
                url = "https://example.com/api/v1/sub?token=a\"b&flag=clash#frag",
                intervalSeconds = 3_600,
                prefix = "🇭🇰✈️ ",
                pathId = "ab12cd34",
            )

        val text = NodeSourceTemplate.render(form)

        assertTrue(text, text.contains("✈️ 机场 A"))
        assertTrue(text, text.contains("""url: "https://example.com/api/v1/sub?token=a\"b&flag=clash#frag""""))
        assertEquals(form, NodeSourceTemplate.parse(text))
    }

    @Test
    fun emptyPrefixLeavesOutTheOverride() {
        val form = NodeSourceForm("air", "https://example.com/s", 86_400, "", "k3n8x2qa")

        val text = NodeSourceTemplate.render(form)

        assertTrue(text, !text.contains("override"))
        assertEquals(form, NodeSourceTemplate.parse(text))
    }

    @Test
    fun namesYamlWouldRetypeStayStrings() {
        listOf("123", "true", "null", "a: b", "#tag", "- dash", "[x]").forEach { name ->
            val form = NodeSourceForm(name, "https://e.com/s", 60, "$name ", "k3n8x2qa")
            assertEquals(name, form, NodeSourceTemplate.parse(NodeSourceTemplate.render(form)))
        }
    }

    @Test
    fun handEditedShapesAreNotParsed() {
        val valid = NodeSourceTemplate.render(NodeSourceForm("air", "https://e.com/s", 60, "air ", "k3n8x2qa"))
        val edits =
            listOf(
                valid.replaceFirst("# kiminobox:node-source v1\n", ""),
                valid.replace("proxy-providers-merge", "proxy-providers"),
                valid.replace("type: http", "type: file"),
                valid.replace("interval: 60", "interval: \"60\""),
                valid.replace("src-k3n8x2qa.yaml", "air.yaml"),
                valid.replace("    type: http\n", "    type: http\n    health-check: {enable: true}\n"),
                valid.replace("      additional-prefix", "      additional-suffix"),
                valid + "rules:\n  - MATCH,DIRECT\n",
                valid + "  second:\n    type: http\n",
                valid.replace("    interval: 60\n", "    interval: 60\n    interval: 61\n"),
                "# kiminobox:node-source v1\n[unclosed",
            )

        edits.forEach { edited -> assertNull(edited, NodeSourceTemplate.parse(edited)) }
    }

    @Test
    fun nameMustBeUniqueAmongNodeSources() {
        val input = NodeSourceInput(name = "air", url = "https://e.com/s", prefix = "air ")

        assertEquals(NodeText.NAME_TAKEN, NodeSourceTemplate.validate(input, setOf("air", "self")).name)
        assertTrue(NodeSourceTemplate.validate(input, setOf("self", "Air")).isEmpty)
    }

    @Test
    fun invalidInputIsReported() {
        val ok = NodeSourceInput(name = "air", url = "https://e.com/s", prefix = "")
        assertTrue(NodeSourceTemplate.validate(ok, emptySet()).isEmpty)

        fun problems(input: NodeSourceInput) = NodeSourceTemplate.validate(input, emptySet())
        assertEquals(NodeText.NAME_REQUIRED, problems(ok.copy(name = " ")).name)
        assertEquals(NodeText.NAME_SPACES, problems(ok.copy(name = "air ")).name)
        assertEquals(NodeText.CONTROL_CHARS, problems(ok.copy(name = "a\nb")).name)
        listOf("", "ftp://e.com/s", "https://e.com/a b", "e.com/s").forEach { url ->
            assertEquals(url, NodeText.URL_INVALID, problems(ok.copy(url = url)).url)
        }
        listOf("", "-1", "1.5", "abc", "99999999999").forEach { interval ->
            assertEquals(interval, NodeText.INTERVAL_INVALID, problems(ok.copy(interval = interval)).interval)
        }
        assertNull(problems(ok.copy(interval = "0")).interval)
        assertEquals(NodeText.CONTROL_CHARS, problems(ok.copy(prefix = "a\tb")).prefix)
    }

    @Test
    fun anEmptyPrefixFieldFollowsTheName() {
        val input = NodeSourceInput(name = "✈️ air", url = "https://e.com/s")

        assertEquals("✈️ air ", NodeSourceTemplate.formFor(input, null) { "k3n8x2qa" }.prefix)
        assertEquals("", NodeSourceTemplate.formFor(input.copy(noPrefix = true), null) { "k3n8x2qa" }.prefix)
        assertEquals("A ", NodeSourceTemplate.formFor(input.copy(prefix = "A "), null) { "k3n8x2qa" }.prefix)

        val saved = NodeSourceForm("air", "https://e.com/s", 60, "air ", "k3n8x2qa")
        assertEquals(NodeSourceInput("air", "https://e.com/s", "60", ""), NodeSourceTemplate.inputOf(saved))
        assertTrue(NodeSourceTemplate.inputOf(saved.copy(prefix = "")).noPrefix)
        assertEquals("A ", NodeSourceTemplate.inputOf(saved.copy(prefix = "A ")).prefix)
    }

    @Test
    fun aChangedUrlGetsANewCachePath() {
        val saved = NodeSourceForm("air", "https://e.com/a", 60, "air ", "k3n8x2qa")

        val sameUrl =
            NodeSourceTemplate.formFor(NodeSourceTemplate.inputOf(saved).copy(prefix = "A "), saved) { "zzzzzzzz" }
        val newUrl =
            NodeSourceTemplate.formFor(NodeSourceTemplate.inputOf(saved).copy(url = " https://e.com/b "), saved) {
                "zzzzzzzz"
            }

        assertEquals(saved.copy(prefix = "A "), sameUrl)
        assertEquals(saved.copy(url = "https://e.com/b", pathId = "zzzzzzzz"), newUrl)
    }

    @Test
    fun newPathIdsLookLikeTheTemplate() {
        repeat(50) { assertTrue(Regex("[a-z0-9]{8}").matches(NodeSourceTemplate.newPathId())) }
    }

    @Test
    fun markersAndProviderNamesAreReadLoosely() {
        val text = NodeSourceTemplate.render(NodeSourceForm("air", "https://e.com/s", 60, "", "k3n8x2qa"))

        assertEquals(NodeTemplateKind.NodeSource, NodeTemplateKind.of(text))
        assertEquals(NodeTemplateKind.NodeSource, NodeTemplateKind.of("\uFEFF" + text.replace("\n", "\r\n")))
        assertNull(NodeTemplateKind.of("proxy-groups: []\n"))
        assertEquals(setOf("air"), NodeTemplates.providerNames(text))
        assertEquals(
            setOf("a", "b"),
            NodeTemplates.providerNames("proxy-providers:\n  a: {}\n+proxy-providers-merge:\n  b: {}\n"),
        )
        assertEquals(emptySet<String>(), NodeTemplates.providerNames("function main(c) { return c }"))
    }
}
