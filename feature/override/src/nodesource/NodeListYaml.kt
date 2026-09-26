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

import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.SequenceNode

/** The node list of the self-nodes page, as YAML. */
object NodeListYaml {
    private val requiredKeys = listOf("name", "type", "server", "port")

    /**
     * Nodes of pasted YAML in any of three shapes: a node list, a mapping with `proxies:` (for
     * example a whole config), or a single node mapping. The failure message can be shown as is.
     */
    fun parse(text: String): Result<List<MappingNode>> {
        val root =
            try {
                NodeYaml.compose(text)
            } catch (error: Exception) {
                return Result.failure(IllegalArgumentException(NodeText.yamlInvalid(NodeYaml.describe(error))))
            }
        val items: List<Node> =
            when (root) {
                null -> emptyList()
                is SequenceNode -> root.value
                is MappingNode ->
                    when (val proxies = root["proxies"]) {
                        is SequenceNode -> proxies.value
                        null -> if (root["type"] != null) listOf(root) else return shapeFailure()
                        else -> return shapeFailure()
                    }

                else -> return shapeFailure()
            }
        val nodes =
            items.mapIndexed { index, item ->
                item as? MappingNode
                    ?: return Result.failure(IllegalArgumentException(NodeText.notANode(index + 1)))
            }
        return Result.success(nodes)
    }

    /** Problems of a node list, one line each; empty when it can be saved. */
    fun problems(nodes: List<MappingNode>): List<String> {
        if (nodes.isEmpty()) return listOf(NodeText.NO_NODES)
        val problems = mutableListOf<String>()
        nodes.forEachIndexed { index, node ->
            val name = node["name"]?.scalarText?.takeIf(String::isNotBlank)
            val label = name ?: NodeText.nthNode(index + 1)
            val missing = requiredKeys.filter { node[it]?.scalarText.isNullOrBlank() }
            if (missing.isNotEmpty()) problems += NodeText.missingKeys(label, missing)
            node["port"]?.scalarText?.takeIf { (it.toIntOrNull() ?: 0) !in 1..65535 }?.let {
                problems += "$label：${NodeText.PORT_INVALID}"
            }
            name?.let(NodeTemplates::nameProblem)?.let { problems += "$label：$it" }
        }
        nodes
            .mapNotNull { it["name"]?.scalarText }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { problems += NodeText.duplicateName(it) }
        return problems
    }

    /** The list as the self-nodes editor shows it. */
    fun render(nodes: List<MappingNode>): String =
        if (nodes.isEmpty()) "" else NodeYaml.serialize(NodeYaml.seq(nodes.map { it.deepCopy() }), indentSequences = false)

    private fun shapeFailure(): Result<List<MappingNode>> =
        Result.failure(IllegalArgumentException(NodeText.UNKNOWN_SHAPE))
}
