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
 * KimiNoBox: reordering part of a profile's override chain (docs/plan-a-round3.md D5). The chain
 * mixes node sources and other overrides; the sheet sorts each kind on its own, and an entry of
 * one kind only ever takes a place an entry of the same kind held.
 */
object OverrideChain {
    /**
     * [chain] with its entries that [inGroup] picks put in the order of [order], each into a
     * place one of them held before; every other entry keeps its place. Entries of the group that
     * [order] misses follow in their chain order, and entries of [order] outside it are ignored.
     */
    fun <T> reorder(chain: List<T>, order: List<T>, inGroup: (T) -> Boolean): List<T> {
        val places = chain.indices.filter { inGroup(chain[it]) }
        val members = places.map(chain::get)
        val arranged = order.filter { it in members }.distinct() + members.filter { it !in order }
        return chain.toMutableList().also { result ->
            places.forEachIndexed { k, place -> result[place] = arranged[k] }
        }
    }
}
