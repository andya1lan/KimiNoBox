/*
 * This file is part of YumeBox.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumeyucca.yumebox.runtime.service.core

import android.content.Context
import android.os.SystemClock
import com.github.yumeyucca.yumebox.core.bridge.Channel
import com.github.yumeyucca.yumebox.core.bridge.NativeProcess
import com.github.yumeyucca.yumebox.core.util.runtimeHomeDir
import com.github.yumeyucca.yumebox.runtime.service.controller.CoreController
import java.io.File
import java.util.UUID
import timber.log.Timber

/**
 * Owns the inspect-only core child. It intentionally has no relationship with [CoreProcess]: no
 * Root record, no shared endpoint and no VPN socket-owner channel can leak across this boundary.
 */
class PreviewCoreProcess(private val context: Context) {
    private var process: NativeProcess? = null
    private var endpoint: CoreEndpoint? = null
    private var controller: CoreController? = null

    // KimiNoBox: serializes stop() so every caller returns only after the child is gone
    private val exitLock = Any()

    // KimiNoBox: the previous child is awaited outside the monitor, then the new one launched
    fun start(config: String): CoreEndpoint {
        stop()
        return launch(config)
    }

    @Synchronized
    private fun launch(config: String): CoreEndpoint {
        // Compiled provider paths and the geo/MMDB assets are rooted at runtimeHomeDir. Keep that
        // as the core home, but use a nested process workdir so preview diagnostics cannot overwrite
        // the real core's core.log.
        val home = context.runtimeHomeDir.apply { mkdirs() }
        val workdir = File(home, PREVIEW_WORKDIR).apply { mkdirs() }
        File(home, SOCK).delete()
        val (runtimeConfig, secret) = ensureControllerSecret(config)
        val nextEndpoint = CoreEndpoint(File(home, SOCK).absolutePath, secret)
        val args =
            arrayOf(
                "--home",
                home.absolutePath,
                "--controller",
                nextEndpoint.sock,
                "--mode",
                "preview",
            )
        val proc = spawn(home, workdir, args)
        try {
            Channel(proc.channelFd).use { channel ->
                val bytes = runtimeConfig.toByteArray(Charsets.UTF_8)
                var offset = 0
                while (offset < bytes.size) {
                    val length = minOf(CHUNK, bytes.size - offset)
                    channel.writeMessage(bytes, offset, length)
                    offset += length
                }
                // Closing the parent socket is the complete preview handoff. No descriptor and no
                // post-start RPC are ever sent to this process.
            }
        } catch (error: Throwable) {
            runCatching { proc.kill() }
            throw IllegalStateException("preview config handoff failed", error)
        }
        process = proc
        endpoint = nextEndpoint
        controller = CoreController(local = CoreController.Local(nextEndpoint.sock) { nextEndpoint.secret })
        Timber.tag(TAG).i("preview core launched, pid=%d", proc.pid)
        return nextEndpoint
    }

    /**
     * KimiNoBox: SIGTERM, wait for `/proc/<pid>` to vanish, SIGKILL after the grace period. A
     * preview blocked in ApplyConfig ignores SIGTERM while holding the shared cache.db lock, and a
     * real core started before it exits runs the whole session without a selection cache.
     * Blocking — callers stay off the main thread; the wait runs outside the monitor so
     * [isAlive]/[controller] never queue behind it.
     */
    fun stop() {
        synchronized(exitLock) {
            val previous = detach() ?: return
            runCatching { previous.terminate() }
            if (!awaitExit(previous.pid, TERM_GRACE_MS)) {
                Timber.tag(TAG).w("preview core ignored SIGTERM; sending SIGKILL")
                runCatching { previous.kill() }
                awaitExit(previous.pid, KILL_GRACE_MS)
            }
        }
    }

    @Synchronized
    private fun detach(): NativeProcess? {
        val previous = process
        process = null
        endpoint = null
        controller = null
        return previous
    }

    private fun awaitExit(pid: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (File("/proc/$pid").exists()) {
            if (SystemClock.elapsedRealtime() >= deadline) return false
            Thread.sleep(EXIT_POLL_MS)
        }
        return true
    }

    @Synchronized
    fun isAlive(): Boolean {
        val pid = process?.pid ?: return false
        // On some Android builds `kill(pid, 0)` from the app process is intermittently denied
        // while a just-forked child is still completing exec. The preview PID is app-owned and
        // short-lived, so its proc entry is the reliable liveness signal for this handle.
        return File("/proc/$pid").exists()
    }

    @Synchronized
    fun controller(): CoreController = checkNotNull(controller) { "Preview core is not running" }

    @Synchronized
    fun endpoint(): CoreEndpoint? = endpoint

    private fun spawn(home: File, workdir: File, args: Array<String>): NativeProcess {
        val launchArgs = CoreArtifacts.previewArguments(context, args)
        return try {
            NativeProcess.start(CoreArtifacts.previewShell(context).absolutePath, launchArgs, workdir.absolutePath)
        } catch (error: Throwable) {
            Timber.tag(TAG).w(error, "preview exec from nativeLibraryDir failed; retrying extracted copy")
            NativeProcess.start(extractBin().absolutePath, launchArgs, workdir.absolutePath)
        }
    }

    private fun extractBin(): File {
        val source = CoreArtifacts.previewShell(context)
        val target = File(context.filesDir, "bin/preview")
        target.parentFile?.mkdirs()
        if (!target.exists() || target.length() != source.length()) {
            source.copyTo(target, overwrite = true)
        }
        target.setReadable(true, false)
        target.setExecutable(true, false)
        return target
    }

    private fun ensureControllerSecret(config: String): Pair<String, String> {
        val secret = UUID.randomUUID().toString().replace("-", "")
        val line = "secret: \"$secret\""
        val lines = config.lineSequence().toMutableList()
        val index = lines.indexOfFirst { it.trimStart().startsWith("secret:") }
        if (index >= 0) {
            lines[index] = line
        } else {
            lines.add(0, line)
        }
        return lines.joinToString("\n") to secret
    }

    private companion object {
        const val TAG = "PreviewCoreProcess"
        const val SOCK = "preview.sock"
        const val PREVIEW_WORKDIR = "preview"
        const val CHUNK = 32 * 1024
        // KimiNoBox: preview handoff grace periods (mirrors CoreProcess.stopVpnProcess)
        const val TERM_GRACE_MS = 1_500L
        const val KILL_GRACE_MS = 500L
        const val EXIT_POLL_MS = 25L
    }
}
