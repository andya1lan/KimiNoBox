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

@file:Suppress("UnnecessaryVariable")

package com.github.yumeyucca.yumebox.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.yumeyucca.yumebox.domain.model.ProxyGroupInfo

data class ProxyGroupSelectionState(
    val selectedGroupName: String?,
    val selectedGroup: ProxyGroupInfo?,
    val displayGroup: ProxyGroupInfo?,
    val selectGroup: (ProxyGroupInfo) -> Unit,
    val clearSelection: () -> Unit,
)

@Composable
fun rememberProxyGroupSelectionState(
    proxyGroups: List<ProxyGroupInfo>,
    onRefreshGroup: (String) -> Unit,
    retainLastKnownGroup: Boolean,
    /**
     * When non-null, selection is controlled by the caller (e.g. shared ViewModel for dual-pane).
     */
    controlledSelectedGroupName: String? = null,
    onControlledSelectedGroupNameChange: ((String?) -> Unit)? = null,
    /**
     * KimiNoBox: the config the groups belong to (the proxy view model's loadedConfigKey). An open
     * group closes as soon as a non-null key differs from the one it was opened under, so any
     * profile or config change sends the page back to the group list. The opened-under key is
     * kept with the selection, not taken from a change event: those are missed while the page is
     * away and the key flow restarts from null.
     */
    configKey: String? = null,
    /** KimiNoBox: the opened-under key of a controlled selection; see [configKey]. */
    controlledOpenedKey: String? = null,
    onControlledOpenedKeyChange: ((String?) -> Unit)? = null,
): ProxyGroupSelectionState {
    val uncontrolledNameState = rememberSaveable { mutableStateOf<String?>(null) }
    val uncontrolledOpenedKeyState = rememberSaveable { mutableStateOf<String?>(null) } // KimiNoBox
    val controlledSetter = onControlledSelectedGroupNameChange
    val selectedGroupName =
        if (controlledSetter != null) controlledSelectedGroupName else uncontrolledNameState.value
    val openedKey = if (controlledSetter != null) controlledOpenedKey else uncontrolledOpenedKeyState.value
    val latestConfigKey by rememberUpdatedState(configKey)
    val setOpenedKey =
        remember(controlledSetter, onControlledOpenedKeyChange) {
            { key: String? ->
                if (controlledSetter != null) onControlledOpenedKeyChange?.invoke(key)
                else uncontrolledOpenedKeyState.value = key
            }
        }
    val selectedGroupSnapshotState = remember { mutableStateOf<ProxyGroupInfo?>(null) }
    val selectGroup =
        remember(controlledSetter, setOpenedKey) {
            { group: ProxyGroupInfo ->
                setOpenedKey(latestConfigKey) // KimiNoBox
                if (controlledSetter != null) {
                    controlledSetter(group.name)
                } else {
                    uncontrolledNameState.value = group.name
                }
            }
        }
    val clearSelection =
        remember(controlledSetter, setOpenedKey) {
            {
                setOpenedKey(null) // KimiNoBox
                if (controlledSetter != null) {
                    controlledSetter(null)
                } else {
                    uncontrolledNameState.value = null
                }
            }
        }
    val selectedGroup =
        remember(selectedGroupName, proxyGroups) {
            selectedGroupName?.let { groupName ->
                proxyGroups.firstOrNull { group -> group.name == groupName }
            }
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
    LaunchedEffect(selectedGroupName, selectedGroup, retainLastKnownGroup, controlledSetter, groupsLoaded) {
        if ((!retainLastKnownGroup || groupsLoaded) && selectedGroupName != null && selectedGroup == null) {
            if (controlledSetter != null) {
                controlledSetter(null)
            } else {
                uncontrolledNameState.value = null
            }
        }
    }

    // KimiNoBox: a group opened while the key was still unknown takes the first known key; after
    // that, any other key means another profile or config, and the page goes back to the list.
    LaunchedEffect(selectedGroupName, configKey, openedKey) {
        if (selectedGroupName == null || configKey == null) return@LaunchedEffect
        when (openedKey) {
            null -> setOpenedKey(configKey)
            configKey -> Unit
            else -> clearSelection()
        }
    }

    LaunchedEffect(selectedGroupName) { selectedGroupName?.let(onRefreshGroup) }

    return remember(selectedGroupName, selectedGroup, displayGroup, selectGroup, clearSelection) {
        ProxyGroupSelectionState(
            selectedGroupName = selectedGroupName,
            selectedGroup = selectedGroup,
            displayGroup = displayGroup,
            selectGroup = selectGroup,
            clearSelection = clearSelection,
        )
    }
}
