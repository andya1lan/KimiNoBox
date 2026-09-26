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

import com.github.yumeyucca.yumebox.common.util.SubscriptionUserAgentDefaults
import com.github.yumeyucca.yumebox.data.store.AppSettingsStore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A downloaded subscription: the body as sent and the response headers by lower-case name. */
class DownloadedSubscription(val bytes: ByteArray, val headers: Map<String, String>)

/** A download that failed; [message] is the full reason, fit for the user. */
class NodeSourceDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * KimiNoBox: downloads a node source the way a remote subscription is downloaded (same User-Agent
 * setting), but reports why it failed. The URL carries the subscription token, so it is never
 * logged and never part of a message.
 */
class NodeSourceDownloader(private val appSettings: AppSettingsStore) {
    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = READ_TIMEOUT_MS
                requestTimeoutMillis = READ_TIMEOUT_MS
            }
        }
    }

    suspend fun download(url: String): DownloadedSubscription =
        withContext(Dispatchers.IO) {
            val response =
                try {
                    client.get(url) { header(HttpHeaders.UserAgent, userAgent()) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw NodeSourceDownloadException(describe(error), error)
                }
            if (!response.status.isSuccess()) {
                throw NodeSourceDownloadException(
                    "服务器返回 HTTP ${response.status.value} ${response.status.description}".trim() +
                        bodyHint(response)
                )
            }
            val bytes =
                try {
                    response.readRawBytes()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    throw NodeSourceDownloadException(describe(error), error)
                }
            if (bytes.size > MAX_BYTES) throw NodeSourceDownloadException("订阅超过 ${MAX_BYTES / 1024 / 1024} MB")
            DownloadedSubscription(
                bytes = bytes,
                headers = response.headers.entries().associate { (name, values) -> name.lowercase() to values.joinToString(", ") },
            )
        }

    private fun userAgent(): String = appSettings.customUserAgent.value.trim().ifEmpty { SubscriptionUserAgentDefaults.DEFAULT }

    private suspend fun bodyHint(response: HttpResponse): String =
        runCatching { response.bodyAsText().trim().take(BODY_HINT_CHARS) }
            .getOrDefault("")
            .takeIf { it.isNotEmpty() && !it.startsWith("<") }
            ?.let { "\n$it" }
            .orEmpty()

    private fun describe(error: Throwable): String {
        val detail = withoutUrls(error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName)
        val reason =
            when (error) {
                is UnknownHostException -> "无法解析订阅的域名"
                is HttpRequestTimeoutException,
                is SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> "下载超时（连接 15 秒、读取 60 秒）"
                is io.ktor.client.network.sockets.ConnectTimeoutException -> "连接超时（15 秒）"
                is ConnectException -> "连不上订阅服务器"
                is SSLException -> "TLS 握手失败"
                else -> "下载失败"
            }
        return "$reason\n$detail"
    }

    companion object {
        private val urlBracket = Regex("""\s*\[url=[^\]]*]""")
        private val url = Regex("""https?://[^\s"']+""", RegexOption.IGNORE_CASE)

        /** [text] without the subscription URL, which carries its token (Ktor puts it in messages). */
        fun withoutUrls(text: String): String = text.replace(urlBracket, "").replace(url, "<订阅地址>")

        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val READ_TIMEOUT_MS = 60_000L
        private const val MAX_BYTES = 32 * 1024 * 1024
        private const val BODY_HINT_CHARS = 200
    }
}
