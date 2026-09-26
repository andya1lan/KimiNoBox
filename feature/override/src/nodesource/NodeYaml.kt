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

import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.api.lowlevel.Present
import org.snakeyaml.engine.v2.api.lowlevel.Serialize
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.CoreSchema

/**
 * YAML at the representation-node level (SnakeYAML Engine, YAML 1.2 core schema). Composing never
 * instantiates objects, and serializing a composed tree keeps key order and each scalar's text and
 * quoting, so pasted values such as a numeric-looking `short-id` are not re-typed on the way
 * through. Emoji are fine here; the SnakeYAML 1.18 behind upstream's YamlCodec rejects them.
 */
internal object NodeYaml {
    /** Far above any override; the engine's default is 3 MB. */
    private const val CODE_POINT_LIMIT = 16 * 1024 * 1024

    /** Aliases stay shared nodes while composing (nothing is expanded), so they are cheap. */
    private const val MAX_ALIASES = 1_000

    private val schema = CoreSchema()

    private val loadSettings =
        LoadSettings.builder()
            .setSchema(schema)
            .setCodePointLimit(CODE_POINT_LIMIT)
            .setMaxAliasesForCollections(MAX_ALIASES)
            .build()

    private fun dumpSettings(indentSequences: Boolean) =
        DumpSettings.builder()
            .setSchema(schema)
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .apply {
                // `  - item` under a key, as hand-written configs do.
                if (indentSequences) setIndicatorIndent(2).setIndentWithIndicator(true)
            }
            .setWidth(4096)
            .setSplitLines(false)
            .setUseUnicodeEncoding(true)
            .build()

    private val nestedDumpSettings = dumpSettings(indentSequences = true)
    private val flatDumpSettings = dumpSettings(indentSequences = false)

    /** The single document of [text]; null when it is empty. Throws on invalid YAML. */
    fun compose(text: String): Node? = Compose(loadSettings).composeString(text).orElse(null)

    private val inspectSettings =
        LoadSettings.builder()
            .setSchema(schema)
            .setCodePointLimit(CODE_POINT_LIMIT)
            .setMaxAliasesForCollections(MAX_ALIASES)
            .setUseMarks(false)
            .build()

    /** [compose] without source positions, for reading large subscriptions; errors lose their line. */
    fun composeForInspection(text: String): Node? = Compose(inspectSettings).composeString(text).orElse(null)

    /** [indentSequences] false starts a top-level list at column 0 (the paste editor). */
    fun serialize(root: Node, indentSequences: Boolean = true): String {
        val settings = if (indentSequences) nestedDumpSettings else flatDumpSettings
        return Present(settings).emitToString(Serialize(settings).serializeOne(root).iterator())
    }

    /** One-line message of a YAML failure, keeping its line and column. */
    fun describe(error: Throwable): String =
        (error as? YamlEngineException)
            ?.message
            ?.lines()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.joinToString(" ")
            ?: error.message
            ?: error.javaClass.simpleName

    /** Plain when YAML allows it; the emitter quotes names like `123` or `a: b` by itself. */
    fun key(name: String): ScalarNode = ScalarNode(Tag.STR, name, ScalarStyle.PLAIN)

    fun plain(value: String): ScalarNode = ScalarNode(Tag.STR, value, ScalarStyle.PLAIN)

    fun str(value: String): ScalarNode = ScalarNode(Tag.STR, value, ScalarStyle.DOUBLE_QUOTED)

    fun int(value: Long): ScalarNode = ScalarNode(Tag.INT, value.toString(), ScalarStyle.PLAIN)

    fun bool(value: Boolean): ScalarNode =
        ScalarNode(Tag.BOOL, value.toString(), ScalarStyle.PLAIN)

    fun map(entries: List<Pair<String, Node>>, flow: Boolean = false): MappingNode =
        MappingNode(
            Tag.MAP,
            entries.map { (name, value) -> NodeTuple(key(name), value) }.toMutableList(),
            if (flow) FlowStyle.FLOW else FlowStyle.BLOCK,
        )

    fun map(vararg entries: Pair<String, Node>): MappingNode = map(entries.toList())

    fun seq(items: List<Node>): SequenceNode =
        SequenceNode(Tag.SEQ, items.toMutableList(), FlowStyle.BLOCK)
}

internal fun MappingNode.keys(): List<String?> = value.map { (it.keyNode as? ScalarNode)?.value }

internal operator fun MappingNode.get(name: String): Node? =
    value.firstOrNull { (it.keyNode as? ScalarNode)?.value == name }?.valueNode

/** Replaces the value of [name] in place, or appends it. */
internal fun MappingNode.put(name: String, node: Node) {
    val index = value.indexOfFirst { (it.keyNode as? ScalarNode)?.value == name }
    val tuple = NodeTuple(NodeYaml.key(name), node)
    if (index >= 0) value[index] = tuple else value.add(tuple)
}

internal fun MappingNode.remove(name: String) {
    value.removeAll { (it.keyNode as? ScalarNode)?.value == name }
}

/** A tree that can be edited without touching [this] (aliases become separate copies). */
internal fun Node.deepCopy(): Node =
    when (this) {
        is MappingNode ->
            MappingNode(
                tag,
                value.map { NodeTuple(it.keyNode.deepCopy(), it.valueNode.deepCopy()) }.toMutableList(),
                flowStyle,
            )

        is SequenceNode -> SequenceNode(tag, value.map { it.deepCopy() }.toMutableList(), flowStyle)
        is ScalarNode -> ScalarNode(tag, value, scalarStyle)
        else -> this
    }

/** Every key is a scalar and none repeats (the core rejects duplicates). */
internal fun MappingNode.hasPlainUniqueKeys(): Boolean {
    val keys = keys()
    return keys.none { it == null } && keys.size == keys.toSet().size
}

/** The text of a scalar, whatever its resolved type. */
internal val Node.scalarText: String?
    get() = (this as? ScalarNode)?.value

/** The text of a string scalar: quoted, or plain that resolves to a string. */
internal val Node.stringValue: String?
    get() = (this as? ScalarNode)?.takeIf { it.tag == Tag.STR }?.value

/** A plain integer scalar. */
internal val Node.longValue: Long?
    get() = (this as? ScalarNode)?.takeIf { it.tag == Tag.INT }?.value?.toLongOrNull()
