/*
 * This file is part of YumeBox.
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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumeyucca.yumebox.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

@Serializable
data class ConnectionSnapshot(
    val downloadTotal: Long = 0L,
    val uploadTotal: Long = 0L,
    // KimiNoBox: mihomo sends `"connections": null` when idle; decode it as an empty list
    @Serializable(with = NullAsEmptyConnectionsSerializer::class)
    val connections: List<ConnectionInfo> = emptyList(),
    val memory: Long = 0L,
)

// KimiNoBox: see ConnectionSnapshot.connections
@OptIn(ExperimentalSerializationApi::class)
internal object NullAsEmptyConnectionsSerializer : KSerializer<List<ConnectionInfo>> {
    private val delegate = ListSerializer(ConnectionInfo.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<ConnectionInfo>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<ConnectionInfo> =
        if (decoder.decodeNotNullMark()) {
            delegate.deserialize(decoder)
        } else {
            decoder.decodeNull()
            emptyList()
        }
}

@Serializable
data class ConnectionInfo(
    val id: String = "",
    val metadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("upload") val upload: Long = 0L,
    @SerialName("download") val download: Long = 0L,
    val start: String = "",
    val chains: List<String> = emptyList(),
    val providerChains: List<String> = emptyList(),
    val rule: String = "",
    val rulePayload: String = "",
)
