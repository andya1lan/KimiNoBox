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
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Crash-safe file replacement: write a temp file in the same directory, fsync it, then rename it
 * over the target. A reader (or a restart after a kill) sees either the old or the new file.
 */
object AtomicFiles {
    const val TEMP_SUFFIX = ".kimi-tmp"

    fun write(target: File, bytes: ByteArray, lastModified: Long? = null) =
        replace(target, lastModified) { it.write(bytes) }

    fun writeText(target: File, text: String, lastModified: Long? = null) =
        write(target, text.toByteArray(Charsets.UTF_8), lastModified)

    /** Copies [source] over [target]; [beforeRename] runs after fsync (fault-injection hook). */
    fun copy(
        source: File,
        target: File,
        lastModified: Long? = source.lastModified(),
        beforeRename: () -> Unit = {},
    ) = replace(target, lastModified, beforeRename) { out -> source.inputStream().use { it.copyTo(out) } }

    fun isTemp(file: File): Boolean = file.name.endsWith(TEMP_SUFFIX)

    /** Removes temp files an interrupted write left behind below [dir]. */
    fun cleanupTemps(dir: File) {
        if (!dir.isDirectory) return
        dir.walkTopDown().filter { it.isFile && isTemp(it) }.forEach { it.delete() }
    }

    /** Best-effort fsync of a directory so a completed rename survives power loss. */
    fun syncDir(dir: File) {
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }

    private inline fun replace(
        target: File,
        lastModified: Long?,
        beforeRename: () -> Unit = {},
        write: (FileOutputStream) -> Unit,
    ) {
        val dir = target.absoluteFile.parentFile ?: error("No parent directory for ${target.name}")
        dir.mkdirs()
        val temp = File(dir, ".${target.name}.${UUID.randomUUID().toString().take(8)}$TEMP_SUFFIX")
        try {
            FileOutputStream(temp).use { out ->
                write(out)
                out.flush()
                out.fd.sync()
            }
            if (lastModified != null && lastModified > 0L) temp.setLastModified(lastModified)
            beforeRename()
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }
}
