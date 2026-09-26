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

import android.content.Context
import android.net.Uri
import com.github.yumeyucca.yumebox.core.util.NodeSourceCopies
import com.github.yumeyucca.yumebox.data.store.ProfileBindingProvider
import com.github.yumeyucca.yumebox.runtime.api.Profile
import com.github.yumeyucca.yumebox.runtime.client.ProfilesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * KimiNoBox: 「从节点源新建配置」 (docs/plan-a-round2.md C5). A local profile holding only the base
 * fields; its nodes come from the bound node sources and its groups and rules from the bound rule
 * override. Without a rule override the one group and rule of [BASE_CONFIG] keep it usable; a
 * rule override such as ACL4SSR replaces both.
 */
class NodeSourceProfileFactory(
    private val context: Context,
    private val profiles: ProfilesRepository,
    private val bindings: ProfileBindingProvider,
) {
    /** Creates the profile, binds [overrideIds] in order, and imports it; returns its uuid. */
    suspend fun create(name: String, overrideIds: List<String>): UUID =
        withContext(Dispatchers.IO) {
            val source = NodeSourceCopies.directory(context).resolve("profiles").resolve("base-${System.currentTimeMillis()}.yaml")
            NodeSourceCopies.write(BASE_CONFIG.toByteArray(), source)
            val uuid = profiles.createProfile(Profile.Type.File, name, Uri.fromFile(source).toString())
            try {
                val config = NodeSourceCopies.profileDir(context, uuid.toString()).resolve("config.yaml")
                source.copyTo(config.apply { parentFile?.mkdirs() }, overwrite = true)
                overrideIds.forEach { bindings.addOverride(uuid.toString(), it) }
                profiles.updateProfile(uuid)
                uuid
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                runCatching { bindings.clearOverrides(uuid.toString()) }
                runCatching { profiles.deleteProfile(uuid) }
                throw error
            }
        }

    companion object {
        const val BASE_CONFIG =
            "# Made by KimiNoBox from node sources: nodes from the bound node sources, groups and\n" +
                "# rules from the bound rule override, or else the group and rule below.\n" +
                "mixed-port: 7890\n" +
                "mode: rule\n" +
                "log-level: info\n" +
                "ipv6: false\n" +
                "proxy-groups:\n" +
                "  - {name: 节点选择, type: select, include-all: true}\n" +
                "rules:\n" +
                "  - MATCH,节点选择\n"
    }
}
