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
import android.os.SystemClock
import com.github.yumeyucca.yumebox.runtime.service.core.CoreProcess
import com.github.yumeyucca.yumebox.runtime.service.log.RuntimeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * KimiNoBox: the readiness wait gives a fresh VPN core about two seconds, and a core sometimes
 * needs more before it lists its groups (5–8 s on the emulator). A local core that is still alive
 * gets up to [GRACE_MS] more and is then accepted without groups (the group polling fills them
 * in), so the start is not failed and the new profile version not rolled back. A core that exited
 * keeps the failure.
 */
internal object LateGroups {
    private const val GRACE_MS = 10_000L
    private const val POLL_MS = 500L

    suspend fun tolerate(context: Context, log: RuntimeLog.Writer, awaitReady: suspend () -> Unit) {
        try {
            awaitReady()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalStateException) {
            if (!awaitAlive(context, log)) throw error
        }
    }

    /** True once the live core lists groups or the grace is over; false when the core exits. */
    private suspend fun awaitAlive(context: Context, log: RuntimeLog.Writer): Boolean {
        val deadline = SystemClock.elapsedRealtime() + GRACE_MS
        while (CoreProcess.isLocalCoreAlive()) {
            val groups =
                try {
                    CoreProcess.probeGroupCount(context)
                } catch (error: CancellationException) {
                    throw error
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    0
                }
            if (groups > 0) {
                log.w(RuntimeLog.Type.Verify, "groups exposed late: actualGroups=$groups")
                return true
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                log.w(RuntimeLog.Type.Verify, "no groups ${GRACE_MS}ms later; core alive, start accepted")
                return true
            }
            delay(POLL_MS)
        }
        return false
    }
}
