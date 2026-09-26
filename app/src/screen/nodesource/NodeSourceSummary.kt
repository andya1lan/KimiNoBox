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

package com.github.yumeyucca.yumebox.screen.nodesource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.github.yumeyucca.yumebox.nodesource.NodeSourceManager
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: the node source line of a profile card (C3 ①), in the card's own miuix style. One
 * source reads like a remote subscription: update time, traffic, expiry; several are counted.
 */
@Composable
fun NodeSourceSummary(profileId: String, onClick: () -> Unit) {
    val manager: NodeSourceManager = koinInject()
    val sources by manager.sources.collectAsState()
    val bound = sources.filter { profileId in it.boundProfileIds }
    val colors = MiuixTheme.colorScheme
    val text =
        when (bound.size) {
            0 -> "节点源：无 · 点此添加"
            1 -> {
                val source = bound.single()
                val info = source.state.info
                listOfNotNull(
                        "节点源「${source.form.name}」",
                        info?.updatedAt?.let(NodeSourceFormat::relativeTime),
                        info?.let(NodeSourceFormat::traffic),
                        info?.let(NodeSourceFormat::expiry),
                    )
                    .joinToString(" · ")
            }
            else -> "节点源 ${bound.size} 个 · 点此管理"
        }
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = if (bound.any { it.state.lastError != null }) colors.error else colors.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.space4).clickable(onClick = onClick),
    )
}

/** KimiNoBox: the 「从节点源新建配置」 entry under the add-profile card and in the empty page. */
@Composable
fun NewFromSourcesEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "从节点源新建配置",
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = AppTheme.spacing.space8),
    )
}
