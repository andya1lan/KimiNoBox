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

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeSourceDownloaderTest {
    @Test
    fun messagesLoseTheSubscriptionUrl() {
        assertEquals(
            "Request timeout has expired",
            NodeSourceDownloader.withoutUrls(
                "Request timeout has expired [url=https://sub.example.com/api?token=abc123, request_timeout=60000 ms]"
            ),
        )
        assertEquals(
            "Get \"<订阅地址>\": context deadline exceeded",
            NodeSourceDownloader.withoutUrls("Get \"https://sub.example.com/api?token=abc123\": context deadline exceeded"),
        )
        assertEquals("404 Not Found", NodeSourceDownloader.withoutUrls("404 Not Found"))
    }
}
