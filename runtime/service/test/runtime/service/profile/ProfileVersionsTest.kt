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

import com.github.yumeyucca.yumebox.runtime.api.RuntimePhase
import com.github.yumeyucca.yumebox.runtime.api.RuntimeSnapshot
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions.Status
import com.github.yumeyucca.yumebox.runtime.service.profile.VersionStore.Finish
import com.github.yumeyucca.yumebox.runtime.service.profile.VersionStore.Outcome
import com.github.yumeyucca.yumebox.runtime.service.session.RuntimeOperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileVersionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000L
    private lateinit var store: VersionStore
    private lateinit var config: File

    @Before
    fun setUp() {
        store = VersionStore(tmp.newFolder("profile-versions")) { now }
        config = tmp.newFolder("imported", UUID).resolve("config.yaml")
    }

    private fun commit(text: String) {
        config.writeText(text)
        now += 1_000
    }

    private fun begin(): ProfileVersions.Activation =
        ProfileVersions.Activation(UUID, VersionStore.shaOf(config)!!, config)

    private fun status() = store.status(UUID, config)

    @Test
    fun successfulStartPromotesTheVersionToLkg() {
        commit(V1)
        assertEquals(Status.Pending, status().status)

        assertEquals(Finish.Promoted, store.finish(begin(), Outcome.Succeeded))

        assertEquals(Status.Verified, status().status)
        assertTrue(status().hasLkg)
        assertEquals(Finish.Unchanged, store.finish(begin(), Outcome.Succeeded))
    }

    @Test
    fun failedPendingVersionIsReplacedByTheLkg() {
        commit(V1)
        store.finish(begin(), Outcome.Succeeded)
        commit(V2)
        val activation = begin()

        assertEquals(Finish.Restored, store.finish(activation, Outcome.Failed("reload failed: boom")))

        assertEquals(V1, config.readText())
        val info = status()
        assertEquals(Status.Restored, info.status)
        val failure = info.lastFailure!!
        assertEquals(activation.sha, failure.sha)
        assertEquals("reload failed: boom", failure.error)
        // The failed content is refused until the record is cleared.
        val staged = tmp.newFile("staged.yaml").apply { writeText(V2) }
        assertTrue(store.isKnownBad(UUID, staged))
        store.clearFailure(UUID)
        assertFalse(store.isKnownBad(UUID, staged))
        assertEquals(Status.Verified, status().status)
    }

    @Test
    fun failingVerifiedVersionIsNotRolledBack() {
        commit(V1)
        store.finish(begin(), Outcome.Succeeded)

        assertEquals(Finish.Recorded, store.finish(begin(), Outcome.Failed("VPN permission denied")))

        assertEquals(V1, config.readText())
        assertEquals(Status.Verified, status().status)
        assertFalse(status().lastFailure!!.restored)
        // A verified version stays committable.
        val staged = tmp.newFile("staged.yaml").apply { writeText(V1) }
        assertFalse(store.isKnownBad(UUID, staged))
        // Starting it again shows the failure was not this version's.
        assertEquals(Finish.Unchanged, store.finish(begin(), Outcome.Succeeded))
        assertNull(status().lastFailure)
    }

    @Test
    fun failureWithoutLkgOnlyRecords() {
        commit(V1)

        assertEquals(Finish.Recorded, store.finish(begin(), Outcome.Failed("parse error")))

        assertEquals(V1, config.readText())
        assertEquals(Status.Pending, status().status)
        assertFalse(status().hasLkg)
    }

    @Test
    fun resultIsIgnoredWhenTheConfigChangedDuringTheCall() {
        commit(V1)
        store.finish(begin(), Outcome.Succeeded)
        commit(V2)
        val activation = begin()
        commit(V3) // a newer commit landed while V2 was starting

        assertEquals(Finish.Ignored, store.finish(activation, Outcome.Failed("boom")))
        assertEquals(V3, config.readText())
        assertNull(status().lastFailure)

        assertEquals(Finish.Ignored, store.finish(activation, Outcome.Succeeded))
        assertEquals(Status.Pending, status().status)
    }

    @Test
    fun laterPromotionEndsTheRestoredState() {
        commit(V1)
        store.finish(begin(), Outcome.Succeeded)
        commit(V2)
        store.finish(begin(), Outcome.Failed("boom"))
        assertEquals(Status.Restored, status().status)

        commit(V3)
        store.finish(begin(), Outcome.Succeeded)

        assertEquals(Status.Verified, status().status)
    }

    @Test
    fun manualRestorePutsTheLkgBack() {
        commit(V1)
        assertFalse(store.restoreLkg(UUID, config))
        store.finish(begin(), Outcome.Succeeded)
        commit(V2)

        assertTrue(store.restoreLkg(UUID, config))
        assertEquals(V1, config.readText())
        assertFalse(store.restoreLkg(UUID, config))
    }

    @Test
    fun interruptedStartsProveNothing() {
        commit(V1)
        val activation = begin()
        val interrupted =
            ProfileVersions.outcomeOf(
                activation,
                RuntimeOperationResult(success = true),
                RuntimeSnapshot(phase = RuntimePhase.Idle),
            )
        assertEquals(Outcome.Inconclusive, interrupted)
        assertEquals(Finish.Ignored, store.finish(activation, interrupted))

        val otherProfile =
            ProfileVersions.outcomeOf(
                activation,
                RuntimeOperationResult(success = true),
                RuntimeSnapshot(phase = RuntimePhase.Running, profileUuid = "another"),
            )
        assertEquals(Outcome.Inconclusive, otherProfile)

        val running =
            ProfileVersions.outcomeOf(
                activation,
                RuntimeOperationResult(success = true),
                RuntimeSnapshot(phase = RuntimePhase.Running, profileUuid = UUID),
            )
        assertEquals(Outcome.Succeeded, running)
        assertEquals(
            Outcome.Failed("x"),
            ProfileVersions.outcomeOf(
                activation,
                RuntimeOperationResult(success = false, error = "x"),
                RuntimeSnapshot(phase = RuntimePhase.Running, profileUuid = UUID),
            ),
        )
    }

    @Test
    fun forgetDropsTheState() {
        commit(V1)
        store.finish(begin(), Outcome.Succeeded)
        assertEquals(listOf(UUID), store.tracked())

        store.forget(UUID)

        assertEquals(emptyList<String>(), store.tracked())
        assertEquals(Status.Pending, status().status)
    }

    private companion object {
        const val UUID = "3f1c2d4e-0000-4000-8000-000000000001"
        const val V1 = "mixed-port: 7890\n"
        const val V2 = "mixed-port: 7891\n"
        const val V3 = "mixed-port: 7892\n"
    }
}
