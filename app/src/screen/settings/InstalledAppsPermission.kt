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

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.screen.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.github.yumeyucca.yumebox.presentation.component.AppCard
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: decisions around MIUI/HyperOS's "get installed apps" permission, FlClash style. The
 * permission counts as supported whenever the system defines it — upstream additionally required
 * the defining package to be `com.lbe.security.miui`, which HyperOS builds may not match, so the
 * list silently stayed almost empty.
 */
internal object InstalledAppsPermission {
    const val NAME = "com.android.permission.GET_INSTALLED_APPS"

    /** Fewer non-system apps than this means the list is very likely filtered. */
    const val MIN_USER_APPS = 5

    fun isSupported(packageManager: PackageManager): Boolean =
        runCatching { packageManager.getPermissionInfo(NAME, 0) }.isSuccess

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED

    /** Ask on every page entry while the permission is not granted. */
    fun shouldRequest(supported: Boolean, granted: Boolean): Boolean = supported && !granted

    /**
     * Banner with a shortcut to the app's system settings: shown when the user denied the
     * request, or when the loaded list has too few user apps to be complete. Devices that do not
     * define the permission (AOSP, emulators) keep the upstream behaviour.
     */
    fun shouldShowBanner(
        supported: Boolean,
        granted: Boolean,
        denied: Boolean,
        userAppCount: Int?,
    ): Boolean {
        if (!supported) return false
        if (denied && !granted) return true
        return userAppCount != null && userAppCount < MIN_USER_APPS
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    object Text {
        const val BANNER = "未授予「获取应用列表」权限，列表可能不完整"
        const val GRANT = "去授权"
    }
}

@Composable
internal fun InstalledAppsPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    AppCard(
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.space12),
        insideMargin = PaddingValues(spacing.space16),
        applyHorizontalPadding = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Text(
                text = InstalledAppsPermission.Text.BANNER,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body2,
            )
            TextButton(
                text = InstalledAppsPermission.Text.GRANT,
                onClick = {
                    runCatching {
                        context.startActivity(InstalledAppsPermission.appDetailsIntent(context))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
