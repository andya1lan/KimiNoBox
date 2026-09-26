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

package com.github.yumeyucca.yumebox.screen.nodesource

import com.github.yumeyucca.yumebox.common.util.ByteFormatter
import com.github.yumeyucca.yumebox.nodesource.NodeSourceInfo
import tf.gal.yumebox.locale.YumeTxt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** KimiNoBox: node source info in the words the profile card already uses for subscriptions. */
internal object NodeSourceFormat {
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - epochMillis).coerceAtLeast(0L)
        val minutes = diff / 60_000
        val hours = minutes / 60
        return when {
            diff < 60_000 -> YumeTxt.Component.ProfileCard.JustNow
            minutes < 60 -> YumeTxt.Component.ProfileCard.MinutesAgo.format(minutes.toInt())
            hours < 24 -> YumeTxt.Component.ProfileCard.HoursAgo.format(hours.toInt())
            else -> YumeTxt.Component.ProfileCard.DaysAgo.format((hours / 24).toInt())
        }
    }

    /** "流量：used / total (p%)", "已用：used", or null when the subscription reports none. */
    fun traffic(info: NodeSourceInfo): String? {
        val used = info.used
        val total = info.total?.takeIf { it > 0 }
        return when {
            total != null -> YumeTxt.Component.ProfileCard.Traffic.format(
                ByteFormatter.format(used ?: 0L),
                ByteFormatter.format(total),
                ((used ?: 0L) * 100 / total).toInt(),
            )
            used != null && used > 0 -> YumeTxt.Component.ProfileCard.UsedTraffic.format(ByteFormatter.format(used))
            else -> null
        }
    }

    /** Share of the traffic used, 0..1, or null without a total. */
    fun usedFraction(info: NodeSourceInfo): Float? {
        val total = info.total?.takeIf { it > 0 } ?: return null
        return ((info.used ?: 0L).toFloat() / total).coerceIn(0f, 1f)
    }

    fun expiry(info: NodeSourceInfo, today: LocalDate = LocalDate.now()): String? {
        val expire = info.expire ?: return null
        val date = Instant.ofEpochSecond(expire).atZone(ZoneId.systemDefault()).toLocalDate()
        val daysLeft = ChronoUnit.DAYS.between(today, date)
        return when {
            daysLeft > 0 -> YumeTxt.Component.ProfileCard.ExpireAt.format(date, daysLeft.toInt())
            daysLeft == 0L -> YumeTxt.Component.ProfileCard.ExpireToday
            else -> YumeTxt.Component.ProfileCard.Expired.format(date)
        }
    }

    /** Where the numbers came from, with the time they were read. */
    fun origin(info: NodeSourceInfo): String =
        when (info.origin) {
            NodeSourceInfo.Origin.App -> "App 下载时的数据"
            NodeSourceInfo.Origin.Kernel -> "内核数据"
        } + "，" + relativeTime(info.fetchedAt) + "获取"

    /** The first free name among [name], "[name] 2", "[name] 3", … */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        var index = 2
        while ("$name $index" in taken) index++
        return "$name $index"
    }
}
