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

package com.github.yumeyucca.yumebox.runtime.service

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Brings the runtime back after the system kills the app process.
 *
 * ColorOS kills the process, and the core child with it, without performing the restart that
 * START_REDELIVER_INTENT asks for, so the VPN stayed down until the user noticed. The coordinator
 * records that a runtime should be up for the rest of this boot; a process start or the periodic
 * watchdog job then restores it through [AutoRestartService]. Explicit stops (user, Wi-Fi
 * automation, revoke by another VPN) and failures clear the record, so this never fights the
 * user, another VPN app or a broken profile. A reboot ends the record; boot start stays with the
 * auto start setting.
 */
object SelfHeal {
    private const val TAG = "SelfHeal"
    private const val JOB_ID = 0x4B4E4201
    private const val WATCHDOG_PERIOD_MS = 15 * 60 * 1000L
    private const val KEY_EXPECTED_BOOT = "self_heal_expected_boot"
    const val TRIGGER_PROCESS_START = "process_start"
    const val TRIGGER_WATCHDOG = "watchdog"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache: MMKV
        get() = MMKV.mmkvWithID("service_cache", MMKV.MULTI_PROCESS_MODE)

    /** The runtime reached Running: expect it until an explicit stop, and arm the watchdog. */
    fun markRunning(context: Context) {
        cache.encode(KEY_EXPECTED_BOOT, bootCount(context))
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job =
            JobInfo.Builder(JOB_ID, ComponentName(context, SelfHealJobService::class.java))
                .setPeriodic(WATCHDOG_PERIOD_MS)
                .build()
        runCatching { scheduler.schedule(job) }
            .onFailure { error -> Timber.tag(TAG).w(error, "Watchdog not scheduled") }
    }

    /** An explicit stop or a failure: nothing comes back on its own. */
    fun markStopped(context: Context) {
        cache.removeValueForKey(KEY_EXPECTED_BOOT)
        cancelWatchdog(context)
    }

    /** True while a runtime was left running in this boot and nothing stopped it on purpose. */
    fun isExpected(context: Context): Boolean {
        val expectedBoot = cache.decodeInt(KEY_EXPECTED_BOOT, -1)
        return expectedBoot >= 0 && expectedBoot == bootCount(context)
    }

    /** From Application.onCreate: a process that starts while a runtime is expected restores it. */
    fun onProcessStart(context: Context) {
        val appContext = context.applicationContext
        if (!isExpected(appContext)) return
        scope.launch { restoreIfNeeded(appContext, TRIGGER_PROCESS_START) }
    }

    suspend fun restoreIfNeeded(context: Context, trigger: String) {
        if (!isExpected(context)) {
            cancelWatchdog(context)
            return
        }
        // verify() bootstraps the coordinator first, which reattaches a surviving root daemon.
        RuntimeCoordinator.verify()
        if (RuntimeCoordinator.state.value.active) return
        val detail = "trigger=$trigger" + lastExitSummary(context)?.let { " last_exit=$it" }.orEmpty()
        Timber.tag(TAG).i("Restoring runtime: %s", detail)
        val intent =
            Intent(context, AutoRestartService::class.java)
                .putExtra(AutoRestartService.EXTRA_REASON, AutoRestartService.REASON_SELF_HEAL)
                .putExtra(AutoRestartService.EXTRA_DETAIL, detail)
        runCatching { context.startForegroundService(intent) }
            .onFailure { error -> Timber.tag(TAG).w(error, "Restore not started") }
    }

    private fun cancelWatchdog(context: Context) {
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) }
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** The previous process's death as the system recorded it, for the runtime log. */
    private fun lastExitSummary(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val info =
            runCatching {
                    context
                        .getSystemService(ActivityManager::class.java)
                        ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                        ?.firstOrNull()
                }
                .getOrNull() ?: return null
        val at = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(info.timestamp))
        val description = info.description?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
        return "reason=${info.reason} status=${info.status} importance=${info.importance} " +
            "at=$at$description"
    }
}
