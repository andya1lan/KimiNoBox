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

package com.github.yumeyucca.yumebox.screen.profiles

import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions.Failure
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions.Info
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNotesTest {
    private val failure = Failure(sha = "a", error = "proxy group[0]: 'x' not found", at = 1)

    @Test
    fun verifiedAndNeverStartedProfilesSayNothing() {
        assertNull(VersionNotes.of(Info(Status.Verified, lastFailure = null, hasLkg = true)))
        assertNull(VersionNotes.of(Info(Status.Pending, lastFailure = null, hasLkg = false)))
        assertNull(VersionNotes.of(Info(Status.None, lastFailure = null, hasLkg = false)))
    }

    @Test
    fun theThreeStatesOfTheCard() {
        assertEquals(
            VersionNote(VersionNotes.PENDING, isError = false),
            VersionNotes.of(Info(Status.Pending, lastFailure = null, hasLkg = true)),
        )
        assertEquals(
            VersionNote(VersionNotes.lastFailed(failure.error), isError = true),
            VersionNotes.of(Info(Status.Verified, lastFailure = failure, hasLkg = true)),
        )
        assertEquals(
            VersionNote(VersionNotes.restored(failure.error), isError = true),
            VersionNotes.of(Info(Status.Restored, lastFailure = failure.copy(restored = true), hasLkg = true)),
        )
    }

    @Test
    fun restoringIsOfferedOnlyForAPendingVersionWithAnLkg() {
        assertTrue(VersionNotes.canRestore(Info(Status.Pending, lastFailure = null, hasLkg = true)))
        assertFalse(VersionNotes.canRestore(Info(Status.Pending, lastFailure = null, hasLkg = false)))
        assertFalse(VersionNotes.canRestore(Info(Status.Restored, lastFailure = failure, hasLkg = true)))
        assertFalse(VersionNotes.canRestore(Info(Status.Verified, lastFailure = null, hasLkg = true)))
    }

    @Test
    fun longErrorsAreShortenedToOneLine() {
        val note = VersionNotes.lastFailed("line one\n" + "x".repeat(400))

        assertFalse(note.contains('\n'))
        assertTrue(note, note.endsWith("…"))
    }
}
