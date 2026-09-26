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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.data.model.OverrideConfig
import com.github.yumeyucca.yumebox.nodesource.NodeNameClash
import com.github.yumeyucca.yumebox.nodesource.NodeSource
import com.github.yumeyucca.yumebox.nodesource.NodeSourceManager
import com.github.yumeyucca.yumebox.nodesource.NodeSourceTemplate
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.presentation.navigation.Route
import com.github.yumeyucca.yumebox.runtime.api.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More

/**
 * KimiNoBox: the node sources and the other overrides of one profile (docs/plan-a-round2.md C3).
 * While it is open and the VPN runs, the numbers are read from the core every 5 seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSourceSheet(profile: Profile, navigator: Navigator, onDismiss: () -> Unit) {
    val manager: NodeSourceManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileId = profile.uuid.toString()
    val sources by manager.sources.collectAsState()
    val updating by manager.updating.collectAsState()
    val kernelNames by manager.kernelNodeNames.collectAsState()
    val binding by manager.boundOverrideIds(profileId).collectAsState(initial = null)
    var overrides by remember { mutableStateOf<List<OverrideConfig>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<NodeSource?>(null) }
    var picking by remember { mutableStateOf(false) }
    val bound = sources.filter { profileId in it.boundProfileIds }
    val boundIds = binding?.overrideIds.orEmpty()
    val clashes =
        remember(kernelNames, bound, boundIds, overrides) {
            val selfNames = overrides.filter { it.id in boundIds && NodeTemplateKind.of(it.content) == NodeTemplateKind.SelfNodes }
            val providers = bound.map { it.form.name } + selfNames.map { it.name } + "default"
            NodeNameClash.find(kernelNames.filterKeys { it in providers })
        }

    LaunchedEffect(Unit) { overrides = manager.otherOverrides() }
    LaunchedEffect(Unit) {
        while (isActive) {
            manager.refreshKernelInfo()
            delay(REFRESH_MS)
        }
    }
    val update: (List<NodeSource>) -> Unit = { targets ->
        scope.launch {
            val failed =
                targets.mapNotNull { source ->
                    runCatching { manager.update(source) }.exceptionOrNull()?.let { "「${source.form.name}」${it.message}" }
                }
            if (failed.isNotEmpty()) failure = failed.joinToString("\n\n")
        }
    }

    NodeSourceTheme {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("节点源与覆写", style = MaterialTheme.typography.titleLarge)
                    Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { SectionTitle("节点源") }
                if (bound.isEmpty()) {
                    item { Hint("还没有节点源。节点源的节点会进入配置里 include-all: true 的代理组") }
                }
                items(bound, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        updating = source.id in updating,
                        clash = clashes[source.form.name],
                        onUpdate = { update(listOf(source)) },
                        onEdit = {
                            onDismiss()
                            navigator.push(Route.NodeSourceEdit(overrideId = source.id))
                        },
                        onRemove = { scope.launch { manager.unbind(source.id, profileId) } },
                        onDelete = { deleting = source },
                        onAddPrefix = {
                            scope.launch {
                                manager.save(source, source.form.copy(prefix = NodeSourceTemplate.defaultPrefix(source.form.name)), null)
                            }
                        },
                        onShowError = { failure = it },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { update(bound) }, enabled = bound.isNotEmpty() && updating.isEmpty()) {
                            Text("全部更新")
                        }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { menu = true }) { Text("添加节点源") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("新建节点源") },
                                    onClick = {
                                        menu = false
                                        onDismiss()
                                        navigator.push(Route.NodeSourceEdit(bindProfileId = profileId))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("选择已有的节点源") },
                                    enabled = sources.any { profileId !in it.boundProfileIds },
                                    onClick = {
                                        menu = false
                                        picking = true
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("覆写")
                    Hint("勾选的覆写按顺序应用在这个配置上，新勾选的放在最后")
                }
                items(overrides, key = { it.id }) { config ->
                    val checked = config.id in boundIds
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { manager.setOverrideBound(profileId, config.id, !checked) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { scope.launch { manager.setOverrideBound(profileId, config.id, it) } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(config.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                overrideKind(config),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        failure?.let { reason ->
            AlertDialog(
                onDismissRequest = { failure = null },
                title = { Text("更新失败") },
                text = { Text(reason) },
                confirmButton = { TextButton(onClick = { failure = null }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { context.copyText(reason) }) { Text("复制") } },
            )
        }
        deleting?.let { source ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("删除节点源") },
                text = {
                    Text("删除「${source.form.name}」，并从 ${source.boundProfileIds.size} 个配置里移除。已下载的节点副本也会删除。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleting = null
                            scope.launch { manager.delete(source) }
                        }
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            )
        }
        if (picking) {
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("选择节点源") },
                text = {
                    Column {
                        sources.filter { profileId !in it.boundProfileIds }.forEach { source ->
                            Text(
                                source.form.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            picking = false
                                            scope.launch { manager.bind(source.id, profileId) }
                                        }
                                        .padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: NodeSource,
    updating: Boolean,
    clash: NodeNameClash.Clash?,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onAddPrefix: () -> Unit,
    onShowError: (String) -> Unit,
) {
    val info = source.state.info
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.form.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val parts =
                        listOfNotNull(
                            info?.nodeCount?.let { "$it 个节点" },
                            info?.updatedAt?.let { "更新于 " + NodeSourceFormat.relativeTime(it) },
                        )
                    Text(
                        parts.joinToString(" · ").ifEmpty { "还没有下载" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (updating) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(20.dp))
                } else {
                    TextButton(onClick = onUpdate) { Text("更新") }
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, contentDescription = "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("从此配置移除") }, onClick = { menu = false; onRemove() })
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (info != null) {
                NodeSourceFormat.traffic(info)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                NodeSourceFormat.usedFraction(info)?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                }
                NodeSourceFormat.expiry(info)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(NodeSourceFormat.origin(info), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            source.state.lastError?.let { error ->
                Text(
                    "上次更新失败：$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp).clickable { onShowError(error) },
                )
            }
            clash?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val other = if (it.other == "default") "配置内节点" else it.other
                    Text(
                        "有 ${it.count} 个节点与「$other」重名",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    if (source.form.prefix.isEmpty()) TextButton(onClick = onAddPrefix) { Text("加前缀") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun overrideKind(config: OverrideConfig): String =
    when {
        NodeTemplateKind.of(config.content) == NodeTemplateKind.SelfNodes -> "自建节点"
        config.id.startsWith("builtin-") -> "内置 · " + config.contentType.name
        else -> config.contentType.name
    }

private const val REFRESH_MS = 5_000L
