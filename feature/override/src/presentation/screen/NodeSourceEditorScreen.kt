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

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.nodesource.NodeSourceForm
import com.github.yumeyucca.yumebox.nodesource.NodeSourceInput
import com.github.yumeyucca.yumebox.nodesource.NodeSourceTemplate
import com.github.yumeyucca.yumebox.nodesource.NodeText
import com.github.yumeyucca.yumebox.presentation.component.AppCard
import com.github.yumeyucca.yumebox.presentation.component.OverridePlainFormSection
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * KimiNoBox: the 「节点源」 form. [original] is the saved source when editing, null for a new one.
 * [takenNames] are the provider names of the other node sources.
 */
@Composable
internal fun NodeSourceEditorScreen(
    original: NodeSourceForm?,
    takenNames: Set<String>,
    onSave: suspend (NodeSourceForm) -> Result<Unit>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember {
        mutableStateOf(original?.let(NodeSourceTemplate::inputOf) ?: NodeSourceInput())
    }
    var showProblems by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val problems = remember(input, takenNames) { NodeSourceTemplate.validate(input, takenNames) }

    NodeEditorScaffold(
        title = if (original == null) NodeText.NEW_NODE_SOURCE else NodeText.EDIT_NODE_SOURCE,
        saving = saving,
        onSave = {
            showProblems = true
            if (problems.isEmpty && !saving) {
                saving = true
                scope.launch {
                    onSave(NodeSourceTemplate.formFor(input, original))
                        .onFailure { context.toast(NodeText.saveFailed(it.message)) }
                    saving = false
                }
            }
        },
        onClose = onClose,
    ) {
        NodeHint(NodeText.NODE_SOURCE_HINT)
        OverridePlainFormSection(title = NodeText.NODE_SOURCE) {
            NodeFormField(
                label = NodeText.NAME,
                value = input.name,
                onValueChange = { input = input.copy(name = it) },
                support = NodeText.NAME_SUPPORT,
                error = problems.name.takeIf { showProblems },
            )
            NodeFormField(
                label = NodeText.URL,
                value = input.url,
                onValueChange = { input = input.copy(url = it) },
                error = problems.url.takeIf { showProblems },
                keyboardType = KeyboardType.Uri,
            )
            NodeFormField(
                label = NodeText.INTERVAL,
                value = input.interval,
                onValueChange = { input = input.copy(interval = it) },
                support = NodeText.INTERVAL_SUPPORT,
                error = problems.interval.takeIf { showProblems },
                keyboardType = KeyboardType.Number,
            )
            // Empty follows the name, so the field is never rewritten while the name is typed.
            if (!input.noPrefix) {
                NodeFormField(
                    label = NodeText.PREFIX,
                    value = input.prefix,
                    onValueChange = { input = input.copy(prefix = it) },
                    support = NodeText.prefixSupport(input.effectivePrefix),
                    error = problems.prefix.takeIf { showProblems },
                )
            }
        }
        AppCard(applyHorizontalPadding = false) {
            SwitchPreference(
                title = NodeText.NO_PREFIX,
                checked = input.noPrefix,
                onCheckedChange = { input = input.copy(noPrefix = it) },
            )
        }
        NodeHint(NodeText.TEMPLATE_HINT)
    }
}
