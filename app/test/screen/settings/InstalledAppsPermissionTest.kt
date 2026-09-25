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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsPermissionTest {
    @Test
    fun requestsWheneverSupportedButNotGranted() {
        assertTrue(InstalledAppsPermission.shouldRequest(supported = true, granted = false))
        assertFalse(InstalledAppsPermission.shouldRequest(supported = true, granted = true))
        // AOSP / emulator: the permission is not defined, nothing to ask for.
        assertFalse(InstalledAppsPermission.shouldRequest(supported = false, granted = false))
    }

    @Test
    fun unsupportedDevicesNeverShowTheBanner() {
        assertFalse(
            InstalledAppsPermission.shouldShowBanner(
                supported = false, granted = false, denied = true, userAppCount = 0,
            )
        )
    }

    @Test
    fun deniedRequestShowsTheBanner() {
        assertTrue(
            InstalledAppsPermission.shouldShowBanner(
                supported = true, granted = false, denied = true, userAppCount = null,
            )
        )
    }

    @Test
    fun tooFewUserAppsShowsTheBannerEvenWhenGranted() {
        assertTrue(
            InstalledAppsPermission.shouldShowBanner(
                supported = true, granted = true, denied = false, userAppCount = 4,
            )
        )
        assertFalse(
            InstalledAppsPermission.shouldShowBanner(
                supported = true, granted = true, denied = false, userAppCount = 5,
            )
        )
    }

    @Test
    fun noBannerWhileTheListIsStillLoading() {
        assertFalse(
            InstalledAppsPermission.shouldShowBanner(
                supported = true, granted = false, denied = false, userAppCount = null,
            )
        )
    }
}
