/*
 * This file is part of YumeBox.
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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.feature.editor.screen


import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha // KimiNoBox
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.feature.editor.editor.CodeEditor
import com.github.yumeyucca.yumebox.feature.editor.editor.WordWrapButton // KimiNoBox
import com.github.yumeyucca.yumebox.feature.editor.editor.rememberConfiguredCodeEditorState
import com.github.yumeyucca.yumebox.feature.editor.format.CodeFormatter
import com.github.yumeyucca.yumebox.feature.editor.language.LanguageScope
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.presentation.component.SmallTopBar
import com.github.yumeyucca.yumebox.presentation.icon.Yume
import com.github.yumeyucca.yumebox.presentation.icon.yume.ListCollapse
import com.github.yumeyucca.yumebox.presentation.icon.yume.Save
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons // KimiNoBox
import top.yukonga.miuix.kmp.icon.extended.Back // KimiNoBox
import top.yukonga.miuix.kmp.icon.extended.Redo // KimiNoBox
import top.yukonga.miuix.kmp.icon.extended.Undo // KimiNoBox

@Composable
fun ConfigPreviewScreen(
    navigator: Navigator,
    title: String = YumeTxt.Editor.Title.Preview,
    initialContent: String = "",
    language: LanguageScope = LanguageScope.Yaml,
    onSave: (suspend (String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val formattedContent =
        remember(initialContent, language) {
            if (language == LanguageScope.Json) {
                CodeFormatter.format(initialContent, language) ?: initialContent
            } else {
                initialContent
            }
        }

    val editorState =
        rememberConfiguredCodeEditorState(
            initialContent = formattedContent,
            language = language,
            readOnly = false,
        )
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SmallTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                // KimiNoBox: a real back button; undo and redo moved next to format and save
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(MiuixIcons.Back, contentDescription = YumeTxt.Component.Navigation.Back)
                    }
                },
                actions = {
                    WordWrapButton(Modifier.padding(end = UiDp.dp12)) // KimiNoBox
                    // KimiNoBox: miuix does not dim a disabled IconButton, so the icon fades itself
                    val canUndo = editorState.canUndo()
                    val canRedo = editorState.canRedo()
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        onClick = { editorState.undo() },
                        enabled = canUndo,
                    ) {
                        Icon(
                            MiuixIcons.Undo,
                            contentDescription = "撤销",
                            modifier = Modifier.alpha(if (canUndo) 1f else DISABLED_ALPHA),
                        )
                    }
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        onClick = { editorState.redo() },
                        enabled = canRedo,
                    ) {
                        Icon(
                            MiuixIcons.Redo,
                            contentDescription = "重做",
                            modifier = Modifier.alpha(if (canRedo) 1f else DISABLED_ALPHA),
                        )
                    }
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        onClick = {
                            // KimiNoBox: say why nothing changed
                            if (!editorState.format()) {
                                context.toast(
                                    if (CodeFormatter.format(editorState.content, language) == null) {
                                        "内容有语法错误，无法格式化"
                                    } else {
                                        "已是标准格式"
                                    }
                                )
                            }
                        },
                    ) {
                        Icon(
                            Yume.ListCollapse,
                            contentDescription = YumeTxt.Editor.Action.Format,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isSaving || onSave == null) return@IconButton
                            // KimiNoBox: saving stays enabled so an unmodified save can say so
                            if (!editorState.isModified) {
                                context.toast("没有修改")
                                return@IconButton
                            }
                            coroutineScope.launch {
                                isSaving = true
                                runCatching { onSave(editorState.content) }
                                    .onSuccess {
                                        editorState.resetModified()
                                        navigator.navigateUp()
                                    }
                                    .onFailure {
                                        context.toast(
                                            it.message ?: YumeTxt.Editor.Message.SaveFailed
                                        )
                                    }
                                isSaving = false
                            }
                        },
                        enabled = onSave != null && !isSaving, // KimiNoBox
                    ) {
                        Icon(
                            Yume.Save,
                            contentDescription = YumeTxt.Editor.Action.Save,
                            modifier = Modifier.alpha(if (onSave != null) 1f else DISABLED_ALPHA), // KimiNoBox
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
        ) {
            CodeEditor(state = editorState, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val DISABLED_ALPHA = 0.3f // KimiNoBox
