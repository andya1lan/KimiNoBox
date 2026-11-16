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

package com.github.yumeyucca.yumebox.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.yumeyucca.yumebox.domain.model.ProxyGroupInfo

/**
 * 选中分组的唯一可变入口。
 *
 * 页面不再区分"选中项由谁托管":本地保存([rememberSelectedGroupController])和分栏共享 ViewModel 的那份([rememberSelectedGroupController]
 * 的托管重载)都只是这个 holder 的两种 构造,调用方只需交出一个 holder,而不是"名字 + 回调"两个参数。
 */
@Stable
class SelectedGroupController
    internal constructor(
        private val nameState: State<String?>,
        private val onSelect: (String?) -> Unit,
    ) {
        val name: String?
            get() = nameState.value

        /** 选中 [name];传 `null` 表示清空选中。 */
        fun select(name: String?) {
            onSelect(name)
        }

        fun selectGroup(group: ProxyGroupInfo) {
            select(group.name)
        }

        fun clear() {
            select(null)
        }
    }

/** 页面自己保存选中项(手机分栏、sheet)。 */
@Composable
fun rememberSelectedGroupController(): SelectedGroupController {
    val nameState = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(nameState) {
        SelectedGroupController(nameState) { next -> nameState.value = next }
    }
}

/** 选中项由调用方托管(分栏 shell 共享 ViewModel)。 */
@Composable
fun rememberSelectedGroupController(
    name: String?,
    onSelect: (String?) -> Unit,
): SelectedGroupController {
    val latestOnSelect by rememberUpdatedState(onSelect)
    val nameState = rememberUpdatedState(name)
    return remember { SelectedGroupController(nameState) { next -> latestOnSelect(next) } }
}

/** [SelectedGroupController.name] 在 [proxyGroups] 里解析出的分组;纯值,不含回调。 */
@Immutable
data class ProxyGroupSelectionState(
    val selectedGroupName: String?,
    val selectedGroup: ProxyGroupInfo?,
    val displayGroup: ProxyGroupInfo?,
)

@Composable
fun rememberProxyGroupSelectionState(
    proxyGroups: List<ProxyGroupInfo>,
    onRefreshGroup: (String) -> Unit,
    retainLastKnownGroup: Boolean,
    controller: SelectedGroupController,
    /**
     * KimiNoBox: the config the groups belong to (the proxy view model's loadedConfigKey). An open
     * group closes as soon as a non-null key differs from the one it was opened under, so any
     * profile or config change sends the page back to the group list. The opened-under key is
     * kept with the selection, not taken from a change event: those are missed while the page is
     * away and the key flow restarts from null.
     */
    configKey: String? = null,
    /** KimiNoBox: the opened-under key of a selection the caller keeps; see [configKey]. */
    controlledOpenedKey: String? = null,
    onControlledOpenedKeyChange: ((String?) -> Unit)? = null,
): ProxyGroupSelectionState {
    val selectedGroupName = controller.name
    val selectedGroupSnapshotState = remember { mutableStateOf<ProxyGroupInfo?>(null) }
    val selectedGroup =
        remember(selectedGroupName, proxyGroups) {
            selectedGroupName?.let { groupName -> proxyGroups.firstOrNull { group -> group.name == groupName } }
        }
    val displayGroup =
        remember(selectedGroup, selectedGroupSnapshotState.value, retainLastKnownGroup) {
            selectedGroup ?: selectedGroupSnapshotState.value.takeIf { retainLastKnownGroup }
        }

    LaunchedEffect(selectedGroup, retainLastKnownGroup) {
        if (retainLastKnownGroup) {
            selectedGroup?.let { selectedGroupSnapshotState.value = it }
        }
    }

    // KimiNoBox: the last known group only bridges an empty list (a reload). A loaded list
    // without it belongs to another profile or config, so the page goes back to the group list.
    val groupsLoaded = proxyGroups.isNotEmpty()
    LaunchedEffect(
        controller,
        selectedGroupName,
        selectedGroup,
        retainLastKnownGroup,
        groupsLoaded,
    ) {
        if ((!retainLastKnownGroup || groupsLoaded) && selectedGroupName != null && selectedGroup == null) {
            controller.clear()
        }
    }

    // KimiNoBox: a group opened while the key was still unknown takes the first known key; after
    // that, any other key means another profile or config, and the page goes back to the list.
    val uncontrolledOpenedKey = rememberSaveable { mutableStateOf<String?>(null) }
    val openedKey = if (onControlledOpenedKeyChange != null) controlledOpenedKey else uncontrolledOpenedKey.value
    val setOpenedKey by rememberUpdatedState { key: String? ->
        if (onControlledOpenedKeyChange != null) onControlledOpenedKeyChange(key) else uncontrolledOpenedKey.value = key
    }
    LaunchedEffect(controller, selectedGroupName, configKey, openedKey) {
        when {
            selectedGroupName == null -> if (openedKey != null) setOpenedKey(null)
            configKey == null -> Unit
            openedKey == null -> setOpenedKey(configKey)
            openedKey != configKey -> controller.clear()
        }
    }

    LaunchedEffect(selectedGroupName) { selectedGroupName?.let(onRefreshGroup) }

    return remember(selectedGroupName, selectedGroup, displayGroup) {
        ProxyGroupSelectionState(
            selectedGroupName = selectedGroupName,
            selectedGroup = selectedGroup,
            displayGroup = displayGroup,
        )
    }
}
