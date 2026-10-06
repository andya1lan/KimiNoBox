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
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.InetAddress

/**
 * Reports when the device's own networks, or their DNS servers, change under the VPN, the way
 * CMFA's NetworkObserveModule drives notifyDnsChanged. After Wi-Fi drops, the core still answers
 * from the cache it filled on Wi-Fi, and some of those addresses are unreachable over mobile data.
 */
internal class NetworkChangeWatcher(
    context: Context,
    private val onChange: () -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var watch: Watch? = null

    fun start() {
        if (watch != null) return
        val next = Watch()
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
        runCatching { connectivity?.registerNetworkCallback(request, next, handler) }
            .onSuccess { watch = next }
    }

    fun stop() {
        val current = watch ?: return
        watch = null
        runCatching { connectivity?.unregisterNetworkCallback(current) }
        handler.removeCallbacks(current.settle)
    }

    /** One registration's view of the networks and their DNS servers; used on [handler] only. */
    private inner class Watch : ConnectivityManager.NetworkCallback() {
        private val networks = mutableMapOf<Network, List<InetAddress>>()
        /** The networks as they last settled; null until the first settle after registering. */
        private var settled: Map<Network, List<InetAddress>>? = null

        val settle = Runnable {
            val before = settled
            val now = networks.toMap()
            settled = now
            // The first settle only records what the session started on.
            if (before != null && before != now && watch === this) onChange()
        }

        override fun onAvailable(network: Network) = update { putIfAbsent(network, emptyList()) }

        override fun onLost(network: Network) = update { remove(network) }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            update { put(network, linkProperties.dnsServers) }

        // A switch arrives as a burst (lost, available, link properties); report it once.
        private fun update(change: MutableMap<Network, List<InetAddress>>.() -> Unit) {
            networks.change()
            handler.removeCallbacks(settle)
            handler.postDelayed(settle, SETTLE_MS)
        }
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
