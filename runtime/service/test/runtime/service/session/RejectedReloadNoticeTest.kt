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

package com.github.yumeyucca.yumebox.runtime.service.session

import com.github.yumeyucca.yumebox.core.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RejectedReloadNoticeTest {
    private fun spec(uuid: String, name: String) =
        RuntimeSpec(
            profileUuid = uuid,
            profileName = name,
            profileDir = "/data/imported/$uuid",
            ageSecretKey = null,
            overrideSpecs = emptyList(),
            runMode = RunMode.VpnService,
            skipRuntimePatches = false,
            preview = false,
        )

    private val a = spec("a", "A")
    private val b = spec("b", "B")

    @Test
    fun aRefusedSwitchNamesBothProfiles() {
        val text = RejectedReloadNotice.textOf(rejected = b, restored = a, reason = "proxy [NOPE] not found", restoredLkg = false)

        assertEquals("没有切换到「B」", text.title)
        assertEquals(
            "「B」的配置有问题，已切回「A」，VPN 继续用「A」运行。\n\n原因：proxy [NOPE] not found",
            text.message,
        )
    }

    @Test
    fun aRefusedUpdateKeepsTheRunningConfigAndSaysWhenTheLkgCameBack() {
        val text = RejectedReloadNotice.textOf(rejected = a, restored = a, reason = "", restoredLkg = true)

        assertEquals("「A」的新配置没有生效", text.title)
        assertEquals(
            "新配置有问题，VPN 继续用之前的配置运行。\n「A」已恢复为上一个能启动的版本。\n\n原因：未知",
            text.message,
        )
    }
}
