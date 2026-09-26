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

/**
 * The override templates behind 「节点源」: plain YAML overrides that add one proxy provider
 * through `proxy-providers-merge`, so several of them (and the built-in overrides) stack on one
 * profile. The first line marks which form generated the override.
 */
enum class NodeTemplateKind(val marker: String) {
    /** A subscription downloaded by the core (an `http` provider). */
    NodeSource("# kiminobox:node-source v1"),

    /** Nodes written into the override itself (an `inline` provider). */
    SelfNodes("# kiminobox:self-nodes v1"),
    ;

    companion object {
        /** The kind whose marker is the first line of [content], if any. */
        fun of(content: String): NodeTemplateKind? {
            val firstLine = content.removePrefix("\uFEFF").lineSequence().firstOrNull()?.trimEnd()
            return entries.firstOrNull { it.marker == firstLine }
        }
    }
}

object NodeTemplates {
    /** The merge key every template writes to; plain `proxy-providers` would replace, not add. */
    const val PROVIDERS_KEY = "proxy-providers-merge"

    /**
     * Provider names an override defines, read loosely so a hand-edited template still counts.
     * Empty for JavaScript, invalid YAML or no provider keys.
     */
    fun providerNames(content: String): Set<String> {
        val root = runCatching { NodeYaml.compose(content) }.getOrNull() as? MappingNode
            ?: return emptySet()
        return root.value
            .filter { tuple -> tuple.keyNode.scalarText?.contains("proxy-providers") == true }
            .flatMap { tuple -> (tuple.valueNode as? MappingNode)?.keys().orEmpty() }
            .filterNotNull()
            .toSet()
    }

    /** Provider names already used by the node source overrides whose [contents] are given. */
    fun takenNames(contents: List<String>): Set<String> = contents.flatMap(::providerNames).toSet()

    /**
     * Problem with a provider or node name, or null. Control characters would break the YAML
     * line layout the core and the list UI rely on.
     */
    internal fun nameProblem(name: String): String? =
        when {
            name.isBlank() -> NodeText.NAME_REQUIRED
            name != name.trim() -> NodeText.NAME_SPACES
            name.any(Char::isISOControl) -> NodeText.CONTROL_CHARS
            else -> null
        }
}
