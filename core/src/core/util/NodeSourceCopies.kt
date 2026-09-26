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

package com.github.yumeyucca.yumebox.core.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * KimiNoBox: where the cache file of a node source lives. The node-source override names its
 * provider path `./providers/src-<id>.yaml`, which the compiler rewrites into every profile's
 * `providers/proxies/` (`lib/native/rust/src/compiler/patch/paths.rs`), so each bound profile has
 * its own copy and the core only updates the copy of the profile it runs. The App keeps one more,
 * the master copy, written when it downloads the source itself.
 */
object NodeSourceCopies {
    const val DIR_NAME = "node-sources"
    private const val IMPORTED_DIR_NAME = "imported"

    /** The provider `path` the node-source template writes. */
    fun templatePath(pathId: String): String = "./providers/${fileName(pathId)}"

    fun fileName(pathId: String): String = "src-$pathId.yaml"

    fun directory(context: Context): File = context.filesDir.resolve(DIR_NAME)

    fun master(context: Context, pathId: String): File = directory(context).resolve(fileName(pathId))

    fun profileDir(context: Context, profileUuid: String): File =
        context.filesDir.resolve(IMPORTED_DIR_NAME).resolve(profileUuid)

    /** Where the compiler points the provider of [pathId] for the profile in [profileDir]. */
    fun profileCopy(profileDir: File, pathId: String): File =
        profileProviderScopeDir(profileDir, PROXY_PROVIDER_SCOPE).resolve(fileName(pathId))

    /** Every profile directory that holds a copy named [fileName]. */
    fun profileCopies(context: Context, fileName: String): List<File> =
        context.filesDir.resolve(IMPORTED_DIR_NAME)
            .listFiles(File::isDirectory)
            .orEmpty()
            .map { profileProviderScopeDir(it, PROXY_PROVIDER_SCOPE).resolve(fileName) }
            .filter(File::isFile)

    /**
     * Copies [from] over [to] through a temporary file and a rename, keeping the modification
     * time: the core reads it as the time of the last download and only downloads again at start
     * when that is older than the interval (`component/resource/fetcher.go`).
     */
    fun copy(from: File, to: File) {
        if (from.canonicalPath == to.canonicalPath) return
        val parent = requireNotNull(to.parentFile) { "no parent for $to" }
        parent.mkdirs()
        val temporary = File.createTempFile(".${to.name}.", ".tmp", parent)
        try {
            from.inputStream().use { input -> writeSynced(temporary) { input.copyTo(it) } }
            temporary.setLastModified(from.lastModified())
            check(temporary.renameTo(to)) { "cannot replace ${to.name}" }
        } finally {
            temporary.delete()
        }
    }

    /** Writes [bytes] as the newest copy at [to], through a temporary file and a rename. */
    fun write(bytes: ByteArray, to: File) {
        val parent = requireNotNull(to.parentFile) { "no parent for $to" }
        parent.mkdirs()
        val temporary = File.createTempFile(".${to.name}.", ".tmp", parent)
        try {
            writeSynced(temporary) { it.write(bytes) }
            check(temporary.renameTo(to)) { "cannot replace ${to.name}" }
        } finally {
            temporary.delete()
        }
    }

    /**
     * Writes [temporary] and syncs it to storage before the caller renames it over the old file:
     * without the sync, a power loss (or a killed emulator) right after the rename can leave a
     * file of the right size full of zero bytes.
     */
    private inline fun writeSynced(temporary: File, write: (FileOutputStream) -> Unit) {
        FileOutputStream(temporary).use { output ->
            write(output)
            output.fd.sync()
        }
    }
}

/**
 * KimiNoBox: which copy of a node source is the newest and which copies need it. The copies are
 * compared by modification time (the core touches its copy after every download); on a tie the
 * earlier one in the list wins, so callers put the copy they trust most first.
 */
object NodeSourceSync {
    /** One place a copy can be; [modifiedAt] is null when there is no file. */
    data class Copy<K>(val key: K, val modifiedAt: Long?)

    data class Plan<K>(val from: K, val to: List<K>)

    /** Null when there is no copy at all. */
    fun <K> plan(copies: List<Copy<K>>): Plan<K>? {
        val newest = copies.filter { it.modifiedAt != null }.maxByOrNull { it.modifiedAt!! } ?: return null
        val newestTime = newest.modifiedAt!!
        val stale =
            copies.filter { copy ->
                copy.key != newest.key && (copy.modifiedAt == null || copy.modifiedAt < newestTime)
            }
        return Plan(newest.key, stale.map(Copy<K>::key))
    }
}
