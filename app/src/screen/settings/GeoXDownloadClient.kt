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

import com.github.yumeyucca.yumebox.common.util.SubscriptionUserAgentDefaults
import com.github.yumeyucca.yumebox.data.store.AppSettingsStore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * KimiNoBox: downloads the GeoX files with the app's subscription User-Agent. This was the
 * download client of the Sub-Store module, which KimiNoBox removed.
 */
class GeoXDownloadClient(private val appSettings: AppSettingsStore) {
    private val client: HttpClient by lazy {
        // OkHttp, like every other client here. With the Android engine, cancelling a download
        // mid-read (leaving its page) closes the platform stream on the main thread, which throws
        // "Unbalanced enter/exit" from the cancel handler and kills the app.
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
            followRedirects = true
        }
    }

    /** Streams [url] into [targetFile]; false, and no file, on an HTTP error or a failed read. */
    suspend fun download(url: String, targetFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                targetFile.delete()
                client
                    .prepareGet(url) { header(HttpHeaders.UserAgent, userAgent()) }
                    .execute { response ->
                        if (!response.status.isSuccess()) return@execute false
                        response.bodyAsChannel().toInputStream().use { input ->
                            targetFile.outputStream().buffered().use { output -> input.copyTo(output) }
                        }
                        true
                    }
            } catch (error: CancellationException) {
                targetFile.delete()
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Timber.e(error, "Download failed: %s", url)
                targetFile.delete()
                false
            }
        }

    private fun userAgent(): String =
        appSettings.customUserAgent.value.ifEmpty { SubscriptionUserAgentDefaults.DEFAULT }
}
