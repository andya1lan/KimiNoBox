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

import com.github.yumeyucca.yumebox.common.util.showToastDialog

/**
 * KimiNoBox: says that a reload was refused and which config keeps running, the way the core
 * reports a rejected config reload. A dialog, so that the whole reason can be read.
 */
internal object RejectedReloadNotice {
    data class Text(val title: String, val message: String)

    /** [restoredLkg]: the rejected version was replaced on disk by the profile's last good one. */
    fun show(rejected: RuntimeSpec, error: ReloadRejectedException, restoredLkg: Boolean) {
        val text = textOf(rejected, error.restored, error.message, restoredLkg)
        showToastDialog(message = text.message, title = text.title)
    }

    fun textOf(
        rejected: RuntimeSpec,
        restored: RuntimeSpec,
        reason: String?,
        restoredLkg: Boolean,
    ): Text {
        val name = rejected.profileName
        val because = "\n\n原因：" + (reason?.takeIf(String::isNotBlank) ?: "未知")
        val lkg = if (restoredLkg) "\n「$name」已恢复为上一个能启动的版本。" else ""
        return if (rejected.profileUuid != restored.profileUuid) {
            val running = restored.profileName
            Text(
                title = "没有切换到「$name」",
                message = "「$name」的配置有问题，已切回「$running」，VPN 继续用「$running」运行。$lkg$because",
            )
        } else {
            Text(
                title = "「$name」的新配置没有生效",
                message = "新配置有问题，VPN 继续用之前的配置运行。$lkg$because",
            )
        }
    }
}
