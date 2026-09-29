/*
 * This file is part of YumeBox.
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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumeyucca.yumebox.runtime.service.controller

import com.github.yumeyucca.yumebox.core.model.LogMessage
import com.github.yumeyucca.yumebox.runtime.api.LogObserver
import com.github.yumeyucca.yumebox.runtime.api.LogSubscription
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * REST log streams for one controller endpoint. KimiNoBox: each subscriber gets a stream of its own.
 * The session and the log page share one controller, and with a single stream the later subscriber
 * took it from the earlier one without telling it, so the log page stopped at every core restart.
 */
internal class CoreControllerLogStream(
    private val client: HttpClient,
    private val json: Json,
    private val logUrl: () -> String,
    private val applyAuth: HttpRequestBuilder.() -> Unit,
    private val probeUrl: () -> String, // KimiNoBox
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun subscribe(observer: LogObserver): LogSubscription {
        val sink = LogSink(observer)
        val job = scope.launch {
            while (isActive && sink.open) {
                try {
                    streamOnce(sink)
                    if (sink.open) sink.observer.onError(IOException("log stream ended"))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (sink.open) sink.observer.onError(error)
                    Timber.w(error, "log stream failed; retrying")
                }
                delay(LOG_STREAM_RETRY_MS)
            }
        }
        return LogSubscription {
            sink.open = false
            job.cancel()
        }
    }

    private suspend fun streamOnce(sink: LogSink) {
        // KimiNoBox: mihomo sends the /logs headers only with the first log line, which can be
        // half a minute away on an idle core. A reachable controller is reported as connected now.
        client.get(probeUrl()) { applyAuth() }
        if (!sink.open) return
        sink.observer.onConnected()
        client
            .prepareGet(logUrl()) {
                applyAuth()
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            }
            .execute { response ->
                if (!sink.open) return@execute
                sink.observer.onConnected()
                val channel = response.bodyAsChannel()
                while (sink.open && !channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    if (line.isBlank()) continue
                    val entry = runCatching { json.decodeFromString<RawLogLine>(line) }.getOrNull() ?: continue
                    sink.observer.newItem(
                        LogMessage(
                            level = parseLogLevel(entry.type),
                            message = entry.payload,
                            time = Date(),
                        )
                    )
                }
            }
    }

    private fun parseLogLevel(raw: String): LogMessage.Level =
        when (raw.trim().lowercase()) {
            "debug" -> LogMessage.Level.Debug
            "info" -> LogMessage.Level.Info
            "warning", "warn" -> LogMessage.Level.Warning
            "error" -> LogMessage.Level.Error
            "silent" -> LogMessage.Level.Silent
            else -> LogMessage.Level.Unknown
        }

    private class LogSink(val observer: LogObserver) {
        @Volatile var open = true
    }

    @Serializable private data class RawLogLine(val type: String = "info", val payload: String = "")

    private companion object {
        const val LOG_STREAM_RETRY_MS = 1_500L
    }
}
