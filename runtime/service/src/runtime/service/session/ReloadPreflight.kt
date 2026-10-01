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
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileConfigTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * KimiNoBox: the core's own config reload parses a new config first and keeps the running one
 * when it is rejected. A VPN reload forks a new core instead, so the same check runs before the
 * running core is stopped: the compiled config goes through the core's `--test`.
 */
internal object ReloadPreflight {
    private const val DIR = "reload-preflight"

    /** Throws with the core's reason when it would not load [config]. */
    suspend fun check(context: Context, config: String) {
        withContext(Dispatchers.IO) {
            val dir = context.cacheDir.resolve(DIR).apply { mkdirs() }
            val file = File.createTempFile("reload-", ".yaml", dir)
            try {
                file.writeText(config)
                ProfileConfigTester.validateMainConfig(context, file).getOrThrow()
            } finally {
                file.delete()
            }
        }
    }
}
