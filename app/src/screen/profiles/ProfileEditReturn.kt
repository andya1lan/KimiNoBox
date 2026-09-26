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

import androidx.compose.runtime.*
import com.github.yumeyucca.yumebox.runtime.api.Profile

/**
 * KimiNoBox: the 「编辑」 dialog comes back after the editor or the config viewer it opened. The
 * profiles page is composed again when the pushed page is popped, so the request is read once when
 * the page composes and never reaches the page that is still sliding out.
 */
internal object ProfileEditReturn {
    @Volatile private var pendingUuid: String? = null

    fun request(profile: Profile) {
        pendingUuid = profile.uuid.toString()
    }

    fun take(): String? = pendingUuid.also { pendingUuid = null }
}

@Composable
internal fun ReopenEditOptionsOnReturn(state: ProfilesDialogState, profiles: List<Profile>) {
    val uuid = remember { ProfileEditReturn.take() } ?: return
    var reopened by remember { mutableStateOf(false) }
    LaunchedEffect(profiles) {
        if (reopened) return@LaunchedEffect
        val profile = profiles.firstOrNull { it.uuid.toString() == uuid } ?: return@LaunchedEffect
        reopened = true
        state.editOptionsTarget = profile
        state.showEditOptions = true
    }
}
