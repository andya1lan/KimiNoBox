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

package com.github.yumeyucca.yumebox.runtime.service.session

import android.content.Context
import com.github.yumeyucca.yumebox.runtime.api.appContextOrSelf
import com.github.yumeyucca.yumebox.runtime.service.profile.ImportedDao
import com.github.yumeyucca.yumebox.runtime.service.util.importedDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The two read-only views of 「查看配置」 for any profile: the imported `config.yaml` and the final
 * config its VPN start would hand to the core.
 *
 * The final view compiles the VPN spec of the profile, so it has the global UA fragment, the
 * disable-all setting and the VPN run mode, through the same [CompiledConfigPipeline.compile]
 * call as a real start, which returns the YAML without writing a file.
 */
class ProfileConfigViews(context: Context) {
    private val context: Context = context.appContextOrSelf

    /** Each view is either the YAML or a failure whose message can be shown as is. */
    data class Views(val raw: Result<String>, val final: Result<String>)

    suspend fun load(uuid: UUID): Views =
        withContext(Dispatchers.IO) {
            val profile =
                ImportedDao.queryByUUID(uuid)
                    ?: return@withContext failed(IllegalStateException(PROFILE_MISSING))
            // Upstream never materialises the plaintext YAML of an encrypted profile in the app.
            if (!profile.ageSecretKey.isNullOrBlank()) {
                return@withContext failed(UnsupportedOperationException(ENCRYPTED))
            }
            val raw = runCatching {
                val file = context.importedDir.resolve("$uuid/config.yaml")
                check(file.isFile) { CONFIG_MISSING }
                file.readText()
            }
            Views(raw = raw, final = runCatching { compileFinal(uuid) })
        }

    private suspend fun compileFinal(uuid: UUID): String =
        CompiledConfigPipeline(context)
            .compile(SessionRuntimeSpecFactory(context).createVpnSpecFor(uuid))
            .finalYaml

    private fun failed(error: Throwable) = Views(Result.failure(error), Result.failure(error))

    private companion object {
        const val PROFILE_MISSING = "找不到这个配置"
        const val CONFIG_MISSING = "config.yaml 不存在，请先更新这个配置"
        const val ENCRYPTED = "加密配置不支持查看"
    }
}
