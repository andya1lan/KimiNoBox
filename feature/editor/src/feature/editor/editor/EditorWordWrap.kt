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

package com.github.yumeyucca.yumebox.feature.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.data.store.AppSettingsStore
import com.github.yumeyucca.yumebox.data.store.Preference
import com.github.yumeyucca.yumebox.presentation.component.GlassTopAction
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: soft wrap of the code editors (docs/plan-a-round4.md E4), one app-wide setting that
 * every [CodeEditor] follows. It is off by default, as the editors always were.
 */
@Composable
internal fun editorWordWrap(): Preference<Boolean> = koinInject<AppSettingsStore>().editorWordWrap

/** The 「自动换行」 button of an editor's top bar, highlighted while soft wrap is on. */
@Composable
fun WordWrapButton(modifier: Modifier = Modifier) {
    val setting = editorWordWrap()
    val on by setting.state.collectAsState()
    val colors = MiuixTheme.colorScheme
    IconButton(
        onClick = { setting.set(!on) },
        modifier = modifier,
        backgroundColor = if (on) colors.primary.copy(alpha = HIGHLIGHT_ALPHA) else Color.Unspecified,
    ) {
        Icon(WordWrapIcon, contentDescription = "自动换行", tint = if (on) colors.primary else colors.onSurface)
    }
}

/** The 「自动换行」 entry of a glass top action pill, tinted while soft wrap is on. */
@Composable
fun wordWrapTopAction(): GlassTopAction {
    val setting = editorWordWrap()
    val on by setting.state.collectAsState()
    return GlassTopAction(
        icon = WordWrapIcon,
        contentDescription = "自动换行",
        onClick = { setting.set(!on) },
        tint = if (on) MiuixTheme.colorScheme.primary else Color.Unspecified,
    )
}

/** Material's wrap_text icon, which the app's icon sets lack. */
private val WordWrapIcon: ImageVector by lazy {
    ImageVector.Builder(name = "WordWrap", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString(WRAP_TEXT_PATH).toNodes(), fill = SolidColor(Color.Black))
        .build()
}

private const val WRAP_TEXT_PATH =
    "M4 19h6v-2H4v2zM20 5H4v2h16V5zm-3 6H4v2h13.25c1.1 0 2 .9 2 2s-.9 2-2 2H15v-2l-3 3 3 3v-2h2c2.21 0 4-1.79 4-4s-1.79-4-4-4z"
private const val HIGHLIGHT_ALPHA = 0.12f
