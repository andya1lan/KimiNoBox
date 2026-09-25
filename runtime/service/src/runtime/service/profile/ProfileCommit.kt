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

package com.github.yumeyucca.yumebox.runtime.service.profile

import java.io.File

/**
 * File-level atomic commit of a staged profile update, replacing "delete the profile dir, copy
 * staging over it":
 * - the profile directory is never moved or deleted;
 * - only files this update produced or changed (against the [Baseline] taken right after staging
 *   copied the profile dir) are written back, each through temp file + fsync + rename;
 * - a profile file newer than its staged copy (the core refreshed a provider meanwhile) is kept;
 * - `config.yaml`, the commit point, is written last;
 * - files the update did not produce are left alone; temp files of an interrupted commit are
 *   cleaned up by the next one.
 *
 * A kill at any step leaves a complete old or complete new `config.yaml`. Provider copies
 * written before it only carry newer node data, which the old config reads just as well.
 */
object ProfileCommit {
    const val CONFIG = "config.yaml"

    /**
     * Held by every writer of committed profile files (this commit, LKG restores), so none of
     * them interleaves with another.
     */
    val lock = Any()

    /** Size and mtime of one file; a changed stamp means the update produced or rewrote it. */
    data class Stamp(val size: Long, val lastModified: Long)

    /** Files of the staging copy before the update touched them, keyed by relative path. */
    class Baseline internal constructor(internal val stamps: Map<String, Stamp>)

    fun baseline(stagingDir: File): Baseline = Baseline(stampsOf(stagingDir))

    /**
     * Writes what [stagingDir] produced or changed since [baseline] into [targetDir]. [step] is a
     * fault-injection hook for tests, called before each file write and before each rename.
     */
    fun commit(
        stagingDir: File,
        targetDir: File,
        baseline: Baseline,
        step: (String) -> Unit = {},
    ) {
        synchronized(lock) {
            targetDir.mkdirs()
            AtomicFiles.cleanupTemps(targetDir)
            val changed =
                stampsOf(stagingDir).filter { (path, stamp) -> baseline.stamps[path] != stamp }.keys
            changed.filter { it != CONFIG }.sorted().forEach { path ->
                val staged = stagingDir.resolve(path)
                val target = targetDir.resolve(path)
                if (target.isFile && target.lastModified() > staged.lastModified()) return@forEach
                write(staged, target, path, step)
            }
            if (CONFIG in changed) {
                write(stagingDir.resolve(CONFIG), targetDir.resolve(CONFIG), CONFIG, step)
            }
            AtomicFiles.syncDir(targetDir)
        }
    }

    private fun write(staged: File, target: File, path: String, step: (String) -> Unit) {
        step("write:$path")
        AtomicFiles.copy(staged, target, beforeRename = { step("rename:$path") })
    }

    private fun stampsOf(dir: File): Map<String, Stamp> {
        if (!dir.isDirectory) return emptyMap()
        return dir.walkTopDown()
            .filter { it.isFile && !AtomicFiles.isTemp(it) }
            .associate { file ->
                file.relativeTo(dir).invariantSeparatorsPath to Stamp(file.length(), file.lastModified())
            }
    }
}
