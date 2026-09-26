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

package com.github.yumeyucca.yumebox.screen.settings

import android.content.Context
import com.github.yumeyucca.yumebox.common.util.showToastDialog
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.core.model.GeoXItem
import com.github.yumeyucca.yumebox.core.util.runtimeHomeDir
import com.github.yumeyucca.yumebox.substore.util.SubStoreDownloadClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KimiNoBox: GeoX updates that never cost the working files. The download client deletes its
 * target before and after a failed download, so every file is fetched next to its target and
 * renamed over it only once complete; a failed or unusable download leaves the current file.
 */
internal object GeoXUpdate {
    data class Result(val updated: List<GeoXItem>, val failed: List<GeoXItem>)

    /**
     * Downloads live in a scope of their own, not the page's: leaving the page used to cancel a
     * download in flight, and the platform HTTP stack behind Ktor's Android engine then throws
     * `IllegalStateException: Unbalanced enter/exit` from the cancel handler on the main thread,
     * which crashed the app.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun start(
        context: Context,
        client: SubStoreDownloadClient,
        items: List<GeoXItem>,
        vpnRunning: () -> Boolean,
    ) {
        if (!running.compareAndSet(false, true)) {
            context.toast("GeoX 正在更新，完成后会提示")
            return
        }
        scope.launch {
            try {
                val result = download(client, context.runtimeHomeDir, items)
                val message = message(result, vpnRunning())
                if (result.failed.isEmpty()) context.toast(message) else showToastDialog(message)
            } finally {
                running.set(false)
            }
        }
    }

    private const val PARTIAL_SUFFIX = ".download"

    /** Trailing marker of the metadata section every MaxMind database ends with. */
    private val mmdbMetadataMarker =
        byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()) + "MaxMind.com".toByteArray()
    private const val MMDB_METADATA_WINDOW = 128 * 1024

    suspend fun download(
        client: SubStoreDownloadClient,
        runtimeHome: File,
        items: List<GeoXItem>,
    ): Result {
        runtimeHome.mkdirs()
        val updated = mutableListOf<GeoXItem>()
        val failed = mutableListOf<GeoXItem>()
        items.forEach { item ->
            val target = File(runtimeHome, item.fileName)
            val partial = File(runtimeHome, item.fileName + PARTIAL_SUFFIX)
            val replaced =
                try {
                    client.download(item.url, partial) && isUsable(partial) && partial.renameTo(target)
                } finally {
                    partial.delete()
                }
            if (replaced) updated += item else failed += item
        }
        return Result(updated, failed)
    }

    /** Non-empty, and a `.mmdb` must carry the MaxMind metadata (not an HTML error page). */
    internal fun isUsable(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (!file.name.removeSuffix(PARTIAL_SUFFIX).endsWith(".mmdb", ignoreCase = true)) return true
        val tailLength = minOf(file.length(), MMDB_METADATA_WINDOW.toLong()).toInt()
        val tail = ByteArray(tailLength)
        file.inputStream().use { input ->
            input.skip(file.length() - tailLength)
            var read = 0
            while (read < tailLength) {
                val count = input.read(tail, read, tailLength - read)
                if (count < 0) break
                read += count
            }
        }
        return tail.indexOf(mmdbMetadataMarker) >= 0
    }

    /** The summary line: counts, the files kept, and that a running core needs a restart. */
    fun message(result: Result, vpnRunning: Boolean): String {
        val total = result.updated.size + result.failed.size
        val parts = mutableListOf("下载完成：${result.updated.size}/$total")
        if (result.failed.isNotEmpty()) {
            parts += "失败：${result.failed.joinToString("、") { it.title }}，已保留原文件"
        }
        if (vpnRunning && result.updated.isNotEmpty()) parts += "重启 VPN 后生效"
        return parts.joinToString("；")
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        if (pattern.isEmpty() || size < pattern.size) return -1
        for (start in 0..size - pattern.size) {
            if (pattern.indices.all { this[start + it] == pattern[it] }) return start
        }
        return -1
    }
}
