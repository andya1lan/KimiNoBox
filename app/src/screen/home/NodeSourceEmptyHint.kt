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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.github.yumeyucca.yumebox.common.util.ToastDialogBridge
import com.github.yumeyucca.yumebox.data.store.OverrideConfigStore
import com.github.yumeyucca.yumebox.data.store.ProfileBindingProvider
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.nodesource.NodeTemplates
import com.github.yumeyucca.yumebox.runtime.api.Intents
import com.github.yumeyucca.yumebox.runtime.api.RuntimeOwner
import com.github.yumeyucca.yumebox.runtime.client.ProxyFacade
import com.github.yumeyucca.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import timber.log.Timber
import java.time.Instant

/**
 * KimiNoBox: after each VPN start or reload (the service's "profile loaded" broadcast), names the
 * subscription node sources of the running profile that came up with no nodes, once. The core
 * lists groups before providers finish their first download, so a source counts as empty only
 * once the core logged its failed initial load, or after the core's download timeout.
 */
@Composable
fun NodeSourceEmptyHint() {
    val context = LocalContext.current.applicationContext
    val proxyFacade: ProxyFacade = koinInject()
    val bindings: ProfileBindingProvider = koinInject()
    val overrides: OverrideConfigStore = koinInject()
    // The profile of the latest load, with a stamp so every load is checked.
    var loaded by remember { mutableStateOf<Pair<String, Long>?>(null) }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val uuid = intent?.getStringExtra(Intents.EXTRA_UUID) ?: return
                    loaded = uuid to SystemClock.elapsedRealtime()
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intents.actionProfileLoaded(context.packageName)),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(loaded) {
        val profileUuid = loaded?.first ?: return@LaunchedEffect
        // A start is announced before the app's snapshot reaches Running; a load that never gets
        // there (the preview core) is not a VPN start.
        val snapshot =
            withTimeoutOrNull(RUNNING_WAIT_MS) { proxyFacade.runtimeSnapshot.first { it.running } }
        if (snapshot?.owner != RuntimeOwner.VpnService) return@LaunchedEffect
        val empty =
            withContext(Dispatchers.IO) {
                runCatching { emptyNodeSources(context, profileUuid, bindings, overrides) }
                    .onFailure { Timber.w(it, "empty node source check failed") }
                    .getOrDefault(emptyMap())
            }
        // The app-wide dialog host: this composable sits outside every Scaffold.
        if (empty.isNotEmpty()) ToastDialogBridge.show(message = summaryOf(empty), title = TITLE)
    }
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
private const val RUNNING_WAIT_MS = 15_000L

/** The core's HTTP download timeout is 20 s. */
private const val WAIT_MS = 25_000L

/** Covers the core launch before the snapshot turned Running. */
private const val LOG_LOOKBACK_MS = 60_000L
