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

package com.github.yumeyucca.yumebox.presentation.screen

import com.github.yumeyucca.yumebox.core.model.Proxy
import kotlinx.coroutines.flow.Flow

/**
 * KimiNoBox: the provider names of a profile's node sources in its chain order, which the user sets
 * in the node source sheet (docs/plan-a-round3.md D5). The app provides it.
 */
fun interface NodeSourceProviderOrder {
    fun of(profileId: String): Flow<List<String>>
}

/**
 * KimiNoBox: a group's members in sections by the node source they come from, like zashboard's
 * "group by provider" (docs/plan-a-round2.md C6). A name found in several sources belongs to the
 * first one, as the core picks it; nodes of the config itself form their own section, and
 * sub-groups and the built-in DIRECT / REJECT lead the list.
 */
internal object NodeSourceSections {
    const val OWN_NODES = "配置内节点"
    const val GROUPS = "策略组与内置"

    data class Section(val title: String, val proxies: List<Proxy>)

    private val builtIns =
        setOf(Proxy.Type.Direct, Proxy.Type.Reject, Proxy.Type.RejectDrop, Proxy.Type.Compatible, Proxy.Type.Pass, Proxy.Type.PassRule)

    /**
     * [sourceNodes]: node names of each node source, in the core's order, which decides a name
     * found in several. The sections follow [order] (the profile's chain order), sources it misses
     * after them. Null when the members come from fewer than two sources, so the list is shown as
     * it was.
     */
    fun of(members: List<Proxy>, sourceNodes: Map<String, List<String>>, order: List<String> = emptyList()): List<Section>? {
        val sourceOf = HashMap<String, String>()
        sourceNodes.forEach { (source, names) -> names.forEach { sourceOf.putIfAbsent(it, source) } }
        val bySource = LinkedHashMap<String, MutableList<Proxy>>()
        sourceNodes.keys.forEach { bySource[it] = mutableListOf() }
        val own = mutableListOf<Proxy>()
        val groups = mutableListOf<Proxy>()
        members.forEach { proxy ->
            val source = sourceOf[proxy.name]
            when {
                source != null -> bySource.getValue(source) += proxy
                proxy.isGroup || proxy.type in builtIns -> groups += proxy
                else -> own += proxy
            }
        }
        val rank = order.withIndex().associate { (index, name) -> name to index }
        val sources =
            bySource.filterValues { it.isNotEmpty() }
                .map { (title, proxies) -> Section(title, proxies) }
                .sortedBy { rank[it.title] ?: Int.MAX_VALUE } +
                listOfNotNull(Section(OWN_NODES, own).takeIf { own.isNotEmpty() })
        if (sources.size < 2) return null
        return listOfNotNull(Section(GROUPS, groups).takeIf { groups.isNotEmpty() }) + sources
    }

    /** Where the first card named [name] is among the section headers and cards, or null. */
    fun position(sections: List<Section>, name: String): Int? {
        var position = 0
        sections.forEach { section ->
            position += 1
            val index = section.proxies.indexOfFirst { it.name == name }
            if (index >= 0) return position + index
            position += section.proxies.size
        }
        return null
    }
}
