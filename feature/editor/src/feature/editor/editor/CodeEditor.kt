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

package com.github.yumeyucca.yumebox.feature.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.yumeyucca.yumebox.feature.editor.codemirror.CodeMirrorEditor

@Composable
fun CodeEditor(
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = DefaultEditorFontSizeSp,
    onScroll: ((Int) -> Unit)? = null,
    onTextChange: ((String) -> Unit)? = null,
) {
    // KimiNoBox: CodeMirror 6 in a WebView replaces Sora (feature/editor/codemirror)
    CodeMirrorEditor(state, modifier, fontSizeSp, onScroll, onTextChange)
}
