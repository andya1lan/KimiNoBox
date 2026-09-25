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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: the uptime normally lives in the sidebar; once the sidebar is collapsed it is shown
 * inside the card. [visibleFraction] is `1 - sidebarToggleProgress`, so it fades with the toggle.
 */
@Composable
internal fun MoeCardUptime(
    duration: MoeDurationPair,
    visibleFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (visibleFraction <= 0.01f) return
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = modifier.graphicsLayer { alpha = visibleFraction.coerceIn(0f, 1f) },
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Traffic.itemGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        MoeCardLabel("UPTIME")
        Text(
            text = "${duration.top}:${duration.bottom}",
            color = onSurface,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
        )
    }
}

/** KimiNoBox: small caps label matching the traffic strip's UP / DOWN labels. */
@Composable
internal fun MoeCardLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        style = MiuixTheme.textStyles.footnote1,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = MoeUi.Traffic.labelBottomPadding),
    )
}
