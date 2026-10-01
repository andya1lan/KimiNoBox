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

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.ToastDialogBridge
import com.github.yumeyucca.yumebox.data.store.OverrideConfigStore
import com.github.yumeyucca.yumebox.data.store.ProfileBindingProvider
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.nodesource.NodeTemplates
import com.github.yumeyucca.yumebox.runtime.api.RuntimeOwner
import com.github.yumeyucca.yumebox.runtime.api.RuntimePhase
import com.github.yumeyucca.yumebox.runtime.service.RuntimeCoordinator
import com.github.yumeyucca.yumebox.runtime.service.config.ServiceStore
import com.github.yumeyucca.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import timber.log.Timber
import java.time.Instant

/**
 * KimiNoBox: after each VPN start or reload (the runtime settling on Running), names the
 * subscription node sources of the running profile that came up with no nodes, once. The core
 * lists groups before providers finish their first download, so a source counts as empty only
 * once the core logged its failed initial load, or after the core's download timeout.
 */
@Composable
fun NodeSourceEmptyHint() {
    val context = LocalContext.current.applicationContext
    val bindings: ProfileBindingProvider = koinInject()
    val overrides: OverrideConfigStore = koinInject()
    LaunchedEffect(Unit) {
        // Each start or reload settles in a state of its own generation. A recreated activity
        // sees that state again, so a load whose check already finished is not checked again.
        RuntimeCoordinator.state
            .filter { it.owner == RuntimeOwner.VpnService && it.phase == RuntimePhase.Running && !it.reloading }
            .distinctUntilChangedBy { it.generation }
            .filter { it.generation != checkedGeneration }
            .collectLatest { state ->
                checkEmptyNodeSources(context, bindings, overrides)
                checkedGeneration = state.generation
            }
    }
}

/** The runtime state generation whose node sources were last checked, for the whole process. */
@Volatile private var checkedGeneration = -1L

private suspend fun checkEmptyNodeSources(
    context: Context,
    bindings: ProfileBindingProvider,
    overrides: OverrideConfigStore,
) {
    val profileUuid = ServiceStore().activeProfile?.toString() ?: return
    val empty =
        withContext(Dispatchers.IO) {
            runCatching { emptyNodeSources(context, profileUuid, bindings, overrides) }
                .onFailure { Timber.w(it, "empty node source check failed") }
                .getOrDefault(emptyMap())
        }
    // The app-wide dialog host: this composable sits outside every Scaffold.
    if (empty.isNotEmpty()) ToastDialogBridge.show(message = summaryOf(empty), title = TITLE)
}

/** Empty node sources of the profile, each with the core's reason when it logged one. */
private suspend fun emptyNodeSources(
    context: Context,
    profileUuid: String,
    bindings: ProfileBindingProvider,
    overrides: OverrideConfigStore,
): Map<String, String?> {
    val names =
        bindings.getBinding(profileUuid)
            ?.overrideIds
            .orEmpty()
            .mapNotNull(overrides::getConfigContent)
            .filter { NodeTemplateKind.of(it) == NodeTemplateKind.NodeSource }
            .flatMap(NodeTemplates::providerNames)
    if (names.isEmpty()) return emptyMap()
    val since = System.currentTimeMillis() - LOG_LOOKBACK_MS
    val deadline = System.currentTimeMillis() + WAIT_MS
    while (true) {
        val sizes = CoreProcess.proxyProviderSizes(context)
        val emptyNames = names.filter { sizes[it] == 0 }
        if (emptyNames.isEmpty()) return emptyMap()
        val errors = initialProviderErrors(CoreProcess.coreDiagnosticLog(context), since)
        if (emptyNames.all { it in errors } || System.currentTimeMillis() >= deadline) {
            return emptyNames.associateWith { errors[it] }
        }
        delay(POLL_MS)
    }
}

private val initialErrorLine =
    Regex("""^time="([^"]+)" level=error msg="initial proxy provider (.+?) error: (.*)"$""")

/** `initial proxy provider <name> error: <reason>` lines of `core.log` since [sinceMillis]. */
internal fun initialProviderErrors(log: String, sinceMillis: Long): Map<String, String> =
    log.lineSequence()
        .mapNotNull { line -> initialErrorLine.matchEntire(line.trim())?.groupValues }
        .filter { (_, time) ->
            runCatching { Instant.parse(time).toEpochMilli() >= sinceMillis }.getOrDefault(false)
        }
        .associate { groups -> groups[2] to groups[3] }

internal fun summaryOf(empty: Map<String, String?>): String =
    empty.entries.joinToString("\n") { (name, reason) -> "「$name」" + reason?.let { "：$it" }.orEmpty() } +
        "\n\n订阅没下载成功或内容为空，这些节点不会出现在代理组里，没有其他节点的组已回落为 COMPATIBLE（直连）。" +
        "如果下载订阅需要特定网络，请先连上该网络，再在「外部资源」里更新。"

private const val TITLE = "节点源没有节点"
private const val POLL_MS = 2_000L

/** The core's HTTP download timeout is 20 s. */
private const val WAIT_MS = 25_000L

/** Covers the core launch before the snapshot turned Running. */
private const val LOG_LOOKBACK_MS = 60_000L
