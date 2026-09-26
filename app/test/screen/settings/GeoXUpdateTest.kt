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

package com.github.yumeyucca.yumebox.screen.settings

import com.github.yumeyucca.yumebox.core.model.geoXItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeoXUpdateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val mmdbMarker =
        byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()) + "MaxMind.com".toByteArray()

    @Test
    fun emptyDownloadIsNeverUsable() {
        val file = tmp.newFile("geosite.dat.download")
        assertFalse(GeoXUpdate.isUsable(file))
    }

    @Test
    fun nonEmptyDatIsUsable() {
        val file = tmp.newFile("geosite.dat.download").apply { writeBytes(ByteArray(64) { 1 }) }
        assertTrue(GeoXUpdate.isUsable(file))
    }

    @Test
    fun mmdbNeedsTheMaxMindMetadata() {
        val page = tmp.newFile("country.mmdb.download").apply { writeText("<html>rate limited</html>") }
        assertFalse(GeoXUpdate.isUsable(page))

        val database =
            tmp.newFile("ASN.mmdb.download").apply {
                writeBytes(ByteArray(200_000) { 7 } + mmdbMarker + ByteArray(300) { 2 })
            }
        assertTrue(GeoXUpdate.isUsable(database))
    }

    @Test
    fun messageNamesTheKeptFilesAndTheRestart() {
        val (geoip, geosite, country) = geoXItems
        val result = GeoXUpdate.Result(updated = listOf(geoip, geosite), failed = listOf(country))

        assertEquals(
            "下载完成：2/3；失败：Country.mmdb，已保留原文件；重启 VPN 后生效",
            GeoXUpdate.message(result, vpnRunning = true),
        )
        assertEquals(
            "下载完成：2/3；失败：Country.mmdb，已保留原文件",
            GeoXUpdate.message(result, vpnRunning = false),
        )
    }

    @Test
    fun noRestartHintWhenNothingChanged() {
        val result = GeoXUpdate.Result(updated = emptyList(), failed = geoXItems.take(1))
        assertEquals(
            "下载完成：0/1；失败：GeoIP.dat，已保留原文件",
            GeoXUpdate.message(result, vpnRunning = true),
        )
    }
}
