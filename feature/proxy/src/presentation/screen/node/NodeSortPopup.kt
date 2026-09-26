/*
 * This file is part of YumeBox.
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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.presentation.screen.node


import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.yumeyucca.yumebox.data.model.ProxySortMode
import com.github.yumeyucca.yumebox.data.store.ProxyDisplaySettingsStore
import org.koin.compose.koinInject
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup

internal val NodeSortModes =
    listOf(ProxySortMode.DEFAULT, ProxySortMode.BY_NAME, ProxySortMode.BY_LATENCY)

@Composable
internal fun NodeSortPopup(
    show: Boolean,
    onDismiss: () -> Unit,
    sortMode: ProxySortMode,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    onLocateCurrentProxy: (() -> Unit)? = null,
    onSortSelected: (ProxySortMode) -> Unit,
) {
    val settings: ProxyDisplaySettingsStore = koinInject() // KimiNoBox
    val groupBySource by settings.groupBySource.state.collectAsState() // KimiNoBox
    val entries =
        buildList {
            onLocateCurrentProxy?.let { locateCurrentProxy ->
                add(
                    DropdownEntry(
                        items =
                            listOf(
                                DropdownItem(
                                    text = YumeTxt.Proxy.Action.LocateCurrent,
                                    onClick = locateCurrentProxy,
                                )
                            ),
                    )
                )
            }
            // KimiNoBox: the node list in sections by node source (C6)
            add(
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = "按节点源分组",
                                selected = groupBySource,
                                onClick = { settings.groupBySource.set(!groupBySource) },
                            )
                        ),
                )
            )
            add(
                DropdownEntry(
                    items =
                        NodeSortModes.map { mode ->
                            DropdownItem(
                                text = mode.displayName,
                                selected = mode == sortMode,
                                onClick = {
                                    if (mode != sortMode) onSortSelected(mode)
                                },
                            )
                        },
                )
            )
        }

    WindowCascadingListPopup(
        show = show,
        entries = entries,
        alignment = alignment,
        onDismissRequest = onDismiss,
    )
}
