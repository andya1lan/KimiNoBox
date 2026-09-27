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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.nodesource.ContentCheck
import com.github.yumeyucca.yumebox.nodesource.FetchedSource
import com.github.yumeyucca.yumebox.nodesource.NodeSourceInput
import com.github.yumeyucca.yumebox.nodesource.NodeSourceManager
import com.github.yumeyucca.yumebox.nodesource.NodeSourceTemplate
import com.github.yumeyucca.yumebox.nodesource.NodeText
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * KimiNoBox: new or edited subscription node source (docs/plan-a-round2.md C2). The user gives the
 * URL and taps 「获取」; the App downloads it once, fills in the name from the file name, and shows
 * the nodes, traffic and expiry it got. The update interval starts at an hour and is only what
 * the user sets (docs/plan-a-round3.md D2). Saving writes that download as the master copy the
 * bound profiles start from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSourceEditScreen(navigator: Navigator, overrideId: String?, bindProfileId: String?) {
    val manager: NodeSourceManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by manager.sources.collectAsState()
    val existing = remember(sources, overrideId) { overrideId?.let { id -> sources.firstOrNull { it.id == id } } }
    if (overrideId != null && existing == null) return

    var input by remember(existing?.id) { mutableStateOf(existing?.form?.let(NodeSourceTemplate::inputOf) ?: NodeSourceInput()) }
    var fetched by remember { mutableStateOf<FetchedSource?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var nameMissing by remember { mutableStateOf(false) }
    var showProblems by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    // The name is checked when it changes (typed, or filled in by 获取) and once more on 保存,
    // against the names the other sources had then. Changes to those names later on, such as this
    // source's own save while the page is still open, don't check it again.
    var nameCheck by remember { mutableStateOf(NameCheck()) }
    val checkName: (String) -> Unit = { name ->
        val taken = manager.takenNames(exceptId = existing?.id)
        nameCheck =
            NameCheck(
                problem = NodeSourceTemplate.nameProblem(name, taken),
                suggestion = NodeSourceFormat.uniqueName(name, taken).takeIf { name in taken },
            )
    }
    val problems = remember(input, nameCheck) { NodeSourceTemplate.validate(input, emptySet()).copy(name = nameCheck.problem) }
    val urlChanged = existing == null || input.url.trim() != existing.form.url
    val needsFetch = urlChanged && fetched?.url != input.url.trim()

    val focusManager = LocalFocusManager.current
    val fetch: () -> Unit = {
        focusManager.clearFocus()
        if (!fetching) {
            fetching = true
            scope.launch {
                runCatching { manager.fetch(input.url) }
                    .onSuccess { result ->
                        fetched = result
                        nameMissing = result.meta.name == null
                        if (input.name.isBlank()) {
                            val taken = manager.takenNames(exceptId = existing?.id)
                            val name = result.meta.name?.let { NodeSourceFormat.uniqueName(it, taken) }.orEmpty()
                            input = input.copy(name = name)
                            checkName(name)
                        }
                    }
                    .onFailure { failure = it.message ?: it::class.java.simpleName }
                fetching = false
            }
        }
    }
    val save: () -> Unit = save@{
        if (saving) return@save
        showProblems = true
        checkName(input.name)
        if (!NodeSourceTemplate.validate(input, manager.takenNames(exceptId = existing?.id)).isEmpty) return@save
        if (needsFetch) {
            context.toast(NEED_FETCH)
            return@save
        }
        saving = true
        scope.launch {
            val form = NodeSourceTemplate.formFor(input, existing?.form)
            runCatching {
                    if (existing == null) manager.create(form, requireNotNull(fetched), bindProfileId)
                    else manager.save(existing, form, fetched?.takeIf { urlChanged })
                }
                .onSuccess {
                    context.toast(NodeText.SAVED)
                    navigator.navigateUp()
                }
                .onFailure { context.toast(NodeText.saveFailed(it.message)) }
            saving = false
        }
    }

    NodeSourceTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (existing == null) NodeText.NEW_NODE_SOURCE else NodeText.EDIT_NODE_SOURCE) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateUp() }) { Icon(MiuixIcons.Back, contentDescription = "返回") }
                    },
                    actions = {
                        TextButton(onClick = save, enabled = !saving && !fetching) { Text(NodeText.SAVE) }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Address and 「获取」, what the download gave, then the source's own settings
                OutlinedTextField(
                    value = input.url,
                    onValueChange = { input = input.copy(url = it) },
                    label = { Text(NodeText.URL) },
                    singleLine = true,
                    isError = showProblems && problems.url != null,
                    supportingText = (problems.url.takeIf { showProblems } ?: URL_SUPPORT.takeIf { existing == null })?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(
                    onClick = fetch,
                    enabled = !fetching && problems.url == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(FETCHING)
                    } else {
                        if (fetched != null) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        }
                        Text(if (fetched == null) FETCH else FETCH_AGAIN)
                    }
                }
                fetched?.let { FetchedCard(it) }
                Text(
                    SETTINGS,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = input.name,
                    onValueChange = {
                        input = input.copy(name = it)
                        nameMissing = false
                        checkName(it)
                    },
                    label = { Text(NodeText.NAME) },
                    singleLine = true,
                    isError = (showProblems || nameCheck.suggestion != null) && nameCheck.problem != null,
                    supportingText = {
                        val problem = nameCheck.problem?.takeIf { showProblems || nameCheck.suggestion != null }
                        Text(problem ?: (NAME_MISSING.takeIf { nameMissing } ?: NodeText.NAME_SUPPORT))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                nameCheck.suggestion?.let { suggestion ->
                    AssistChip(
                        onClick = {
                            input = input.copy(name = suggestion)
                            checkName(suggestion)
                        },
                        label = { Text("改用「$suggestion」") },
                    )
                }
                OutlinedTextField(
                    value = input.interval,
                    onValueChange = { input = input.copy(interval = it) },
                    label = { Text(NodeText.INTERVAL) },
                    singleLine = true,
                    isError = showProblems && problems.interval != null,
                    supportingText = { Text(problems.interval.takeIf { showProblems } ?: INTERVAL_SUPPORT) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = input.prefix,
                    onValueChange = { input = input.copy(prefix = it) },
                    label = { Text(PREFIX_LABEL) },
                    singleLine = true,
                    isError = showProblems && problems.prefix != null,
                    supportingText = { Text(problems.prefix.takeIf { showProblems } ?: PREFIX_SUPPORT) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    NodeText.TEMPLATE_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        failure?.let { reason ->
            val message = reason + "\n\n" + NodeText.NETWORK_HINT
            AlertDialog(
                onDismissRequest = { failure = null },
                title = { Text(FETCH_FAILED) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { failure = null }) { Text("确定") } },
                dismissButton = {
                    TextButton(onClick = { context.copyText(message) }) { Text("复制") }
                },
            )
        }
    }
}

@Composable
private fun FetchedCard(fetched: FetchedSource) {
    val info = fetched.info()
    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val format = if (fetched.check.format == ContentCheck.Format.ShareLinks) "分享链接" else "Clash YAML"
            Text("节点 ${fetched.check.nodeCount} 个 · $format", style = MaterialTheme.typography.titleSmall)
            NodeSourceFormat.traffic(info)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            NodeSourceFormat.usedFraction(info)?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            NodeSourceFormat.expiry(info)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (info.total == null && info.expire == null) {
                Text(
                    "订阅没有提供流量和到期信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The last check of the name: its problem, and a free name to offer when it is taken. */
private data class NameCheck(val problem: String? = null, val suggestion: String? = null)

internal fun Context.copyText(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("KimiNoBox", text))
    toast("已复制")
}

private const val URL_SUPPORT = "只需填地址，点「获取」后自动填好名称"
private const val FETCH = "获取"
private const val FETCH_AGAIN = "重新获取"
private const val FETCHING = "正在下载"
private const val FETCH_FAILED = "获取失败"
private const val SETTINGS = "设置"
private const val NEED_FETCH = "请先点「获取」下载一次订阅"
private const val NAME_MISSING = "订阅没有提供名称，请填写"
private const val INTERVAL_SUPPORT = "内核按这个间隔自动更新，0 表示不自动更新"
private const val PREFIX_LABEL = "节点前缀（可选）"
private const val PREFIX_SUPPORT = "加在每个节点名称前，留空则不加"
