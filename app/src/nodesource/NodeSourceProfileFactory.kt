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
 * KimiNoBox: 「从节点源新建配置」 (docs/plan-a-round2.md C5). A local profile whose own file is
 * empty ([NewProfileDefaults.EMPTY_PROFILE]): its nodes come from the bound node sources, and its
 * base fields, groups and rules from 「默认配置」 when that is bound, then from the overrides after
 * it (docs/plan-a-round4.md E3). With no override bound it has no group and no rule, so all traffic
 * goes direct.
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
            NodeSourceCopies.write(NewProfileDefaults.EMPTY_PROFILE.toByteArray(), source)
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
}
