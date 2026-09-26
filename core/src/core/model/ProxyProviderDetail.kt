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

package com.github.yumeyucca.yumebox.core.model

/**
 * KimiNoBox: a proxy provider as the core's `GET /providers/proxies` reports it. The usage comes
 * from the `subscription-userinfo` of the core's own last download, kept in its cache file.
 */
data class ProxyProviderDetail(
    val name: String,
    /** `HTTP`, `File`, `Inline` or `Compatible`, as the core names it. */
    val vehicleType: String,
    /** Epoch milliseconds of the provider's content, or null when the core reports none. */
    val updatedAt: Long?,
    val nodeNames: List<String>,
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    /** Epoch seconds; null when the subscription sends none. */
    val expire: Long? = null,
)
