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
    /** 「定期测速」: the URL the core tests every node with, or null for no checks. */
    val healthCheckUrl: String? = null,
)

/** What the user typed, before it becomes a [NodeSourceForm]. */
data class NodeSourceInput(
    val name: String = "",
    val url: String = "",
    val interval: String = NodeSourceTemplate.DEFAULT_INTERVAL_SECONDS.toString(),
    /** Added before every node name; empty (the default) adds none. */
    val prefix: String = "",
    /** 「定期测速」, on for a new source. */
    val healthCheck: Boolean = true,
)

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
 *     health-check:
 *       enable: true
 *       url: "https://www.gstatic.com/generate_204"
 *       interval: 300
 *       lazy: true
 * ```
 * Profile groups pick the nodes up with `include-all: true`; `use:` would fail the first import's
 * `--test`, which runs before any override adds the provider.
 */
object NodeSourceTemplate {
    /** A new source updates hourly; the subscription's `Profile-Update-Interval` is not read. */
    const val DEFAULT_INTERVAL_SECONDS = 3_600L

    /**
     * 「定期测速」 tests every node this often, and only while connections go through the source's
     * groups (`lazy`), so an idle phone tests nothing.
     */
    const val HEALTH_CHECK_INTERVAL_SECONDS = 300L

    private const val PATH_ID_LENGTH = 8
    private const val PATH_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val pathPattern = Regex("""\./providers/src-([a-z0-9]{$PATH_ID_LENGTH})\.yaml""")
    private val urlPattern = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
    private val bodyKeys = setOf("type", "url", "interval", "path", "override", "health-check")
    private val healthCheckKeys = listOf("enable", "url", "interval", "lazy")

    /** The prefix that marks every node of [name] with the source name, for the duplicate fix. */
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
        form.healthCheckUrl?.let { testUrl ->
            body +=
                "health-check" to
                    NodeYaml.map(
                        "enable" to NodeYaml.bool(true),
                        "url" to NodeYaml.str(testUrl),
                        "interval" to NodeYaml.int(HEALTH_CHECK_INTERVAL_SECONDS),
                        "lazy" to NodeYaml.bool(true),
                    )
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
        val healthCheckUrl =
            when (val check = body["health-check"]) {
                null -> null
                is MappingNode ->
                    check.takeIf {
                        it.keys() == healthCheckKeys &&
                            it["enable"]?.scalarText == "true" &&
                            it["interval"]?.longValue == HEALTH_CHECK_INTERVAL_SECONDS &&
                            it["lazy"]?.scalarText == "true"
                    }
                        ?.get("url")
                        ?.stringValue
                        ?: return null

                else -> return null
            }
        return NodeSourceForm(name, url, interval, prefix, pathId, healthCheckUrl)
    }

    fun inputOf(form: NodeSourceForm): NodeSourceInput =
        NodeSourceInput(
            name = form.name,
            url = form.url,
            interval = form.intervalSeconds.toString(),
            prefix = form.prefix,
            healthCheck = form.healthCheckUrl != null,
        )

    /** [takenNames]: provider names of the other node sources. */
    fun validate(input: NodeSourceInput, takenNames: Set<String>): NodeSourceProblems {
        val interval = input.interval.trim()
        return NodeSourceProblems(
            name = nameProblem(input.name, takenNames),
            url = NodeText.URL_INVALID.takeUnless { urlPattern.matches(input.url.trim()) },
            interval =
                NodeText.INTERVAL_INVALID.takeUnless {
                    interval.all(Char::isDigit) &&
                        (interval.toLongOrNull() ?: -1L) in 0L..Int.MAX_VALUE.toLong()
                },
            prefix = NodeText.CONTROL_CHARS.takeIf { input.prefix.any(Char::isISOControl) },
        )
    }

    /** Problem with [name] as a node source name, given the names [takenNames] of the others. */
    fun nameProblem(name: String, takenNames: Set<String>): String? =
        NodeTemplates.nameProblem(name) ?: NodeText.NAME_TAKEN.takeIf { name in takenNames }

    /**
     * The form to save for a valid [input]. [original] is the saved form when editing: a changed
     * URL gets a new cache path, since the core keeps serving a fresh cache file of the old URL
     * until its interval ends. [testUrl] is the App's 「延迟测试地址」, which 「定期测速」 uses as
     * it is at the save.
     */
    fun formFor(
        input: NodeSourceInput,
        original: NodeSourceForm?,
        testUrl: String,
        generatePathId: () -> String = { newPathId() },
    ): NodeSourceForm {
        val url = input.url.trim()
        return NodeSourceForm(
            name = input.name,
            url = url,
            intervalSeconds = input.interval.trim().toLong(),
            prefix = input.prefix,
            pathId = original?.takeIf { it.url == url }?.pathId ?: generatePathId(),
            healthCheckUrl = testUrl.takeIf { input.healthCheck },
        )
    }
}
