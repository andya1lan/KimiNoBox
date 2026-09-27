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

/** Self-hosted nodes: one `inline` provider named [name] holding [nodes]; empty [prefix] adds none. */
class SelfNodesForm(val name: String, val prefix: String, val nodes: List<MappingNode>)

/**
 * Generates and reads back the self-nodes override:
 * ```yaml
 * # kiminobox:self-nodes v1
 * proxy-providers-merge:
 *   self:
 *     type: inline
 *     override:
 *       additional-prefix: "self "
 *     payload:
 *       - {name: "🇸🇬 SG", type: ss, server: 192.0.2.22, port: 8388, cipher: aes-128-gcm, password: "test"}
 * ```
 */
object SelfNodesTemplate {
    const val DEFAULT_NAME = "self"
    private val bodyKeys = setOf("type", "override", "payload")

    fun render(form: SelfNodesForm): String {
        val body = mutableListOf<Pair<String, Node>>("type" to NodeYaml.plain("inline"))
        if (form.prefix.isNotEmpty()) {
            body += "override" to NodeYaml.map("additional-prefix" to NodeYaml.str(form.prefix))
        }
        body += "payload" to NodeYaml.seq(form.nodes.map { it.deepCopy() })
        val root =
            NodeYaml.map(NodeTemplates.PROVIDERS_KEY to NodeYaml.map(form.name to NodeYaml.map(body)))
        return NodeTemplateKind.SelfNodes.marker + "\n" + NodeYaml.serialize(root)
    }

    /** How many nodes a self-nodes override holds, or null when it is not the template shape. */
    fun nodeCount(content: String): Int? = parse(content)?.nodes?.size

    /** The form of a self-nodes override, or null when it is not the template shape. */
    fun parse(content: String): SelfNodesForm? {
        if (NodeTemplateKind.of(content) != NodeTemplateKind.SelfNodes) return null
        val root = runCatching { NodeYaml.compose(content) }.getOrNull() as? MappingNode ?: return null
        if (root.keys() != listOf(NodeTemplates.PROVIDERS_KEY)) return null
        val providers = root[NodeTemplates.PROVIDERS_KEY] as? MappingNode ?: return null
        val provider = providers.value.singleOrNull() ?: return null
        val name = provider.keyNode.stringValue ?: return null
        val body = provider.valueNode as? MappingNode ?: return null
        if (!body.hasPlainUniqueKeys() || !body.keys().all { it in bodyKeys }) return null
        if (body["type"]?.stringValue != "inline") return null
        val payload = body["payload"] as? SequenceNode ?: return null
        val nodes = payload.value.map { it as? MappingNode ?: return null }
        val prefix =
            when (val override = body["override"]) {
                null -> ""
                is MappingNode ->
                    override.takeIf { it.keys() == listOf("additional-prefix") }
                        ?.get("additional-prefix")
                        ?.stringValue
                        ?: return null

                else -> return null
            }
        return SelfNodesForm(name, prefix, nodes)
    }

    /** Problems of the provider name and prefix; [takenNames] as for node sources. */
    fun problems(name: String, prefix: String, takenNames: Set<String>): NodeSourceProblems =
        NodeSourceProblems(
            name = NodeTemplates.nameProblem(name) ?: NodeText.NAME_TAKEN.takeIf { name in takenNames },
            prefix = NodeText.CONTROL_CHARS.takeIf { prefix.any(Char::isISOControl) },
        )
}
