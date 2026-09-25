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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileCommitTest {
    @get:Rule val tmp = TemporaryFolder()

    private class InjectedFailure(step: String) : RuntimeException(step)

    /** A committed profile plus a staged update of it, prepared the way ProfileProcessor does. */
    private inner class Fixture {
        val target: File = tmp.newFolder()
        val staging: File = tmp.newFolder()
        val baseline: ProfileCommit.Baseline

        init {
            target.put(CONFIG, OLD_CONFIG, T0)
            target.put(PROVIDER_A, "old-a", T0)
            target.put(PROVIDER_B, "old-b", T0)
            target.put(RUNTIME, "old-runtime", T0)

            staging.deleteRecursively()
            target.copyRecursively(staging, overwrite = true)
            baseline = ProfileCommit.baseline(staging)

            // The update: new config, a refreshed provider A, a new provider C.
            staging.put(CONFIG, NEW_CONFIG, T1)
            staging.put(PROVIDER_A, "new-a", T1)
            staging.put(PROVIDER_C, "new-c", T1)
        }

        fun commit(step: (String) -> Unit = {}) = ProfileCommit.commit(staging, target, baseline, step)
    }

    @Test
    fun writesChangedFilesWithConfigLast() {
        val fixture = Fixture()
        val steps = mutableListOf<String>()

        fixture.commit { steps += it }

        assertEquals(NEW_CONFIG, fixture.target.read(CONFIG))
        assertEquals("new-a", fixture.target.read(PROVIDER_A))
        assertEquals("new-c", fixture.target.read(PROVIDER_C))
        assertEquals("rename:$CONFIG", steps.last())
        // Unchanged staged files are not written back at all.
        assertFalse(steps.any { it.endsWith(PROVIDER_B) || it.endsWith(RUNTIME) })
        // Staged mtimes survive (the core schedules provider refreshes by them).
        assertEquals(T1, fixture.target.resolve(PROVIDER_A).lastModified())
        assertNoTemps(fixture.target)
    }

    @Test
    fun failureAtEveryStepLeavesACompleteConfig() {
        val steps = mutableListOf<String>()
        Fixture().commit { steps += it }
        assertTrue(steps.size >= 6)

        steps.forEach { failAt ->
            val fixture = Fixture()
            try {
                fixture.commit { if (it == failAt) throw InjectedFailure(it) }
            } catch (_: InjectedFailure) {}

            val target = fixture.target
            assertTrue(failAt, target.isDirectory)
            val config = target.read(CONFIG)
            assertTrue("$failAt: $config", config == OLD_CONFIG || config == NEW_CONFIG)
            assertEquals(failAt, OLD_CONFIG, config)
            // Whatever was written already is complete; the rest is still the old file.
            assertTrue(failAt, target.read(PROVIDER_A) in setOf("old-a", "new-a"))
            assertEquals(failAt, "old-b", target.read(PROVIDER_B))
            assertNoTemps(target)

            // A retry completes the update.
            fixture.commit()
            assertEquals(failAt, NEW_CONFIG, target.read(CONFIG))
        }
    }

    @Test
    fun keepsAProviderCopyTheCoreRefreshedMeanwhile() {
        val fixture = Fixture()
        fixture.target.put(PROVIDER_A, "kernel-a", T2)

        fixture.commit()

        assertEquals("kernel-a", fixture.target.read(PROVIDER_A))
        assertEquals(NEW_CONFIG, fixture.target.read(CONFIG))
    }

    @Test
    fun keepsFilesTheUpdateDidNotProduce() {
        val fixture = Fixture()
        fixture.staging.resolve(PROVIDER_B).delete()
        // The runtime rewrote runtime.yaml during the update; the stale staged copy must not win.
        fixture.target.put(RUNTIME, "reloaded-runtime", T2)

        fixture.commit()

        assertEquals("old-b", fixture.target.read(PROVIDER_B))
        assertEquals("reloaded-runtime", fixture.target.read(RUNTIME))
    }

    @Test
    fun cleansTempFilesOfAKilledCommit() {
        val fixture = Fixture()
        val leftover = fixture.target.resolve(".config.yaml.1a2b3c4d${AtomicFiles.TEMP_SUFFIX}")
        leftover.writeText("half written")
        val nested = fixture.target.resolve("providers/proxies/.a.yaml.5e6f7a8b${AtomicFiles.TEMP_SUFFIX}")
        nested.writeText("half written")

        fixture.commit()

        assertFalse(leftover.exists())
        assertFalse(nested.exists())
        assertEquals(NEW_CONFIG, fixture.target.read(CONFIG))
    }

    @Test
    fun firstCommitCreatesTheProfileDir() {
        val staging = tmp.newFolder()
        val target = tmp.root.resolve("never-committed")
        val baseline = ProfileCommit.baseline(staging)
        staging.put(CONFIG, NEW_CONFIG, T1)
        staging.put(PROVIDER_A, "new-a", T1)

        ProfileCommit.commit(staging, target, baseline)

        assertEquals(NEW_CONFIG, target.read(CONFIG))
        assertEquals("new-a", target.read(PROVIDER_A))
    }

    private fun File.put(path: String, text: String, mtime: Long) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(text)
        file.setLastModified(mtime)
    }

    private fun File.read(path: String): String = resolve(path).readText()

    private fun assertNoTemps(dir: File) {
        assertEquals(emptyList<File>(), dir.walkTopDown().filter { AtomicFiles.isTemp(it) }.toList())
    }

    private companion object {
        const val CONFIG = "config.yaml"
        const val RUNTIME = "runtime.yaml"
        const val PROVIDER_A = "providers/proxies/a.yaml"
        const val PROVIDER_B = "providers/proxies/b.yaml"
        const val PROVIDER_C = "providers/proxies/c.yaml"
        const val OLD_CONFIG = "mixed-port: 7890\n# old, complete\n"
        const val NEW_CONFIG = "mixed-port: 7891\n# new, complete\n"
        const val T0 = 1_700_000_000_000L
        const val T1 = 1_700_000_100_000L
        const val T2 = 1_700_000_200_000L
    }
}
