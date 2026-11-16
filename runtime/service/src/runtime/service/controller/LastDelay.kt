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

package com.github.yumeyucca.yumebox.runtime.service.controller

import java.time.OffsetDateTime

/**
 * How long a delay test stands: two rounds of a node source's 「定期测速」 (every 5 minutes), so a
 * round that takes a while does not blank the delays it is about to renew.
 */
internal const val DELAY_FRESH_MILLIS = 10 * 60_000L

/**
 * The delay a node's [history] stands for at [nowMillis]: 0 (untested) when there is no test or
 * the last one is older than [DELAY_FRESH_MILLIS], -1 (timeout) when it failed (the core records
 * 0), the delay otherwise. A time the core wrote in an unknown format counts as fresh.
 */
internal fun lastDelay(history: List<RawDelay>, nowMillis: Long): Int {
    val last = history.lastOrNull() ?: return 0
    val testedAt = last.time?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    if (testedAt != null && nowMillis - testedAt > DELAY_FRESH_MILLIS) return 0
    return if (last.delay > 0) last.delay else -1
}
