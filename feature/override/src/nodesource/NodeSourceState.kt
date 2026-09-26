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

import kotlinx.serialization.Serializable

/** The latest known state of a node source's subscription, kept by the App between VPN runs. */
@Serializable
data class NodeSourceInfo(
    /** When the nodes were last downloaded, epoch milliseconds. */
    val updatedAt: Long? = null,
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    /** Epoch seconds. */
    val expire: Long? = null,
    val nodeCount: Int? = null,
    /** When the App read this, epoch milliseconds. */
    val fetchedAt: Long,
    val origin: Origin,
) {
    enum class Origin {
        /** From the response the App downloaded itself (import, or an update while the VPN is off). */
        App,
        /** From the running core's `GET /providers/proxies`. */
        Kernel,
    }

    val used: Long?
        get() = if (upload == null && download == null) null else (upload ?: 0L) + (download ?: 0L)

    /**
     * [kernel] as read from the core, keeping the traffic and expiry of this info while the core
     * has none of its own: right after an import the core has not downloaded the source yet.
     */
    fun mergedWith(kernel: NodeSourceInfo): NodeSourceInfo {
        val kernelHasUsage = kernel.upload != null || kernel.download != null || kernel.total != null
        return if (kernelHasUsage) kernel
        else kernel.copy(upload = upload, download = download, total = total, expire = kernel.expire ?: expire)
    }

    companion object {
        fun fromDownload(meta: SubscriptionMeta, nodeCount: Int, now: Long): NodeSourceInfo =
            NodeSourceInfo(
                updatedAt = now,
                upload = meta.usage?.upload,
                download = meta.usage?.download,
                total = meta.usage?.total,
                expire = meta.usage?.expire,
                nodeCount = nodeCount,
                fetchedAt = now,
                origin = Origin.App,
            )
    }
}

@Serializable
data class NodeSourceState(
    val info: NodeSourceInfo? = null,
    /** The full reason of the last failed update; null after a success. */
    val lastError: String? = null,
)
