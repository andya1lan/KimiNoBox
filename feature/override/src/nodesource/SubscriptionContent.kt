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

import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import java.util.Base64

/** Result of checking a downloaded subscription body. */
data class ContentCheck(
    val ok: Boolean,
    val format: Format? = null,
    val nodeCount: Int = 0,
    /** Node names, only known for the YAML format. */
    val nodeNames: List<String> = emptyList(),
    val error: String? = null,
) {
    enum class Format {
        ClashYaml,
        ShareLinks,
    }
}

/**
 * Accepts what mihomo's proxy provider accepts (`adapter/provider/provider.go`): a Clash YAML with
 * a non-empty `proxies` list, or share links, optionally base64 encoded as a whole. HTML error
 * pages, empty bodies and empty lists are rejected. Ported from the composite MVP.
 */
object SubscriptionContent {
    /**
     * Share-link schemes understood by mihomo's converter. `http(s)` links are left out on purpose:
     * a plain-text error page listing a URL must not pass as a subscription.
     */
    private val linkSchemes =
        setOf(
            "ss", "ssr", "vmess", "vless", "trojan", "hysteria", "hysteria2", "hy2",
            "hysteria2+realm", "hy2+realm", "tuic", "socks", "socks5", "socks5h", "anytls",
            "mierus",
        )

    fun inspect(body: ByteArray): ContentCheck = inspect(body.toString(Charsets.UTF_8))

    fun inspect(body: String): ContentCheck {
        val text = body.removePrefix("﻿")
        if (text.isBlank()) return failure(NodeText.CONTENT_EMPTY)
        if (looksLikeHtml(text)) return failure(NodeText.CONTENT_HTML)

        val yamlResult = inspectYaml(text)
        if (yamlResult != null) return yamlResult

        val links = countLinks(decodeWholeBase64(text) ?: text)
        return if (links > 0) {
            ContentCheck(ok = true, format = ContentCheck.Format.ShareLinks, nodeCount = links)
        } else {
            failure(NodeText.CONTENT_UNRECOGNIZED)
        }
    }

    /** Clash YAML check; null when the body is not a YAML mapping with `proxies` at all. */
    private fun inspectYaml(text: String): ContentCheck? {
        val root = runCatching { NodeYaml.composeForInspection(text) }.getOrNull() as? MappingNode ?: return null
        val proxies = root["proxies"] ?: return null
        val list = proxies as? SequenceNode ?: return failure(NodeText.CONTENT_NO_PROXIES)
        val names = list.value.mapNotNull { (it as? MappingNode)?.get("name")?.scalarText }
        if (names.isEmpty()) return failure(NodeText.CONTENT_NO_PROXIES)
        return ContentCheck(
            ok = true,
            format = ContentCheck.Format.ClashYaml,
            nodeCount = names.size,
            nodeNames = names,
        )
    }

    private fun looksLikeHtml(text: String): Boolean {
        val head = text.trimStart().take(1024).lowercase()
        return head.startsWith("<!doctype html") ||
            head.startsWith("<html") ||
            head.contains("<html") ||
            (head.startsWith("<") && (head.contains("<head") || head.contains("<body")))
    }

    private fun countLinks(text: String): Int =
        text.lineSequence().count { raw ->
            val line = raw.trim()
            val separator = line.indexOf("://")
            separator > 0 && line.substring(0, separator).lowercase() in linkSchemes
        }

    /** mihomo tries raw then padded standard base64 on the whole body (newlines ignored). */
    private fun decodeWholeBase64(text: String): String? {
        val compact = text.filterNot { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
        if (compact.isEmpty()) return null
        val decoders = listOf(Base64.getDecoder(), Base64.getUrlDecoder())
        val candidates = listOf(compact, compact.trimEnd('=')).distinct()
        for (decoder in decoders) {
            for (candidate in candidates) {
                val padded = candidate + "=".repeat((4 - candidate.length % 4) % 4)
                val decoded = runCatching { decoder.decode(padded) }.getOrNull() ?: continue
                return decoded.toString(Charsets.UTF_8)
            }
        }
        return null
    }

    private fun failure(message: String) = ContentCheck(ok = false, error = message)
}
