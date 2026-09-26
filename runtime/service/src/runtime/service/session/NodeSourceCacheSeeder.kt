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
import com.github.yumeyucca.yumebox.core.util.NodeSourceCopies
import com.github.yumeyucca.yumebox.core.util.NodeSourceSync
import timber.log.Timber
import java.io.File

/**
 * KimiNoBox: right after a profile compiles, and before any core loads it, every node-source cache
 * the compiled config points at is brought up to the newest copy there is (the App's master copy
 * or another profile's). The core then starts from the file instead of downloading the source
 * again, which is what makes "the App downloads once" hold for a newly bound profile.
 */
internal object NodeSourceCacheSeeder {
    private val cachePath = Regex("""providers/proxies/(src-[a-z0-9]{8}\.yaml)""")

    fun seed(context: Context, profileDir: File, finalYaml: String) {
        cachePath.findAll(finalYaml).map { it.groupValues[1] }.toSet().forEach { fileName ->
            runCatching { seedOne(context, profileDir, fileName) }
                .onFailure { Timber.w(it, "Node source cache %s not seeded", fileName) }
        }
    }

    private fun seedOne(context: Context, profileDir: File, fileName: String) {
        val target = NodeSourceCopies.profileCopy(profileDir, fileName.removePrefix("src-").removeSuffix(".yaml"))
        val others =
            listOf(NodeSourceCopies.directory(context).resolve(fileName)) +
                NodeSourceCopies.profileCopies(context, fileName)
        val copies =
            (listOf(target) + others)
                .distinctBy(File::getCanonicalPath)
                .map { NodeSourceSync.Copy(it, it.takeIf(File::isFile)?.lastModified()) }
        val plan = NodeSourceSync.plan(copies) ?: return
        if (copies.first().key in plan.to) {
            NodeSourceCopies.copy(plan.from, target)
            Timber.i("Node source cache %s seeded", fileName)
        }
    }
}
