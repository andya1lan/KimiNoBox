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

package com.github.yumeyucca.yumebox.feature.editor.codemirror

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.yumeyucca.yumebox.presentation.theme.isDarkTheme
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The editor's colours: the page reads each one as a CSS variable (src/main.js). */
internal class CodeMirrorPalette(val background: Color, val css: JSONObject)

/**
 * The Cursor Light Colorblind / Cursor Dark colours the Sora editor used (EditorColorSynchronizer), with the app's
 * accent for selections and search matches.
 */
@Composable
internal fun rememberCodeMirrorPalette(): CodeMirrorPalette {
    val isDark = isDarkTheme()
    val accent = MiuixTheme.colorScheme.primary
    return remember(isDark, accent) {
        val tokens = if (isDark) DarkTokens else LightTokens
        val base = if (isDark) DarkBase else LightBase
        val css = JSONObject()
        (base + tokens).forEach { (name, color) -> css.put(name, color) }
        css.put("accent", accent.css())
        css.put("selection", accent.copy(alpha = 0.3f).css())
        css.put("match", accent.copy(alpha = 0.18f).css())
        css.put("match-current", accent.copy(alpha = 0.4f).css())
        CodeMirrorPalette(background = Color(android.graphics.Color.parseColor(base.getValue("bg"))), css = css)
    }
}

private fun Color.css(): String =
    "#%08X".format(toArgb()).let { argb -> "#" + argb.substring(3) + argb.substring(1, 3) }

private val LightBase =
    mapOf(
        "bg" to "#FCFCFC",
        "fg" to "#141414",
        "gutter" to "#1414145C",
        "gutter-active" to "#141414BD",
        "active-line" to "#EFEFEF",
        "cursor" to "#141414",
        "whitespace" to "#14141459",
        "bracket" to "#14141424",
        "panel" to "#F2F2F2",
    )

private val DarkBase =
    mapOf(
        "bg" to "#181818",
        "fg" to "#F0F0F0",
        "gutter" to "#F0F0F05C",
        "gutter-active" to "#F0F0F0",
        "active-line" to "#262626",
        "cursor" to "#F0F0F0",
        "whitespace" to "#6A6A6AB3",
        "bracket" to "#F0F0F01E",
        "panel" to "#202020",
    )

private val LightTokens =
    mapOf(
        "tok-key" to "#006E4E",
        "tok-string" to "#975ABA",
        "tok-plain" to "#975ABA",
        "tok-number" to "#94176C",
        "tok-constant" to "#A30034",
        "tok-comment" to "#14141499",
        "tok-anchor" to "#00538F",
        "tok-keyword" to "#A30034",
        "tok-function" to "#BF5400",
        "tok-punctuation" to "#14141499",
        "tok-invalid" to "#D32F2F",
    )

private val DarkTokens =
    mapOf(
        "tok-key" to "#82D2CE",
        "tok-string" to "#E394DC",
        "tok-plain" to "#E394DC",
        "tok-number" to "#EBC88D",
        "tok-constant" to "#F8C762",
        "tok-comment" to "#F0F0F099",
        "tok-anchor" to "#AAA0FA",
        "tok-keyword" to "#82D2CE",
        "tok-function" to "#EFB080",
        "tok-punctuation" to "#A4A4A4",
        "tok-invalid" to "#F14C4C",
    )
