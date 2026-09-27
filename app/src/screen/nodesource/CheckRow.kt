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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * KimiNoBox: one row to check, as in the sheet's 覆写 section and on 「从节点源新建配置」
 * (docs/plan-a-round4.md E2): a checkbox, a title and a detail line, and [handle] on the right
 * while the row can be sorted. A tap anywhere on the row toggles it.
 */
@Composable
internal fun CheckRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = onCheckedChange) },
        trailingContent = handle,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}

/** The handle a row is dragged by; only a drag from here moves the row. */
@Composable
internal fun DragHandle(modifier: Modifier) {
    Icon(
        DragHandleIcon,
        contentDescription = "拖动排序",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(8.dp).size(24.dp),
    )
}
