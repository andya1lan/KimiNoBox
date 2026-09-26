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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.github.yumeyucca.yumebox.data.model.OverrideConfig
import com.github.yumeyucca.yumebox.data.model.OverrideContentType
import com.github.yumeyucca.yumebox.data.model.OverrideMetadata
import com.github.yumeyucca.yumebox.data.store.OverrideConfigStore
import com.github.yumeyucca.yumebox.nodesource.NodeSourceForm
import com.github.yumeyucca.yumebox.nodesource.NodeSourceTemplate
import com.github.yumeyucca.yumebox.nodesource.NodeTemplateKind
import com.github.yumeyucca.yumebox.nodesource.NodeTemplates
import com.github.yumeyucca.yumebox.nodesource.SelfNodesForm
import com.github.yumeyucca.yumebox.nodesource.SelfNodesTemplate
import com.github.yumeyucca.yumebox.presentation.viewmodel.OverrideConfigViewModel
import org.koin.compose.koinInject
import tf.gal.yumebox.locale.YumeTxt

/** KimiNoBox: which node source form the override list shows in place of itself. */
internal sealed interface NodeEditorTarget {
    data class New(val kind: NodeTemplateKind) : NodeEditorTarget

    data class EditNodeSource(val config: OverrideConfig, val form: NodeSourceForm) :
        NodeEditorTarget

    class EditSelfNodes(val config: OverrideConfig, val form: SelfNodesForm) : NodeEditorTarget

    companion object {
        /** The form of a saved node source override; null once it was edited by hand. */
        fun of(config: OverrideConfig): NodeEditorTarget? =
            when (NodeTemplateKind.of(config.content)) {
                NodeTemplateKind.NodeSource ->
                    NodeSourceTemplate.parse(config.content)?.let { EditNodeSource(config, it) }

                NodeTemplateKind.SelfNodes ->
                    SelfNodesTemplate.parse(config.content)?.let { EditSelfNodes(config, it) }

                null -> null
            }
    }
}

/**
 * KimiNoBox: hosts a node source form and writes the generated YAML override through the
 * upstream store, so it is an ordinary override for the list, the apply sheet and the runtime.
 * [onSaved] runs once the override is written and the list reloaded.
 */
@Composable
internal fun NodeEditorHost(
    target: NodeEditorTarget,
    viewModel: OverrideConfigViewModel,
    onSaved: (OverrideConfig) -> Unit,
    onClose: () -> Unit,
) {
    val store: OverrideConfigStore = koinInject()
    val userConfigs by viewModel.userConfigs.collectAsState()
    val editing =
        when (target) {
            is NodeEditorTarget.EditNodeSource -> target.config
            is NodeEditorTarget.EditSelfNodes -> target.config
            is NodeEditorTarget.New -> null
        }
    val takenNames =
        remember(userConfigs, editing?.id) {
            NodeTemplates.takenNames(
                userConfigs
                    .filter { it.id != editing?.id && NodeTemplateKind.of(it.content) != null }
                    .map(OverrideConfig::content)
            )
        }
    val save: suspend (String, String) -> Result<Unit> = { name, content ->
        runCatching {
            onSaved(
                if (editing == null) {
                    store.createYamlOverride(name, content).also { viewModel.refreshAndAwait() }
                } else {
                    store.updateOverride(editing, name, content, viewModel)
                }
            )
        }
    }

    when (target) {
        is NodeEditorTarget.New ->
            when (target.kind) {
                NodeTemplateKind.NodeSource ->
                    NodeSourceEditorScreen(
                        original = null,
                        takenNames = takenNames,
                        onSave = { form -> save(form.name, NodeSourceTemplate.render(form)) },
                        onClose = onClose,
                    )

                NodeTemplateKind.SelfNodes ->
                    SelfNodesEditorScreen(
                        original = null,
                        takenNames = takenNames,
                        onSave = { form -> save(form.name, SelfNodesTemplate.render(form)) },
                        onClose = onClose,
                    )
            }

        is NodeEditorTarget.EditNodeSource ->
            NodeSourceEditorScreen(
                original = target.form,
                takenNames = takenNames,
                onSave = { form -> save(form.name, NodeSourceTemplate.render(form)) },
                onClose = onClose,
            )

        is NodeEditorTarget.EditSelfNodes ->
            SelfNodesEditorScreen(
                original = target.form,
                takenNames = takenNames,
                onSave = { form -> save(form.name, SelfNodesTemplate.render(form)) },
                onClose = onClose,
            )
    }
}

private suspend fun OverrideConfigStore.createYamlOverride(name: String, content: String): OverrideConfig {
    val now = System.currentTimeMillis()
    val config =
        OverrideConfig(
            id = OverrideMetadata.generateId(),
            name = name,
            description = null,
            contentType = OverrideContentType.Yaml,
            content = content,
            createdAt = now,
            updatedAt = now,
        )
    save(config)
    return config
}

/**
 * The name goes through the store; the content through the view model as well, which reloads
 * the running profile when it uses this override, like a save from the YAML editor.
 */
private suspend fun OverrideConfigStore.updateOverride(
    existing: OverrideConfig,
    name: String,
    content: String,
    viewModel: OverrideConfigViewModel,
): OverrideConfig {
    val config = existing.copy(name = name, content = content, updatedAt = System.currentTimeMillis())
    save(config)
    check(viewModel.saveConfigContent(config.id, content)) { YumeTxt.Override.Save.Failed }
    return config
}
