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
import kotlin.random.Random

/** A subscription node source: one `http` provider named [name]. */
data class NodeSourceForm(
    val name: String,
    val url: String,
    val intervalSeconds: Long,
    /** Added before every node name; empty for none. */
    val prefix: String,
    /** The `<id>` of `./providers/src-<id>.yaml`, the core's cache file of this source. */
    val pathId: String,
)

/** What the user typed, before it becomes a [NodeSourceForm]. */
data class NodeSourceInput(
    val name: String = "",
    val url: String = "",
    val interval: String = NodeSourceTemplate.DEFAULT_INTERVAL_SECONDS.toString(),
    /** Empty follows the name ("name + space"), unless [noPrefix]. */
    val prefix: String = "",
    val noPrefix: Boolean = false,
) {
    val effectivePrefix: String
        get() = if (noPrefix) "" else prefix.ifEmpty { NodeSourceTemplate.defaultPrefix(name) }
}

data class NodeSourceProblems(
    val name: String? = null,
    val url: String? = null,
    val interval: String? = null,
    val prefix: String? = null,
) {
    val isEmpty: Boolean
        get() = name == null && url == null && interval == null && prefix == null
}

/**
 * Generates and reads back the node-source override:
 * ```yaml
 * # kiminobox:node-source v1
 * proxy-providers-merge:
 *   air:
 *     type: http
 *     url: "https://example.com/sub?token=xxx"
 *     interval: 86400
 *     path: ./providers/src-k3n8x2qa.yaml
 *     override:
 *       additional-prefix: "air "
 * ```
 * Profile groups pick the nodes up with `include-all: true`; `use:` would fail the first import's
 * `--test`, which runs before any override adds the provider.
 */
object NodeSourceTemplate {
    const val DEFAULT_INTERVAL_SECONDS = 86_400L
    private const val PATH_ID_LENGTH = 8
    private const val PATH_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val pathPattern = Regex("""\./providers/src-([a-z0-9]{$PATH_ID_LENGTH})\.yaml""")
    private val urlPattern = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
    private val bodyKeys = setOf("type", "url", "interval", "path", "override")

    fun defaultPrefix(name: String): String = "$name "

    fun newPathId(random: Random = Random.Default): String =
        (1..PATH_ID_LENGTH).map { PATH_ID_CHARS[random.nextInt(PATH_ID_CHARS.length)] }
            .joinToString("")

    fun render(form: NodeSourceForm): String {
        val body =
            mutableListOf<Pair<String, Node>>(
                "type" to NodeYaml.plain("http"),
                "url" to NodeYaml.str(form.url),
                "interval" to NodeYaml.int(form.intervalSeconds),
                "path" to NodeYaml.plain("./providers/src-${form.pathId}.yaml"),
            )
        if (form.prefix.isNotEmpty()) {
            body += "override" to NodeYaml.map("additional-prefix" to NodeYaml.str(form.prefix))
        }
        val root =
            NodeYaml.map(NodeTemplates.PROVIDERS_KEY to NodeYaml.map(form.name to NodeYaml.map(body)))
        return NodeTemplateKind.NodeSource.marker + "\n" + NodeYaml.serialize(root)
    }

    /**
     * The form of a node-source override, or null when the content is not exactly the template
     * shape (edited by hand): such an override opens in the plain YAML editor instead.
     */
    fun parse(content: String): NodeSourceForm? {
        if (NodeTemplateKind.of(content) != NodeTemplateKind.NodeSource) return null
        val root = runCatching { NodeYaml.compose(content) }.getOrNull() as? MappingNode ?: return null
        if (root.keys() != listOf(NodeTemplates.PROVIDERS_KEY)) return null
        val providers = root[NodeTemplates.PROVIDERS_KEY] as? MappingNode ?: return null
        val provider = providers.value.singleOrNull() ?: return null
        val name = provider.keyNode.stringValue ?: return null
        val body = provider.valueNode as? MappingNode ?: return null
        if (!body.hasPlainUniqueKeys() || !body.keys().all { it in bodyKeys }) return null
        if (body["type"]?.stringValue != "http") return null
        val url = body["url"]?.stringValue ?: return null
        val interval = body["interval"]?.longValue ?: return null
        val pathId =
            body["path"]?.stringValue?.let(pathPattern::matchEntire)?.groupValues?.get(1)
                ?: return null
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
        return NodeSourceForm(name, url, interval, prefix, pathId)
    }

    /** A saved prefix equal to the default keeps following the name. */
    fun inputOf(form: NodeSourceForm): NodeSourceInput =
        NodeSourceInput(
            name = form.name,
            url = form.url,
            interval = form.intervalSeconds.toString(),
            prefix = form.prefix.takeUnless { it == defaultPrefix(form.name) }.orEmpty(),
            noPrefix = form.prefix.isEmpty(),
        )

    /** [takenNames]: provider names of the other node sources. */
    fun validate(input: NodeSourceInput, takenNames: Set<String>): NodeSourceProblems {
        val interval = input.interval.trim()
        return NodeSourceProblems(
            name =
                NodeTemplates.nameProblem(input.name)
                    ?: NodeText.NAME_TAKEN.takeIf { input.name in takenNames },
            url = NodeText.URL_INVALID.takeUnless { urlPattern.matches(input.url.trim()) },
            interval =
                NodeText.INTERVAL_INVALID.takeUnless {
                    interval.all(Char::isDigit) &&
                        (interval.toLongOrNull() ?: -1L) in 0L..Int.MAX_VALUE.toLong()
                },
            prefix = NodeText.CONTROL_CHARS.takeIf { input.effectivePrefix.any(Char::isISOControl) },
        )
    }

    /**
     * The form to save for a valid [input]. [original] is the saved form when editing: a changed
     * URL gets a new cache path, since the core keeps serving a fresh cache file of the old URL
     * until its interval ends.
     */
    fun formFor(
        input: NodeSourceInput,
        original: NodeSourceForm?,
        generatePathId: () -> String = { newPathId() },
    ): NodeSourceForm {
        val url = input.url.trim()
        return NodeSourceForm(
            name = input.name,
            url = url,
            intervalSeconds = input.interval.trim().toLong(),
            prefix = input.effectivePrefix,
            pathId = original?.takeIf { it.url == url }?.pathId ?: generatePathId(),
        )
    }
}
