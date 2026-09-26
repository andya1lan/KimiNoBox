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

package com.github.yumeyucca.yumebox.screen.log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val LOG_NOT_RUNNING_TITLE = "VPN 未运行"
internal const val LOG_NOT_RUNNING_HINT = "启动 VPN 后，这里显示内核的实时日志"

/** KimiNoBox: heads the list when the only lines are the ones left in `core.log` by the last run. */
@Composable
internal fun LogNotRunningNotice() {
    val spacing = AppTheme.spacing
    Text(
        text = "$LOG_NOT_RUNNING_TITLE。下面是上次运行时内核记录的错误",
        // Lines up with the text inside the log cards below.
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal + spacing.space12, vertical = spacing.space12),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
