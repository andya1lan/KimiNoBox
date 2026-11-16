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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.common.util.toast
import com.github.yumeyucca.yumebox.core.util.AppVisibilityTracker
import com.github.yumeyucca.yumebox.presentation.component.AppCard
import com.github.yumeyucca.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumeyucca.yumebox.presentation.component.Title
import com.github.yumeyucca.yumebox.runtime.api.Profile
import com.github.yumeyucca.yumebox.runtime.service.profile.ProfileVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    const val SECTION = "配置版本"
    const val VIEW_CONFIG_SUMMARY = "导入的原始配置和最终生效的配置"
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
 * start or reload, so the list re-reads them every few seconds while the app is visible. The main
 * pager keeps this page composed in the background too.
 */
@Composable
internal fun rememberVersionNotes(profiles: List<Profile>): Map<UUID, VersionNote> {
    val context = LocalContext.current.applicationContext
    var notes by remember { mutableStateOf(emptyMap<UUID, VersionNote>()) }
    val uuids = remember(profiles) { profiles.map(Profile::uuid) }
    LaunchedEffect(uuids) {
        AppVisibilityTracker.isForeground.collectLatest { foreground ->
            if (!foreground) return@collectLatest
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
    }
    return notes
}

/**
 * KimiNoBox: the 编辑配置 page's 「查看配置」 row and, when they apply, 「恢复上一可用版本」 and
 * 「清除失败记录」.
 */
@Composable
internal fun ProfileConfigVersionSection(profile: Profile?, onViewConfig: (Profile) -> Unit) {
    val target = profile ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(target.uuid) { mutableStateOf<ProfileVersions.Info?>(null) }
    var reads by remember(target.uuid) { mutableIntStateOf(0) }
    LaunchedEffect(target.uuid, reads) {
        info = withContext(Dispatchers.IO) { runCatching { ProfileVersions.status(context, target.uuid) }.getOrNull() }
    }
    val current = info
    Title(VersionNotes.SECTION)
    AppCard {
        Column {
            PreferenceArrowItem(title = VIEW_CONFIG, summary = VersionNotes.VIEW_CONFIG_SUMMARY, onClick = { onViewConfig(target) })
            if (current != null && VersionNotes.canRestore(current)) {
                PreferenceArrowItem(
                    title = VersionNotes.RESTORE_LKG,
                    onClick = {
                        scope.launch {
                            val restored = withContext(Dispatchers.IO) { ProfileVersions.restoreLkg(context, target.uuid) }
                            context.toast(if (restored) VersionNotes.RESTORE_DONE else VersionNotes.RESTORE_NONE)
                            reads++
                        }
                    },
                )
            }
            if (current?.lastFailure != null) {
                PreferenceArrowItem(
                    title = VersionNotes.CLEAR_FAILURE,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ProfileVersions.clearFailure(context, target.uuid) }
                            context.toast(VersionNotes.CLEAR_FAILURE_DONE)
                            reads++
                        }
                    },
                )
            }
        }
    }
}

private const val REFRESH_MS = 3_000L
