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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelayTestUrlTest {
    @Test
    fun theDefaultComesFirstAndEveryPresetIsSavedAsIs() {
        assertEquals(DelayTestUrl.DEFAULT, DelayTestUrl.presets.first().url)
        DelayTestUrl.presets.forEach { preset -> assertEquals(preset.url, DelayTestUrl.normalize(preset.url)) }
        assertEquals(DelayTestUrl.presets.size, DelayTestUrl.presets.map { it.url }.toSet().size)
    }

    @Test
    fun httpAndHttpsUrlsWithAHostAreKeptWithoutTheSurroundingSpaces() {
        assertEquals("http://1.1.1.1", DelayTestUrl.normalize("  http://1.1.1.1 \n"))
        assertEquals("https://e.com:8443/204?a=1&b=2", DelayTestUrl.normalize("https://e.com:8443/204?a=1&b=2"))
        assertEquals("HTTPS://my_host.lan/", DelayTestUrl.normalize("HTTPS://my_host.lan/"))
    }

    @Test
    fun anythingTheCoreCannotFetchIsRefused() {
        listOf(
            "",
            "   ",
            "www.gstatic.com/generate_204",
            "ftp://e.com/file",
            "socks5://1.2.3.4:1080",
            "https://",
            "https:///generate_204",
            "https://e .com/",
            "https://e.com/a b",
        ).forEach { input -> assertNull(input, DelayTestUrl.normalize(input)) }
    }

    @Test
    fun onlyTheExactUrlOfAPresetSelectsIt() {
        assertEquals("Cloudflare", DelayTestUrl.presetOf("https://cp.cloudflare.com/generate_204")?.label)
        assertNull(DelayTestUrl.presetOf("http://cp.cloudflare.com/generate_204"))
        assertNull(DelayTestUrl.presetOf("https://cp.cloudflare.com/generate_204/"))
    }
}
