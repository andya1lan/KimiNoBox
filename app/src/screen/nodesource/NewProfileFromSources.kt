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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.data.model.OverrideConfig
import com.github.yumeyucca.yumebox.nodesource.NodeSourceManager
import com.github.yumeyucca.yumebox.nodesource.NodeSourceProfileFactory
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.presentation.navigation.Route
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ACL4SSR_ID = "builtin-acl4ssr-online-full"

/**
 * KimiNoBox: 「从节点源新建配置」 (C5): pick node sources and a rule override; the App makes a
 * local profile that binds them. [onCreated] runs after the profile is imported.
 */
@Composable
fun NewProfileFromSourcesDialog(navigator: Navigator, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val manager: NodeSourceManager = koinInject()
    val factory: NodeSourceProfileFactory = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by manager.sources.collectAsState()
    var others by remember { mutableStateOf<List<OverrideConfig>>(emptyList()) }
    LaunchedEffect(Unit) { others = manager.otherOverrides() }
    val selfNodes = others.filter { NodeTemplateKind.of(it.content) == NodeTemplateKind.SelfNodes }
    val rules = others.filter { NodeTemplateKind.of(it.content) == null }
    val choices = sources.map { it.id to it.form.name } + selfNodes.map { it.id to it.name }
    var checked by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(choices.size) { if (checked.isEmpty() && choices.size == 1) checked = listOf(choices.single().first) }
    var rule by remember { mutableStateOf<String?>(ACL4SSR_ID) }
    var name by remember { mutableStateOf("") }
    var nameTyped by remember { mutableStateOf(false) }
    val suggested = checked.firstOrNull()?.let { id -> choices.firstOrNull { it.first == id }?.second }.orEmpty()
    val shownName = if (nameTyped) name else suggested
    var creating by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    NodeSourceTheme {
        AlertDialog(
            onDismissRequest = { if (!creating) onDismiss() },
            title = { Text("从节点源新建配置") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("节点源", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (choices.isEmpty()) {
                        Text("还没有节点源，先新建一个", style = MaterialTheme.typography.bodySmall)
                    }
                    choices.forEach { (id, label) ->
                        CheckRow(label, id in checked) {
                            checked = if (id in checked) checked - id else checked + id
                        }
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            navigator.push(Route.NodeSourceEdit())
                        }
                    ) { Text("新建节点源") }
                    Text("规则覆写", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val ruleChoices =
                        listOf(ACL4SSR_ID to "ACL4SSR Online Full（内置）") +
                            rules.filter { it.id != ACL4SSR_ID }.map { it.id to it.name } +
                            listOf(null to "不使用规则覆写")
                    ruleChoices.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { rule = id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = rule == id, onClick = { rule = id })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = shownName,
                        onValueChange = {
                            name = it
                            nameTyped = true
                        },
                        label = { Text("配置名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !creating && checked.isNotEmpty() && shownName.isNotBlank(),
                    onClick = {
                        creating = true
                        scope.launch {
                            runCatching { factory.create(shownName.trim(), checked + listOfNotNull(rule)) }
                                .onSuccess {
                                    context.toast("已创建「${shownName.trim()}」")
                                    onCreated()
                                    onDismiss()
                                }
                                .onFailure { failure = it.message ?: it::class.java.simpleName }
                            creating = false
                        }
                    },
                ) {
                    if (creating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("创建")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !creating) { Text("取消") } },
        )
        failure?.let { reason ->
            AlertDialog(
                onDismissRequest = { failure = null },
                title = { Text("创建失败") },
                text = { Text(reason) },
                confirmButton = { TextButton(onClick = { failure = null }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { context.copyText(reason) }) { Text("复制") } },
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
