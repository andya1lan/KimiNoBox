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

import java.net.URLDecoder
import java.nio.charset.Charset

/** Traffic and expiry from `subscription-userinfo`; bytes, and [expire] in epoch seconds. */
data class SubscriptionUsage(
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expire: Long? = null,
)

/**
 * What a subscription response says about itself. `Profile-Update-Interval` is not read: a node
 * source updates hourly unless the user sets another interval.
 */
data class SubscriptionMeta(
    /** From the `Content-Disposition` file name without its extension; null when absent. */
    val name: String?,
    val usage: SubscriptionUsage?,
)

/** Parsers for the subscription response headers that Clash clients understand. */
object SubscriptionHeaders {
    private val nameExtensions = listOf(".yaml", ".yml", ".txt")
    private val extendedFilename = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val plainFilename = Regex("""(?:^|;)\s*filename\s*=\s*("[^"]*"|[^;]*)""", RegexOption.IGNORE_CASE)

    /** [header] looks a header up by name, ignoring case. */
    fun parse(header: (String) -> String?): SubscriptionMeta =
        SubscriptionMeta(
            name = nameFromFilename(parseFilename(header("content-disposition"))),
            usage = parseUsage(header("subscription-userinfo")),
        )

    fun parseUsage(header: String?): SubscriptionUsage? {
        if (header.isNullOrBlank()) return null
        val fields =
            header.split(';')
                .mapNotNull { part ->
                    val kv = part.split('=', limit = 2)
                    if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                }
                .toMap()
        fun number(key: String) = fields[key]?.toBigDecimalOrNull()?.toLong()
        val usage =
            SubscriptionUsage(
                upload = number("upload"),
                download = number("download"),
                total = number("total"),
                expire = number("expire")?.takeIf { it > 0 },
            )
        return usage.takeIf { it != SubscriptionUsage() }
    }

    /** The file name of a `Content-Disposition`; `filename*=charset''…` wins over `filename=`. */
    fun parseFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        extendedFilename.find(contentDisposition)?.let { match ->
            val charset = match.groupValues[1].trim().ifEmpty { "UTF-8" }
            val encoded = match.groupValues[2].trim().trim('"')
            val decoded =
                runCatching { URLDecoder.decode(encoded, Charset.forName(charset).name()) }
                    .recoverCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }
                    .getOrNull()
            decoded?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        }
        return plainFilename.find(contentDisposition)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('"')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    /** The source name a file name suggests: its last path part without `.yaml`, `.yml` or `.txt`. */
    fun nameFromFilename(filename: String?): String? {
        val base = filename?.substringAfterLast('/')?.substringAfterLast('\\')?.trim() ?: return null
        val extension = nameExtensions.firstOrNull { base.endsWith(it, ignoreCase = true) }
        return (if (extension == null) base else base.dropLast(extension.length)).trim().takeIf(String::isNotEmpty)
    }
}
