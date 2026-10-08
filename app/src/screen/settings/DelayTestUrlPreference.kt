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

package com.github.yumeyucca.yumebox.screen.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumeyucca.yumebox.data.model.DelayTestUrl
import com.github.yumeyucca.yumebox.data.store.AppSettingsStore
import com.github.yumeyucca.yumebox.presentation.component.AppFormDialog
import com.github.yumeyucca.yumebox.presentation.component.OemTextField
import com.github.yumeyucca.yumebox.presentation.component.PreferenceEnumItem
import org.koin.compose.koinInject

/**
 * KimiNoBox: 「延迟测试地址」 in 应用设置 → 网络. A URL from the list applies at once. 「自定义」
 * opens a field for any `http` or `https` URL, also when it is already the selected item.
 */
@Composable
internal fun DelayTestUrlPreferenceItem() {
    val setting = koinInject<AppSettingsStore>().delayTestUrl
    val url by setting.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    PreferenceEnumItem(
        title = TITLE,
        summary = url,
        currentValue = DelayTestUrl.presetOf(url)?.url,
        items = DelayTestUrl.presets.map { it.label } + CUSTOM,
        // null is 「自定义」
        values = DelayTestUrl.presets.map { it.url } + null,
        onValueChange = { picked -> if (picked == null) editing = true else setting.set(picked) },
    )

    CustomDelayTestUrlDialog(
        show = editing,
        current = url,
        onDismiss = { editing = false },
        onSave = { custom ->
            setting.set(custom)
            editing = false
        },
    )
}

@Composable
private fun CustomDelayTestUrlDialog(
    show: Boolean,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue()) }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            field = TextFieldValue(current, TextRange(current.length))
            invalid = false
        }
    }

    val save: () -> Unit = {
        val url = DelayTestUrl.normalize(field.text)
        if (url == null) {
            invalid = true
        } else {
            onSave(url)
        }
    }

    AppFormDialog(
        show = show,
        title = CUSTOM_TITLE,
        summary = CUSTOM_SUMMARY,
        onDismissRequest = onDismiss,
        onConfirm = save,
        error = INVALID.takeIf { invalid },
        confirmEnabled = field.text.isNotBlank(),
    ) {
        OemTextField(
            value = field,
            onValueChange = {
                field = it
                invalid = false
            },
            label = PLACEHOLDER,
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onImeAction = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                save()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val TITLE = "延迟测试地址"
private const val CUSTOM = "自定义"
private const val CUSTOM_TITLE = "自定义延迟测试地址"
private const val CUSTOM_SUMMARY = "测试时，内核经节点请求这个地址，收到响应的用时就是延迟。建议用 https 地址。"
private const val PLACEHOLDER = "https://"
private const val INVALID = "请输入 http:// 或 https:// 开头的完整地址"
