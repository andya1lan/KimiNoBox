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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.github.yumeyucca.yumebox.runtime.api.RuntimePhase
import com.github.yumeyucca.yumebox.runtime.api.RuntimeSnapshot
import com.github.yumeyucca.yumebox.runtime.service.config.ServiceStore
import com.github.yumeyucca.yumebox.runtime.service.session.RuntimeOperationResult
import com.github.yumeyucca.yumebox.runtime.service.session.RuntimeSpec
import com.github.yumeyucca.yumebox.runtime.service.util.importedDir
import com.github.yumeyucca.yumebox.runtime.service.util.sendProfileChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Which config of a profile is known to start, for every profile type. The version is the
 * sha256 of `config.yaml`; it is "verified" once a start or hot reload of exactly that version
 * reached Running, which also saves it as the last known good (LKG) config. A pending version
 * whose start fails is replaced by the LKG, so the next start, even after an app restart, uses a
 * config that worked.
 *
 * State lives in `filesDir/profile-versions/<uuid>/` (`state.json`, `lkg.yaml`), outside the
 * profile dir that every update stages and writes back.
 */
object ProfileVersions {
    const val KNOWN_BAD = "内容与上次启动失败的版本相同"

    private fun restoredText(error: String) = "新版本启动失败，已恢复上一可用版本：$error"

    enum class Status {
        /** No committed config. */
        None,
        Verified,
        Pending,
        /** Verified, but only because a failed newer version was replaced by the LKG. */
        Restored,
    }

    @Serializable
    data class Failure(
        val sha: String,
        val error: String,
        val at: Long,
        /** The failed version was replaced by the LKG. */
        val restored: Boolean = false,
    )

    data class Info(val status: Status, val lastFailure: Failure?, val hasLkg: Boolean)

    /** The version one start()/reload() attempts, read right before the call. */
    data class Activation(val uuid: String, val sha: String, val config: File)

    fun beginActivation(spec: RuntimeSpec): Activation? {
        if (spec.preview) return null
        val config = File(spec.profileDir, ProfileCommit.CONFIG)
        return VersionStore.shaOf(config)?.let { Activation(spec.profileUuid, it, config) }
    }

    /** Promotes, restores or records after start()/reload() returned [result]. */
    fun finishActivation(
        context: Context,
        activation: Activation?,
        result: RuntimeOperationResult,
        snapshot: RuntimeSnapshot,
    ) {
        activation ?: return
        val outcome = outcomeOf(activation, result, snapshot)
        val finish =
            runCatching { store(context).finish(activation, outcome) }
                .onFailure { Timber.tag(TAG).w(it, "version bookkeeping failed for %s", activation.uuid) }
                .getOrNull()
        Timber.tag(TAG)
            .i("profile %s version %s: %s -> %s", activation.uuid, activation.sha.take(12), outcome, finish)
        if (finish == VersionStore.Finish.Restored) {
            val text = restoredText(result.error.orEmpty())
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun status(context: Context, uuid: UUID): Info =
        store(context).status(uuid.toString(), configOf(context, uuid))

    /** Puts the LKG back as `config.yaml` (a running active profile reloads); false without one. */
    fun restoreLkg(context: Context, uuid: UUID): Boolean {
        val restored = store(context).restoreLkg(uuid.toString(), configOf(context, uuid))
        if (restored) {
            context.sendProfileChanged(uuid, affectsRuntime = ServiceStore().activeProfile == uuid)
        }
        return restored
    }

    fun clearFailure(context: Context, uuid: UUID) = store(context).clearFailure(uuid.toString())

    /** Refuses to commit [stagedConfig] when it is the content whose last start failed. */
    fun rejectKnownBad(context: Context, uuid: UUID, stagedConfig: File) {
        check(!store(context).isKnownBad(uuid.toString(), stagedConfig)) { KNOWN_BAD }
    }

    fun forget(context: Context, uuid: UUID) = store(context).forget(uuid.toString())

    /** Profiles with version state, so state of deleted profiles can be dropped. */
    fun trackedProfiles(context: Context): List<UUID> =
        store(context).tracked().mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    internal fun outcomeOf(
        activation: Activation,
        result: RuntimeOperationResult,
        snapshot: RuntimeSnapshot,
    ): VersionStore.Outcome =
        when {
            !result.success -> VersionStore.Outcome.Failed(result.error ?: "start failed")
            snapshot.phase == RuntimePhase.Running && snapshot.profileUuid == activation.uuid ->
                VersionStore.Outcome.Succeeded
            // runGuarded reports an interrupted start/reload as success: no proof either way.
            else -> VersionStore.Outcome.Inconclusive
        }

    private fun store(context: Context) = VersionStore(context.filesDir.resolve("profile-versions"))

    private fun configOf(context: Context, uuid: UUID) =
        context.importedDir.resolve(uuid.toString()).resolve(ProfileCommit.CONFIG)

    private const val TAG = "ProfileVersions"
}

/** The files behind [ProfileVersions]; plain JVM code, unit tested on the host. */
internal class VersionStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data object Succeeded : Outcome

        data class Failed(val error: String) : Outcome

        data object Inconclusive : Outcome
    }

    enum class Finish {
        Promoted,
        Unchanged,
        Restored,
        Recorded,
        /** The config changed during the call (a newer commit): its own start decides. */
        Ignored,
    }

    @Serializable
    data class State(
        val verifiedSha: String? = null,
        val verifiedAt: Long = 0,
        val lastFailure: ProfileVersions.Failure? = null,
    )

    fun finish(activation: ProfileVersions.Activation, outcome: Outcome): Finish {
        if (outcome == Outcome.Inconclusive) return Finish.Ignored
        return synchronized(ProfileCommit.lock) {
            val config = activation.config
            val sha = activation.sha
            if (shaOf(config) != sha) return@synchronized Finish.Ignored
            val uuid = activation.uuid
            val state = read(uuid)
            val lkg = lkgFile(uuid)
            when (outcome) {
                Outcome.Succeeded -> {
                    if (state.verifiedSha == sha && shaOf(lkg) == sha) {
                        // It started again: an earlier failure of this very version is moot.
                        if (state.lastFailure?.sha == sha) write(uuid, state.copy(lastFailure = null))
                        Finish.Unchanged
                    } else {
                        AtomicFiles.copy(config, lkg)
                        write(
                            uuid,
                            state.copy(
                                verifiedSha = sha,
                                verifiedAt = clock(),
                                lastFailure = state.lastFailure?.takeUnless { it.sha == sha },
                            ),
                        )
                        Finish.Promoted
                    }
                }

                is Outcome.Failed -> {
                    val pending = state.verifiedSha != sha
                    if (pending && lkg.isFile) {
                        AtomicFiles.copy(lkg, config, lastModified = clock())
                        write(uuid, state.copy(lastFailure = failure(sha, outcome, restored = true)))
                        Finish.Restored
                    } else {
                        // Verified versions failing point elsewhere (overrides, VPN permission,
                        // a core crash); without an LKG there is nothing to go back to.
                        write(uuid, state.copy(lastFailure = failure(sha, outcome, restored = false)))
                        Finish.Recorded
                    }
                }

                Outcome.Inconclusive -> Finish.Ignored
            }
        }
    }

    fun status(uuid: String, config: File): ProfileVersions.Info {
        val state = read(uuid)
        val current = shaOf(config)
        val failure = state.lastFailure
        val status =
            when {
                current == null -> ProfileVersions.Status.None
                current != state.verifiedSha -> ProfileVersions.Status.Pending
                failure != null && failure.restored && failure.at >= state.verifiedAt ->
                    ProfileVersions.Status.Restored
                else -> ProfileVersions.Status.Verified
            }
        return ProfileVersions.Info(status, failure, lkgFile(uuid).isFile)
    }

    fun restoreLkg(uuid: String, config: File): Boolean =
        synchronized(ProfileCommit.lock) {
            val lkg = lkgFile(uuid)
            val profileDir = config.parentFile
            if (!lkg.isFile || profileDir == null || !profileDir.isDirectory) return@synchronized false
            if (shaOf(config) == shaOf(lkg)) return@synchronized false
            AtomicFiles.copy(lkg, config, lastModified = clock())
            true
        }

    fun clearFailure(uuid: String) {
        synchronized(ProfileCommit.lock) {
            val state = read(uuid)
            if (state.lastFailure != null) write(uuid, state.copy(lastFailure = null))
        }
    }

    /** The staged content is the one whose last start failed (and never started since). */
    fun isKnownBad(uuid: String, stagedConfig: File): Boolean {
        val state = read(uuid)
        val failure = state.lastFailure ?: return false
        val sha = shaOf(stagedConfig) ?: return false
        return failure.sha == sha && state.verifiedSha != sha
    }

    fun forget(uuid: String) {
        root.resolve(uuid).deleteRecursively()
    }

    fun tracked(): List<String> =
        root.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }

    private fun failure(sha: String, outcome: Outcome.Failed, restored: Boolean) =
        ProfileVersions.Failure(sha = sha, error = outcome.error, at = clock(), restored = restored)

    private fun lkgFile(uuid: String) = root.resolve(uuid).resolve(LKG)

    private fun stateFile(uuid: String) = root.resolve(uuid).resolve(STATE)

    private fun read(uuid: String): State {
        val file = stateFile(uuid)
        if (!file.isFile) return State()
        return runCatching { json.decodeFromString(State.serializer(), file.readText()) }
            .getOrElse { State() }
    }

    private fun write(uuid: String, state: State) {
        AtomicFiles.writeText(stateFile(uuid), json.encodeToString(State.serializer(), state))
    }

    companion object {
        private const val STATE = "state.json"
        private const val LKG = "lkg.yaml"
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }

        fun shaOf(file: File): String? {
            if (!file.isFile) return null
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
