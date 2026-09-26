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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.github.yumeyucca.yumebox.nodesource.NodeText
import com.github.yumeyucca.yumebox.presentation.component.OemTextField
import com.github.yumeyucca.yumebox.presentation.component.OverrideFieldAssistText
import com.github.yumeyucca.yumebox.presentation.component.SmallTopBar
import com.github.yumeyucca.yumebox.presentation.icon.Yume
import com.github.yumeyucca.yumebox.presentation.icon.yume.Save
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: page frame of the node source forms, drawn in place of the override list. Back
 * discards the form; the save icon calls [onSave]. A page with a code editor passes
 * [scrollable] false and gives the editor the remaining height.
 */
@Composable
internal fun NodeEditorScaffold(
    title: String,
    saving: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    Scaffold(
        topBar = {
            SmallTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        onClick = onSave,
                        enabled = !saving,
                    ) {
                        Icon(Yume.Save, contentDescription = NodeText.SAVE)
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
            content = content,
        )
    }
}

/** One labelled text field with its support line and, once shown, its error. */
@Composable
internal fun NodeFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    support: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OemTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        support?.let { OverrideFieldAssistText(it, MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        error?.let { OverrideFieldAssistText(it, MiuixTheme.colorScheme.error) }
    }
}

@Composable
internal fun NodeHint(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        style = MiuixTheme.textStyles.body2,
        color =
            if (isError) MiuixTheme.colorScheme.error
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
