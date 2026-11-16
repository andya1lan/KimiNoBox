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
package com.github.yumeyucca.yumebox.common.util

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// KimiNoBox: upstream's copyable message dialog, which it removed. Non-UI code (a refused reload,
// a GeoX update, a provider update) shows a whole reason through it; ToastDialogHost renders it.

data class ToastDialogEvent(
    val id: Long,
    val title: String,
    val message: String,
)

object ToastDialogBridge {
    private const val DEFAULT_TITLE = "提示"

    private val nextId = AtomicLong(1L)
    private val queue = ArrayDeque<ToastDialogEvent>()
    private val lock = Any()
    private val _event = MutableStateFlow<ToastDialogEvent?>(null)
    val event: StateFlow<ToastDialogEvent?> = _event.asStateFlow()

    fun show(message: String, title: String = DEFAULT_TITLE) {
        if (message.isBlank()) return
        val event = ToastDialogEvent(id = nextId.getAndIncrement(), title = title, message = message)
        synchronized(lock) {
            if (_event.value == null) {
                _event.value = event
            } else {
                queue.addLast(event)
            }
        }
    }

    fun dismiss(eventId: Long) {
        synchronized(lock) {
            if (_event.value?.id == eventId) {
                _event.value = if (queue.isEmpty()) null else queue.removeFirst()
            } else {
                queue.removeAll { it.id == eventId }
            }
        }
    }
}

fun showToastDialog(message: String, title: String = "提示") {
    ToastDialogBridge.show(message = message, title = title)
}
