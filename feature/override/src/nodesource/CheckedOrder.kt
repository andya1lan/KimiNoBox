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
 * KimiNoBox: a section of rows to check and sort, as in the 覆写 section of the node source sheet
 * (docs/plan-a-round3.md D5) and on 「从节点源新建配置」 (docs/plan-a-round4.md E2). The checked
 * ids keep the order the user sorted them in and come first; the unchecked rows follow in the
 * order they are listed in.
 */
object CheckedOrder {
    /** [checked] with [id] checked, after the ones checked before, or unchecked. */
    fun toggle(checked: List<String>, id: String, on: Boolean): List<String> =
        when {
            !on -> checked.filter { it != id }
            id in checked -> checked
            else -> checked + id
        }

    /** [ids] with [from] moved to the place [to] has, as a drag does; [ids] when either is missing. */
    fun moved(ids: List<String>, from: String, to: String): List<String> {
        val fromIndex = ids.indexOf(from)
        val toIndex = ids.indexOf(to)
        if (fromIndex < 0 || toIndex < 0) return ids
        return ids.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /**
     * The rows of [all] split into the checked ones, in [checked] order, and the rest, in [all]
     * order. A checked id with no row any more is left out.
     */
    fun <T> split(all: List<T>, checked: List<String>, id: (T) -> String): Pair<List<T>, List<T>> {
        val byId = all.associateBy(id)
        return checked.distinct().mapNotNull(byId::get) to all.filter { id(it) !in checked }
    }

    /** [checked] with the ids of [current] that [known] lacked, such as a source made meanwhile, checked last. */
    fun withAdded(checked: List<String>, known: List<String>, current: List<String>): List<String> =
        checked + current.filter { it !in known && it !in checked }

    /** The override chain of a new profile: its checked node sources as shown, then its checked overrides as shown. */
    fun chain(sources: List<String>, overrides: List<String>): List<String> = sources + overrides
}
