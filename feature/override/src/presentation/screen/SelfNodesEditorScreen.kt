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

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.feature.editor.editor.CodeEditor
import com.github.yumeyucca.yumebox.feature.editor.editor.CodeEditorState
import com.github.yumeyucca.yumebox.feature.editor.editor.WordWrapButton
import com.github.yumeyucca.yumebox.feature.editor.language.LanguageScope
import com.github.yumeyucca.yumebox.nodesource.NodeListYaml
import com.github.yumeyucca.yumebox.nodesource.NodeSourceProblems
import com.github.yumeyucca.yumebox.nodesource.NodeText
import com.github.yumeyucca.yumebox.nodesource.SelfNodesForm
import com.github.yumeyucca.yumebox.nodesource.SelfNodesTemplate
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch

/**
 * KimiNoBox: the 「自建节点」 page. The nodes are YAML in a code editor, a commented blank
 * template for a new source or the current node list when editing, with the provider name and
 * prefix in one row above it so the editor keeps its height when the keyboard is up. [original]
 * is the saved form when editing; [takenNames] are the provider names of the other node sources.
 */
@Composable
internal fun SelfNodesEditorScreen(
    original: SelfNodesForm?,
    takenNames: Set<String>,
    onSave: suspend (SelfNodesForm) -> Result<Unit>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(original?.name ?: SelfNodesTemplate.DEFAULT_NAME) }
    var prefix by remember { mutableStateOf(original?.prefix.orEmpty()) }
    val editorState = remember {
        CodeEditorState(
            original?.let { NodeListYaml.render(it.nodes) } ?: NodeText.BLANK_NODES,
            LanguageScope.Yaml,
        )
    }
    var headerProblems by remember { mutableStateOf(NodeSourceProblems()) }
    var nodeProblems by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    NodeEditorScaffold(
        title = if (original == null) NodeText.NEW_SELF_NODES else NodeText.EDIT_SELF_NODES,
        saving = saving,
        onSave = {
            val parsed = NodeListYaml.parse(editorState.content)
            headerProblems = SelfNodesTemplate.problems(name, prefix, takenNames)
            nodeProblems =
                parsed.fold(NodeListYaml::problems) { listOf(it.message ?: NodeText.UNKNOWN_SHAPE) }
            val nodes = parsed.getOrNull()
            if (headerProblems.isEmpty && nodeProblems.isEmpty() && nodes != null && !saving) {
                saving = true
                scope.launch {
                    onSave(SelfNodesForm(name, prefix, nodes))
                        .onFailure { context.toast(NodeText.saveFailed(it.message)) }
                    saving = false
                }
            }
        },
        onClose = onClose,
        scrollable = false,
        actions = { WordWrapButton(Modifier.padding(end = UiDp.dp12)) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.space12)) {
            NodeFormField(
                label = NodeText.NAME,
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                error = headerProblems.name,
            )
            NodeFormField(
                label = NodeText.PREFIX,
                value = prefix,
                onValueChange = { prefix = it },
                modifier = Modifier.weight(1f),
                error = headerProblems.prefix,
            )
        }
        NodeHint(NodeText.SELF_NODES_HINT)
        nodeProblems.forEach { NodeHint(it, isError = true) }
        CodeEditor(state = editorState, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
