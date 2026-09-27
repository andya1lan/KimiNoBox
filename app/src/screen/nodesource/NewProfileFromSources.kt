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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ACL4SSR_ID = "builtin-acl4ssr-online-full"

/**
 * KimiNoBox: 「从节点源新建配置」 (C5): pick node sources and overrides; the App makes a local
 * profile that binds the checked sources, then the checked overrides, each in the order shown
 * (docs/plan-a-round4.md E2). Both sections check and sort like the sheet's 覆写 section. A page
 * of its own (E1): 「新建节点源」 opens the editor on top of it, and coming back keeps what was
 * filled in and checks the new source. Everything picked is saveable state, since the page
 * leaves composition while the editor is open.
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
    var ruleChoices by rememberSaveable { mutableStateOf<List<Choice>>(emptyList()) }
    LaunchedEffect(Unit) {
        val others = manager.otherOverrides()
        selfChoices =
            others.filter { NodeTemplateKind.of(it.content) == NodeTemplateKind.SelfNodes }.map { config ->
                val count = SelfNodesTemplate.nodeCount(config.content)
                Choice(config.id, config.name, listOfNotNull("自建", count?.let { "$it 个节点" }).joinToString(" · "))
            }
        ruleChoices =
            others.filter { NodeTemplateKind.of(it.content) == null }.map { config ->
                Choice(config.id, config.name, if (config.id.startsWith("builtin-")) "内置" else config.contentType.name)
            }
    }
    val sourceChoices =
        sources.map { Choice(it.id, it.form.name, it.state.info?.nodeCount?.let { count -> "$count 个节点" } ?: "还没有下载") } +
            selfChoices.orEmpty()
    val sourceIds = sourceChoices.map { it.id }
    var checkedSources by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var checkedRules by rememberSaveable { mutableStateOf(listOf(ACL4SSR_ID)) }
    // A single source is checked once, when the page first has its lists
    var picked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selfChoices) {
        if (picked || selfChoices == null) return@LaunchedEffect
        if (checkedSources.isEmpty() && sourceIds.size == 1) checkedSources = sourceIds
        picked = true
    }
    // The sources there were when 「新建节点源」 was tapped; one more on the way back is the new one
    var knownIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(sourceIds, knownIds) {
        val known = knownIds ?: return@LaunchedEffect
        val withNew = CheckedOrder.withAdded(checkedSources, known, sourceIds)
        if (withNew != checkedSources) {
            checkedSources = withNew
            knownIds = null
        }
    }
    val (sourcesOn, sourcesOff) = CheckedOrder.split(sourceChoices, checkedSources) { it.id }
    val (rulesOn, rulesOff) = CheckedOrder.split(ruleChoices, checkedRules) { it.id }
    var name by rememberSaveable { mutableStateOf("") }
    var nameTyped by rememberSaveable { mutableStateOf(false) }
    val shownName = if (nameTyped) name else sourcesOn.firstOrNull()?.label.orEmpty()
    var creating by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val create: () -> Unit = {
        val chain = CheckedOrder.chain(sourcesOn.map { it.id }, rulesOn.map { it.id })
        creating = true
        scope.launch {
            runCatching { factory.create(shownName.trim(), chain) }
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
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            // A row only moves within its own section
            when {
                fromKey.startsWith(SOURCE_KEY) && toKey.startsWith(SOURCE_KEY) ->
                    checkedSources = CheckedOrder.moved(checkedSources, fromKey.removePrefix(SOURCE_KEY), toKey.removePrefix(SOURCE_KEY))
                fromKey.startsWith(RULE_KEY) && toKey.startsWith(RULE_KEY) ->
                    checkedRules = CheckedOrder.moved(checkedRules, fromKey.removePrefix(RULE_KEY), toKey.removePrefix(RULE_KEY))
            }
        }

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
                            enabled = !creating && selfChoices != null && sourcesOn.isNotEmpty() && shownName.isNotBlank(),
                        ) {
                            if (creating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("创建")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "name") {
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
                item(key = "sources-title") { SectionHeader("节点源") }
                if (sourceChoices.isEmpty() && selfChoices != null) {
                    item(key = "sources-empty") {
                        ListItem(
                            headlineContent = { Text("还没有节点源") },
                            supportingContent = { Text("点下面的「新建节点源」添加一个") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                // Checked ones on top in the order sorted, each with a handle; unchecked below, no handle
                items(sourcesOn, key = { SOURCE_KEY + it.id }) { choice ->
                    ReorderableItem(reorderState, key = SOURCE_KEY + choice.id) {
                        CheckRow(
                            title = choice.label,
                            detail = choice.detail,
                            checked = true,
                            onCheckedChange = { checkedSources = CheckedOrder.toggle(checkedSources, choice.id, it) },
                            handle = { DragHandle(Modifier.draggableHandle()) },
                        )
                    }
                }
                items(sourcesOff, key = { SOURCE_KEY + it.id }) { choice ->
                    CheckRow(
                        title = choice.label,
                        detail = choice.detail,
                        checked = false,
                        onCheckedChange = { checkedSources = CheckedOrder.toggle(checkedSources, choice.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "new-source") {
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
                            Modifier.animateItem().clickable {
                                knownIds = sourceIds
                                navigator.push(Route.NodeSourceEdit())
                            },
                    )
                }
                item(key = "rules-title") {
                    Column(Modifier.animateItem()) {
                        SectionHeader("规则覆写")
                        Text(
                            "勾选的覆写从上到下依次应用，拖动右侧把手调整顺序。都不勾时，只用基础配置里的一个组和一条规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(rulesOn, key = { RULE_KEY + it.id }) { choice ->
                    ReorderableItem(reorderState, key = RULE_KEY + choice.id) {
                        CheckRow(
                            title = choice.label,
                            detail = choice.detail,
                            checked = true,
                            onCheckedChange = { checkedRules = CheckedOrder.toggle(checkedRules, choice.id, it) },
                            handle = { DragHandle(Modifier.draggableHandle()) },
                        )
                    }
                }
                items(rulesOff, key = { RULE_KEY + it.id }) { choice ->
                    CheckRow(
                        title = choice.label,
                        detail = choice.detail,
                        checked = false,
                        onCheckedChange = { checkedRules = CheckedOrder.toggle(checkedRules, choice.id, it) },
                        modifier = Modifier.animateItem(),
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

/** One row to pick. Serializable, to be kept as saveable state. */
private data class Choice(val id: String, val label: String, val detail: String) : Serializable

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private const val SOURCE_KEY = "source:"
private const val RULE_KEY = "rule:"
