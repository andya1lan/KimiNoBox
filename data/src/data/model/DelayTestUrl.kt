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

package com.github.yumeyucca.yumebox.data.model

import java.net.URI

/** One of the URLs offered by 「延迟测试地址」. */
data class DelayTestUrlPreset(val label: String, val url: String)

/**
 * KimiNoBox: the URL that the app's own delay tests (a group, one node, the home card) ask the core
 * to fetch through a node. Groups that test on their own keep the `url` of the config.
 */
object DelayTestUrl {
    /** mihomo's default, and the URL every test used before this setting existed. */
    const val DEFAULT = "https://www.gstatic.com/generate_204"

    /** All HTTPS: mihomo warns that some providers hijack plain-HTTP test URLs. The first is the default. */
    val presets: List<DelayTestUrlPreset> =
        listOf(
            DelayTestUrlPreset("Google", DEFAULT),
            DelayTestUrlPreset("Cloudflare", "https://cp.cloudflare.com/generate_204"),
            DelayTestUrlPreset("Apple", "https://www.apple.com/library/test/success.html"),
            DelayTestUrlPreset("V2EX", "https://www.v2ex.com/generate_204"),
        )

    fun presetOf(url: String): DelayTestUrlPreset? = presets.firstOrNull { it.url == url }

    /**
     * The URL to save for what the user typed, or null when it is not an `http` or `https` URL with
     * a host: the core cannot test any other scheme.
     */
    fun normalize(input: String): String? {
        val url = input.trim()
        if (url.any(Char::isWhitespace)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val web = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        return url.takeIf { web && !uri.rawAuthority.isNullOrEmpty() }
    }
}
