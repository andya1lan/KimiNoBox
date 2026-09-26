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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionContentTest {
    private val links =
        """
        ss://YWVzLTEyOC1nY206dGVzdA@192.0.2.11:8388#HK-01
        trojan://secret@example.com:443?sni=example.com#JP-01
        vless://uuid@example.com:443?type=ws#US-01
        """.trimIndent()

    @Test
    fun acceptsClashYamlWithEmojiNames() {
        val yaml =
            """
            proxies:
              - {name: "🇭🇰 香港 01", type: ss, server: 192.0.2.11, port: 8388, cipher: aes-128-gcm, password: test}
              - name: 🚀 日本 IPLC
                type: trojan
                server: example.com
                port: 443
                password: secret
            proxy-groups:
              - {name: 🔰 节点选择, type: select, proxies: [🇭🇰 香港 01]}
            """.trimIndent()

        val check = SubscriptionContent.inspect(yaml.toByteArray())

        assertTrue(check.error, check.ok)
        assertEquals(ContentCheck.Format.ClashYaml, check.format)
        assertEquals(listOf("🇭🇰 香港 01", "🚀 日本 IPLC"), check.nodeNames)
    }

    @Test
    fun acceptsPlainAndBase64ShareLinks() {
        val encoded = Base64.getEncoder().encodeToString(links.toByteArray())
        listOf(links, encoded.chunked(76).joinToString("\n"), encoded.trimEnd('=')).forEach { body ->
            val check = SubscriptionContent.inspect(body)
            assertTrue(check.ok)
            assertEquals(ContentCheck.Format.ShareLinks, check.format)
            assertEquals(3, check.nodeCount)
        }
    }

    @Test
    fun rejectsHtmlEmptyAndEmptyLists() {
        assertEquals(NodeText.CONTENT_HTML, SubscriptionContent.inspect("<!DOCTYPE html><html><body>502</body></html>").error)
        assertEquals(NodeText.CONTENT_EMPTY, SubscriptionContent.inspect("  \n").error)
        assertEquals(NodeText.CONTENT_NO_PROXIES, SubscriptionContent.inspect("proxies: []\n").error)
        assertEquals(NodeText.CONTENT_NO_PROXIES, SubscriptionContent.inspect("proxies: 3\n").error)
    }

    @Test
    fun rejectsTextWithoutNodes() {
        listOf(
                "mixed-port: 7890\nproxy-groups: []\n",
                "Please visit https://example.com to renew",
                "https://example.com/sub\n",
                "not base64 at all !!!",
            )
            .forEach { body ->
                val check = SubscriptionContent.inspect(body)
                assertFalse(body, check.ok)
                assertNull(check.format)
                assertEquals(body, NodeText.CONTENT_UNRECOGNIZED, check.error)
            }
    }

    @Test
    fun acceptsALargeEmojiSubscription() {
        val flags = listOf("🇭🇰", "🇯🇵", "🇸🇬", "🇺🇸", "🇹🇼", "🇬🇧")
        val body =
            buildString {
                append("proxies:\n")
                repeat(30_000) { index ->
                    append("  - {name: \"").append(flags[index % flags.size]).append(" 节点 ").append(index)
                    append(" ✈️\", type: ss, server: 192.0.2.1, port: ").append(10_000 + index % 50_000)
                    append(", cipher: aes-128-gcm, password: \"p").append(index).append("\"}\n")
                }
            }

        val check = SubscriptionContent.inspect(body.toByteArray())

        assertTrue(check.error, check.ok)
        assertEquals(30_000, check.nodeCount)
    }
}
