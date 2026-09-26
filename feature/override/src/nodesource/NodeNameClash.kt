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

/**
 * Node names shared by two sources. The core finds a node by its name and takes the first one,
 * so a group can end up on the node of the other source; a prefix on one of them fixes it.
 */
object NodeNameClash {
    /** [count] names of a source are also in [other], the source sharing the most names with it. */
    data class Clash(val other: String, val count: Int)

    /** For every source that shares names with another one; [names] per source, as the core lists them. */
    fun find(names: Map<String, List<String>>): Map<String, Clash> {
        val sets = names.mapValues { (_, list) -> list.toSet() }
        return sets.mapNotNull { (source, own) ->
                sets.filterKeys { it != source }
                    .map { (other, theirs) -> Clash(other, own.count(theirs::contains)) }
                    .filter { it.count > 0 }
                    .maxByOrNull { it.count }
                    ?.let { source to it }
            }
            .toMap()
    }
}
