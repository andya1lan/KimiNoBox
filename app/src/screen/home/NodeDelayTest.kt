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

package com.github.yumeyucca.yumebox.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * KimiNoBox: the delay beside the node name on both home pages. A tap tests the node again, and the
 * refresh glyph turns while the test runs. [delay] is the node's last result: 0 or null when it was
 * never tested, below 0 when it timed out.
 */
@Composable
internal fun NodeDelayTest(
    delay: Int?,
    testing: Boolean,
    onTest: () -> Unit,
    fastColor: Color,
    slowColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val color =
        when {
            delay == null || delay == 0 -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            delay < 0 -> AppTheme.colors.latency.timeout
            delay < SLOW_DELAY_MS -> fastColor
            else -> slowColor
        }
    val label =
        when {
            delay == null || delay == 0 -> "--"
            delay < 0 -> YumeTxt.Proxy.Node.Timeout
            else -> YumeTxt.Home.NodeInfo.DelayValue.format(delay)
        }
    val rotation by
        rememberInfiniteTransition(label = "home_delay_test")
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
                label = "home_delay_test_rotation",
            )

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = SinkFeedback(),
                    enabled = !testing,
                    onClickLabel = YumeTxt.Proxy.Action.Test,
                    onClick = onTest,
                ),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = YumeTxt.Proxy.Action.Test,
            tint = color.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp).let { if (testing) it.rotate(rotation) else it },
        )
    }
}

/** Both home pages turned to the slower color from here on. */
private const val SLOW_DELAY_MS = 500
