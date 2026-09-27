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
 * KimiNoBox: a section of rows to check and sort, such as the 覆写 section of the node source
 * sheet (docs/plan-a-round3.md D5) and the node sources of 「从节点源新建配置」. The checked ids
 * keep the order the user sorted them in.
 */
object CheckedOrder {
    /** [ids] with [from] moved to the place [to] has, as a drag does; [ids] when either is missing. */
    fun moved(ids: List<String>, from: String, to: String): List<String> {
        val fromIndex = ids.indexOf(from)
        val toIndex = ids.indexOf(to)
        if (fromIndex < 0 || toIndex < 0) return ids
        return ids.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /** [checked] with the ids of [current] that [known] lacked, such as a source made meanwhile, checked last. */
    fun withAdded(checked: List<String>, known: List<String>, current: List<String>): List<String> =
        checked + current.filter { it !in known && it !in checked }
}
