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

package com.github.yumeyucca.yumebox.screen.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.runtime.api.Profile
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.util.UUID

/** KimiNoBox: the line a profile card shows about its config version. */
internal data class VersionNote(val text: String, val isError: Boolean)

internal object VersionNotes {
    const val PENDING = "待验证：这个版本还没有成功启动过"
    const val RESTORE_LKG = "恢复上一可用版本"
    const val RESTORE_DONE = "已恢复上一可用版本"
    const val RESTORE_NONE = "没有可恢复的版本"
    const val CLEAR_FAILURE = "清除失败记录"
    const val CLEAR_FAILURE_DONE = "已清除失败记录"
    private const val MAX_ERROR_CHARS = 160

    fun lastFailed(error: String) = "上次启动失败：${short(error)}"

    fun restored(error: String) = "已回退：新版本启动失败，正在用上一可用版本（${short(error)}）"

    /**
     * Null says nothing: a verified version, or one that never started and has nothing to go back
     * to (every newly imported profile).
     */
    fun of(info: ProfileVersions.Info): VersionNote? {
        val failure = info.lastFailure
        return when (info.status) {
            ProfileVersions.Status.Restored -> VersionNote(restored(failure?.error.orEmpty()), isError = true)
            ProfileVersions.Status.Pending ->
                when {
                    failure != null -> VersionNote("$PENDING；${lastFailed(failure.error)}", isError = true)
                    info.hasLkg -> VersionNote(PENDING, isError = false)
                    else -> null
                }

            ProfileVersions.Status.Verified,
            ProfileVersions.Status.None -> failure?.let { VersionNote(lastFailed(it.error), isError = true) }
        }
    }

    /** Offered for a version that has not started yet while an earlier one did. */
    fun canRestore(info: ProfileVersions.Info): Boolean =
        info.status == ProfileVersions.Status.Pending && info.hasLkg

    private fun short(error: String): String =
        error.lineSequence().joinToString(" ").let {
            if (it.length <= MAX_ERROR_CHARS) it else it.take(MAX_ERROR_CHARS) + "…"
        }
}

/**
 * KimiNoBox: version notes of the listed profiles. They change in the service process after a
 * start or reload, so the list re-reads them every few seconds while it is shown.
 */
@Composable
internal fun rememberVersionNotes(profiles: List<Profile>): Map<UUID, VersionNote> {
    val context = LocalContext.current.applicationContext
    var notes by remember { mutableStateOf(emptyMap<UUID, VersionNote>()) }
    val uuids = remember(profiles) { profiles.map(Profile::uuid) }
    LaunchedEffect(uuids) {
        while (true) {
            notes =
                withContext(Dispatchers.IO) {
                    uuids.mapNotNull { uuid ->
                        runCatching { ProfileVersions.status(context, uuid) }
                            .getOrNull()
                            ?.let(VersionNotes::of)
                            ?.let { uuid to it }
                    }
                        .toMap()
                }
            delay(REFRESH_MS)
        }
    }
    return notes
}

/** KimiNoBox: 「恢复上一可用版本」 and 「清除失败记录」 in a profile's edit menu, when they apply. */
@Composable
internal fun ProfileVersionActions(profile: Profile, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(profile.uuid) { mutableStateOf<ProfileVersions.Info?>(null) }
    LaunchedEffect(profile.uuid) {
        info = withContext(Dispatchers.IO) { runCatching { ProfileVersions.status(context, profile.uuid) }.getOrNull() }
    }
    val current = info ?: return
    if (VersionNotes.canRestore(current)) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val restored = withContext(Dispatchers.IO) { ProfileVersions.restoreLkg(context, profile.uuid) }
                    context.toast(if (restored) VersionNotes.RESTORE_DONE else VersionNotes.RESTORE_NONE)
                    onDone()
                }
            },
        ) {
            Text(VersionNotes.RESTORE_LKG)
        }
    }
    if (current.lastFailure != null) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { ProfileVersions.clearFailure(context, profile.uuid) }
                    context.toast(VersionNotes.CLEAR_FAILURE_DONE)
                    onDone()
                }
            },
        ) {
            Text(VersionNotes.CLEAR_FAILURE)
        }
    }
}

private const val REFRESH_MS = 3_000L
