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

package com.github.yumeyucca.yumebox.nodesource

import android.content.Context
import com.github.yumeyucca.yumebox.core.util.NodeSourceCopies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/** KimiNoBox: the [NodeSourceState] of every node source, one small JSON file per override id. */
class NodeSourceStateStore(context: Context) {
    private val dir = NodeSourceCopies.directory(context).resolve("state")
    private val json = Json { ignoreUnknownKeys = true }
    private val _states = MutableStateFlow(load())
    val states: StateFlow<Map<String, NodeSourceState>> = _states.asStateFlow()

    fun get(id: String): NodeSourceState = _states.value[id] ?: NodeSourceState()

    @Synchronized
    fun update(id: String, transform: (NodeSourceState) -> NodeSourceState) {
        val current = get(id)
        val next = transform(current)
        if (next == current) return
        runCatching {
                dir.mkdirs()
                NodeSourceCopies.write(json.encodeToString(NodeSourceState.serializer(), next).toByteArray(), file(id))
            }
            .onFailure { Timber.w(it, "Node source state %s not saved", id) }
        _states.value = _states.value + (id to next)
    }

    @Synchronized
    fun remove(id: String) {
        file(id).delete()
        _states.value = _states.value - id
    }

    private fun file(id: String): File = dir.resolve("$id.json")

    private fun load(): Map<String, NodeSourceState> =
        dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { file.nameWithoutExtension to json.decodeFromString(NodeSourceState.serializer(), file.readText()) }
                    .getOrNull()
            }
            .toMap()
}
