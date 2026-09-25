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

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.screen.moe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.yumeyucca.yumebox.data.network.IpInfo
import com.github.yumeyucca.yumebox.presentation.component.CountryFlagCircle
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import com.github.yumeyucca.yumebox.screen.home.maskIpAddress
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: egress IP of the app's own `api.ip.sb` probe (rule dependent, not necessarily the
 * selected node's address). Masked by default; tapping toggles the full address.
 */
@Composable
internal fun MoeCardExternalIp(ip: IpInfo?, modifier: Modifier = Modifier) {
    val address = ip?.ip?.takeIf { it.isNotBlank() } ?: return
    var revealed by rememberSaveable(address) { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MoeUi.Hero.infoRowMinHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    revealed = !revealed
                },
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Info.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagCircle(countryCode = ip.countryCode, size = AppTheme.spacing.space16)
        MoeCardLabel(YumeTxt.Home.IpInfo.ExitIp)
        Text(
            text = if (revealed) address else maskIpAddress(address),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
