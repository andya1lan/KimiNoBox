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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileConfigTesterTest {
    private fun shell(script: String): Process =
        ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()

    @Test
    fun hungChildHoldingStdoutTimesOut() {
        // The old code blocked in readText() here: the child never closes stdout.
        val process = shell("echo started; sleep 30")
        val startedAt = System.nanoTime()

        val outcome = ProfileConfigTester.awaitProcess(process, timeoutMs = 300)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(ProfileConfigTester.ProcessOutcome.TimedOut, outcome)
        assertTrue("timeout must fire promptly, took ${elapsedMs}ms", elapsedMs < 5_000)
        assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(process.isAlive)
    }

    @Test
    fun exitedChildReportsCodeAndOutput() {
        val outcome = ProfileConfigTester.awaitProcess(shell("echo 'test failed: boom'; exit 3"), 5_000)

        outcome as ProfileConfigTester.ProcessOutcome.Exited
        assertEquals(3, outcome.exitCode)
        assertTrue(outcome.output.contains("test failed: boom"))
    }

    @Test
    fun successfulChildReportsZero() {
        val outcome = ProfileConfigTester.awaitProcess(shell("exit 0"), 5_000)

        assertEquals(ProfileConfigTester.ProcessOutcome.Exited(0, ""), outcome)
    }
}
