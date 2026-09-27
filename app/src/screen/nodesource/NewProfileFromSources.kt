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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.nodesource.CheckedOrder
import com.github.yumeyucca.yumebox.nodesource.NodeSourceManager
import com.github.yumeyucca.yumebox.nodesource.NodeSourceProfileFactory
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.nodesource.SelfNodesTemplate
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.presentation.navigation.Route
import com.github.yumeyucca.yumebox.screen.profiles.ProfilesViewModel
import java.io.Serializable
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val ACL4SSR_ID = "builtin-acl4ssr-online-full"

/**
 * KimiNoBox: 「从节点源新建配置」 (C5): pick node sources and a rule override; the App makes a
 * local profile that binds them. A page of its own (docs/plan-a-round4.md E1): 「新建节点源」 opens
 * the editor on top of it, and coming back keeps what was filled in and checks the new source.
 * Everything picked is saveable state, since the page leaves composition while the editor is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProfileFromSourcesScreen(navigator: Navigator) {
    val manager: NodeSourceManager = koinInject()
    val factory: NodeSourceProfileFactory = koinInject()
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by manager.sources.collectAsState()
    // Null until the overrides are read for the first time
    var selfChoices by rememberSaveable { mutableStateOf<List<Choice>?>(null) }
    var rules by rememberSaveable { mutableStateOf<List<Choice>>(emptyList()) }
    LaunchedEffect(Unit) {
        val others = manager.otherOverrides()
        selfChoices =
            others.filter { NodeTemplateKind.of(it.content) == NodeTemplateKind.SelfNodes }.map { config ->
                val count = SelfNodesTemplate.nodeCount(config.content)
                Choice(config.id, config.name, listOfNotNull("自建", count?.let { "$it 个节点" }).joinToString(" · "))
            }
        rules =
            others.filter { NodeTemplateKind.of(it.content) == null && it.id != ACL4SSR_ID }.map { config ->
                Choice(config.id, config.name, if (config.id.startsWith("builtin-")) "内置" else config.contentType.name)
            }
    }
    val choices =
        sources.map { Choice(it.id, it.form.name, it.state.info?.nodeCount?.let { count -> "$count 个节点" } ?: "还没有下载") } +
            selfChoices.orEmpty()
    val choiceIds = choices.mapNotNull { it.id }
    var checked by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // A single source is checked once, when the page first has its lists
    var picked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selfChoices) {
        if (picked || selfChoices == null) return@LaunchedEffect
        if (checked.isEmpty() && choiceIds.size == 1) checked = choiceIds
        picked = true
    }
    // The sources there were when 「新建节点源」 was tapped; one more on the way back is the new one
    var knownIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(choiceIds, knownIds) {
        val known = knownIds ?: return@LaunchedEffect
        val withNew = CheckedOrder.withAdded(checked, known, choiceIds)
        if (withNew != checked) {
            checked = withNew
            knownIds = null
        }
    }
    var rule by rememberSaveable { mutableStateOf<String?>(ACL4SSR_ID) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameTyped by rememberSaveable { mutableStateOf(false) }
    val suggested = checked.firstOrNull()?.let { id -> choices.firstOrNull { it.id == id }?.label }.orEmpty()
    val shownName = if (nameTyped) name else suggested
    var creating by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val ruleChoices =
        listOf(Choice(ACL4SSR_ID, "ACL4SSR Online Full", "内置")) + rules +
            listOf(Choice(null, "不使用规则覆写", "只用基础配置里的一个组和一条规则"))
    val create: () -> Unit = {
        creating = true
        scope.launch {
            runCatching { factory.create(shownName.trim(), checked + listOfNotNull(rule)) }
                .onSuccess {
                    context.toast("已创建「${shownName.trim()}」")
                    profilesViewModel.refreshProfiles()
                    navigator.navigateUp()
                }
                .onFailure { failure = it.message ?: it::class.java.simpleName }
            creating = false
        }
    }
    // Leaving now would cancel the creation half way
    BackHandler(enabled = creating) {}

    NodeSourceTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("从节点源新建配置") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateUp() }, enabled = !creating) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = create,
                            enabled = !creating && selfChoices != null && checked.isNotEmpty() && shownName.isNotBlank(),
                        ) {
                            if (creating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("创建")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = shownName,
                        onValueChange = {
                            name = it
                            nameTyped = true
                        },
                        label = { Text("配置名称") },
                        supportingText = { Text("默认用第一个勾选的节点源的名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item { SectionHeader("节点源") }
                if (choices.isEmpty() && selfChoices != null) {
                    item {
                        ListItem(
                            headlineContent = { Text("还没有节点源") },
                            supportingContent = { Text("点下面的「新建节点源」添加一个") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                items(choices, key = { "source:" + it.id }) { choice ->
                    val selected = choice.id in checked
                    val toggle = { checked = if (selected) checked - choice.id!! else checked + choice.id!! }
                    ListItem(
                        headlineContent = { Text(choice.label) },
                        supportingContent = { Text(choice.detail) },
                        leadingContent = { Checkbox(checked = selected, onCheckedChange = { toggle() }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = toggle),
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("新建节点源", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier =
                            Modifier.clickable {
                                knownIds = choiceIds
                                navigator.push(Route.NodeSourceEdit())
                            },
                    )
                }
                item { SectionHeader("规则覆写") }
                items(ruleChoices, key = { "rule:" + it.id }) { choice ->
                    ListItem(
                        headlineContent = { Text(choice.label) },
                        supportingContent = { Text(choice.detail) },
                        leadingContent = { RadioButton(selected = rule == choice.id, onClick = { rule = choice.id }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { rule = choice.id },
                    )
                }
            }
        }
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

/** One row to pick: [id] is null for 「不使用规则覆写」. Serializable, to be kept as saveable state. */
private data class Choice(val id: String?, val label: String, val detail: String) : Serializable

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
