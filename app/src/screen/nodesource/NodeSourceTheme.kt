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

package com.github.yumeyucca.yumebox.screen.nodesource

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: Material 3 for the node source screens, dressed in the app's own colors: every role
 * comes from the current miuix scheme (theme color, dark mode), so no Material default purple
 * shows up next to the rest of the app.
 */
@Composable
internal fun NodeSourceTheme(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    MaterialTheme(colorScheme = materialSchemeOf(colors), content = content)
}

private fun materialSchemeOf(c: Colors): ColorScheme {
    val dark = c.background.luminance() < 0.5f
    val build = if (dark) ::darkScheme else ::lightScheme
    return build(c)
}

private fun lightScheme(c: Colors): ColorScheme =
    lightColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        inversePrimary = c.primaryVariant,
        secondary = c.primary,
        onSecondary = c.onPrimary,
        secondaryContainer = c.primaryContainer,
        onSecondaryContainer = c.onPrimaryContainer,
        tertiary = c.primaryVariant,
        onTertiary = c.onPrimaryVariant,
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.onTertiaryContainer,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariantSummary,
        surfaceTint = c.primary,
        inverseSurface = c.onSurface,
        inverseOnSurface = c.surface,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
        outline = c.outline,
        outlineVariant = c.dividerLine,
        scrim = Color.Black,
        surfaceBright = c.surface,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHighest,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.background,
        surfaceDim = c.surfaceVariant,
    )

private fun darkScheme(c: Colors): ColorScheme =
    darkColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        inversePrimary = c.primaryVariant,
        secondary = c.primary,
        onSecondary = c.onPrimary,
        secondaryContainer = c.primaryContainer,
        onSecondaryContainer = c.onPrimaryContainer,
        tertiary = c.primaryVariant,
        onTertiary = c.onPrimaryVariant,
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.onTertiaryContainer,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariantSummary,
        surfaceTint = c.primary,
        inverseSurface = c.onSurface,
        inverseOnSurface = c.surface,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
        outline = c.outline,
        outlineVariant = c.dividerLine,
        scrim = Color.Black,
        surfaceBright = c.surfaceContainerHigh,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHighest,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.background,
        surfaceDim = c.background,
    )
