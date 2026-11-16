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
package com.github.yumeyucca.yumebox.screen.home

// KimiNoBox: the address masking of the egress IP row (MoeHomeKimiExitIp.kt), kept from upstream's
// classic home IP display, which upstream removed.

internal fun maskIpAddress(ipAddress: String): String =
    if (ipAddress.contains(":")) {
        maskIpv6Address(ipAddress)
    } else {
        maskIpv4Address(ipAddress)
    }

private fun maskIpv4Address(ipAddress: String): String {
    val segments = ipAddress.split(".")
    if (segments.size != 4) {
        return "****"
    }
    return buildString {
        append(segments[0])
        append(".")
        append(segments[1])
        append(".")
        append("*".repeat(segments[2].length.coerceAtLeast(1)))
        append(".")
        append(segments[3])
    }
}

private fun maskIpv6Address(ipAddress: String): String {
    val visibleSegments = ipAddress.split(":").filter { it.isNotBlank() }
    return when {
        visibleSegments.isEmpty() -> "****"
        visibleSegments.size == 1 -> "${visibleSegments.first()}:****"
        visibleSegments.size == 2 ->
            "${visibleSegments[0]}:${"*".repeat(visibleSegments[1].length.coerceAtLeast(4))}"
        else -> "${visibleSegments[0]}:${visibleSegments[1]}:****"
    }
}
