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

package com.github.yumeyucca.yumebox.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.yumeyucca.yumebox.core.model.Proxy
import com.github.yumeyucca.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumeyucca.yumebox.domain.model.ProxyGroupInfo
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import com.github.yumeyucca.yumebox.runtime.client.ProxyFacade
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** KimiNoBox: the sections of [group]'s visible members, or null to show them as one list. */
@Composable
internal fun rememberNodeSourceSections(group: ProxyGroupInfo, visible: List<Proxy>): List<NodeSourceSections.Section>? {
    val settings: ProxyDisplaySettingsStore = koinInject()
    val proxyFacade: ProxyFacade = koinInject()
    val providerOrder: NodeSourceProviderOrder = koinInject()
    val enabled by settings.groupBySource.state.collectAsState()
    val profileId = proxyFacade.currentProfile.collectAsState().value?.uuid?.toString()
    val order by
        remember(profileId) { profileId?.let(providerOrder::of) ?: flowOf(emptyList()) }
            .collectAsState(initial = emptyList())
    // Starts from the last answer, so reopening a group does not show the flat list first
    val sourceNodes by
        produceState(lastSourceNodes, group.name, group.proxies.size, enabled) {
            if (enabled) value = proxyFacade.proxyProviderNodes().also { lastSourceNodes = it }
        }
    return remember(visible, sourceNodes, enabled, order) {
        if (enabled) NodeSourceSections.of(visible, sourceNodes, order) else null
    }
}

private var lastSourceNodes: Map<String, List<String>> = emptyMap()

@Composable
internal fun NodeSourceSectionHeader(title: String, count: Int) {
    Text(
        text = "$title · $count",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.fillMaxWidth().padding(start = UiDp.dp12, top = UiDp.dp12, bottom = UiDp.dp4),
    )
}
