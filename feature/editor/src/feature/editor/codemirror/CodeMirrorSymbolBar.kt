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

package com.github.yumeyucca.yumebox.feature.editor.codemirror

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.feature.editor.R
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import io.github.yumeyucca.lucide.Lucide
import io.github.yumeyucca.lucide.lucide.ArrowDown
import io.github.yumeyucca.lucide.lucide.ArrowLeft
import io.github.yumeyucca.lucide.lucide.ArrowRight
import io.github.yumeyucca.lucide.lucide.ArrowUp
import io.github.yumeyucca.lucide.lucide.ListIndentDecrease
import io.github.yumeyucca.lucide.lucide.ListIndentIncrease
import io.github.yumeyucca.lucide.lucide.Redo2
import io.github.yumeyucca.lucide.lucide.Search
import io.github.yumeyucca.lucide.lucide.Undo2
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

// What YAML and mihomo rules use most comes first
private val Symbols = "- : , # \" ' [ ] { } | / . * & < > ! = _ @ + ? ~ % \$ ^ ( ) ; \\ `".split(' ')

private val SymbolFont = FontFamily(Font(R.font.jetbrains_mono_regular))

/**
 * KimiNoBox: the row of keys above the keyboard. Indent, the caret arrows and the symbols scroll; search, undo and redo
 * stay at the right. Holding an arrow repeats it.
 */
@Composable
internal fun CodeMirrorSymbolBar(handle: CodeMirrorHandle, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface)) {
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth().height(UiDp.dp44), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = UiDp.dp4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconKey(Lucide.ListIndentIncrease, "缩进", onPress = handle::indentOrCommitTab)
                IconKey(Lucide.ListIndentDecrease, "减少缩进", onPress = handle::outdent)
                IconKey(Lucide.ArrowLeft, "光标左移", repeat = true) { handle.moveCaret(CaretMove.Left) }
                IconKey(Lucide.ArrowRight, "光标右移", repeat = true) { handle.moveCaret(CaretMove.Right) }
                Symbols.forEach { symbol -> TextKey(symbol) { handle.type(symbol) } }
                IconKey(Lucide.ArrowUp, "光标上移", repeat = true) { handle.moveCaret(CaretMove.Up) }
                IconKey(Lucide.ArrowDown, "光标下移", repeat = true) { handle.moveCaret(CaretMove.Down) }
            }
            VerticalDivider(modifier = Modifier.height(UiDp.dp24))
            IconKey(Lucide.Search, "查找", onPress = handle::toggleSearch)
            IconKey(Lucide.Undo2, "撤销", enabled = handle.canUndo(), onPress = handle::undo)
            IconKey(Lucide.Redo2, "重做", enabled = handle.canRedo(), onPress = handle::redo)
        }
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    Key(description = description, enabled = enabled, repeat = repeat, onPress = onPress) {
        Icon(
            icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(UiDp.dp20),
        )
    }
}

@Composable
private fun TextKey(symbol: String, onPress: () -> Unit) {
    Key(description = symbol, onPress = onPress) {
        Text(
            text = symbol,
            style = MiuixTheme.textStyles.body1,
            fontFamily = SymbolFont,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One key. It never takes the focus, so the keyboard stays up. A tap types once; a held [repeat] key types again every
 * [KEY_REPEAT_MILLIS] after the long press timeout. Dragging along the row scrolls it instead.
 */
@Composable
private fun Key(
    description: String,
    enabled: Boolean = true,
    repeat: Boolean = false,
    onPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val pressedColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Box(
        modifier =
            Modifier.size(width = KeyWidth, height = UiDp.dp40)
                .clip(RoundedCornerShape(UiDp.dp10))
                .background(if (pressed) pressedColor else Color.Transparent)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .pointerInput(enabled, repeat) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            var repeater: Job? = null
                            if (repeat) {
                                repeater = scope.launch {
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    while (true) {
                                        currentOnPress()
                                        delay(KEY_REPEAT_MILLIS)
                                    }
                                }
                            }
                            tryAwaitRelease()
                            repeater?.cancel()
                            pressed = false
                        },
                        // A held repeat key has typed already; letting go must not type once more
                        onLongPress = if (repeat) ({}) else null,
                        onTap = { currentOnPress() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private val KeyWidth = 38.dp
private const val KEY_REPEAT_MILLIS = 60L
private const val DISABLED_ALPHA = 0.35f
