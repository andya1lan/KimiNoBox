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

package com.github.yumeyucca.yumebox.screen.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.feature.editor.editor.CodeEditor
import com.github.yumeyucca.yumebox.feature.editor.editor.rememberConfiguredCodeEditorState
import com.github.yumeyucca.yumebox.feature.editor.language.LanguageScope
import com.github.yumeyucca.yumebox.presentation.component.SmallTopBar
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import com.github.yumeyucca.yumebox.runtime.service.session.ProfileConfigViews
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

private enum class ConfigView(val label: String, val hint: String) {
    Raw("原始配置", "导入或更新时保存的 config.yaml，还没有经过覆写"),
    Final("最终配置", "按 VPN 启动时的覆写链和运行时补丁编译，即内核收到的配置"),
}

/** KimiNoBox: 「查看配置」 of one profile, both views read-only and copyable. */
@Composable
fun ProfileConfigViewScreen(profileUuid: String, title: String) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val scrollBehavior = MiuixScrollBehavior()
    var view by rememberSaveable { mutableStateOf(ConfigView.Raw) }
    var views by remember(profileUuid) { mutableStateOf<ProfileConfigViews.Views?>(null) }
    LaunchedEffect(profileUuid) {
        views = ProfileConfigViews(context).load(UUID.fromString(profileUuid))
    }
    val content =
        when (view) {
            ConfigView.Raw -> views?.raw
            ConfigView.Final -> views?.final
        }

    Scaffold(topBar = { SmallTopBar(title = title, scrollBehavior = scrollBehavior) }) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfigView.entries.forEach { entry ->
                    TextButton(
                        text = entry.label,
                        onClick = { view = entry },
                        colors =
                            if (entry == view) ButtonDefaults.textButtonColorsPrimary()
                            else ButtonDefaults.textButtonColors(),
                    )
                }
                Spacer(Modifier.weight(1f))
                val text = content?.getOrNull()
                TextButton(
                    text = COPY,
                    onClick = { copyToClipboard(context, text) },
                    enabled = text != null,
                )
            }
            Text(
                text = view.hint,
                modifier =
                    Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.space8),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            when {
                content == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InfiniteProgressIndicator(modifier = Modifier.size(UiDp.dp32))
                    }

                content.isFailure ->
                    Text(
                        text = content.exceptionOrNull()?.message ?: LOAD_FAILED,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )

                else ->
                    key(view) {
                        val state =
                            rememberConfiguredCodeEditorState(
                                initialContent = content.getOrThrow(),
                                language = LanguageScope.Yaml,
                                readOnly = true,
                            )
                        CodeEditor(state = state, modifier = Modifier.fillMaxSize())
                    }
            }
        }
    }
}

/** The clipboard travels through a binder transaction; very large configs do not fit. */
private fun copyToClipboard(context: Context, text: String?) {
    text ?: return
    if (text.length > MAX_CLIP_CHARS) {
        context.toast("内容过大（约 ${text.toByteArray().size / 1024} KB），无法放入剪贴板")
        return
    }
    runCatching {
            context
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(VIEW_CONFIG, text))
        }
        .onSuccess { context.toast("已复制") }
        .onFailure { context.toast("复制失败") }
}

internal const val VIEW_CONFIG = "查看配置"
private const val COPY = "复制"
private const val LOAD_FAILED = "读取失败"
private const val MAX_CLIP_CHARS = 400_000
