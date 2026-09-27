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

package com.github.yumeyucca.yumebox.nodesource

import android.content.Context
import com.github.yumeyucca.yumebox.core.model.Provider
import com.github.yumeyucca.yumebox.core.util.NodeSourceCopies
import com.github.yumeyucca.yumebox.core.util.NodeSourceSync
import com.github.yumeyucca.yumebox.data.controller.ActiveProfileOverrideReloader
import com.github.yumeyucca.yumebox.data.model.OverrideConfig
import com.github.yumeyucca.yumebox.data.model.OverrideContentType
import com.github.yumeyucca.yumebox.data.model.OverrideMetadata
import com.github.yumeyucca.yumebox.data.model.ProfileBinding
import com.github.yumeyucca.yumebox.data.store.OverrideConfigStore
import com.github.yumeyucca.yumebox.data.store.ProfileBindingProvider
import com.github.yumeyucca.yumebox.data.store.RemoteControllerStore
import com.github.yumeyucca.yumebox.runtime.client.ProxyFacade
import com.github.yumeyucca.yumebox.runtime.client.access.RuntimeAccess
import com.github.yumeyucca.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** A subscription node source: its override, its template form, what the App knows of it. */
data class NodeSource(
    val config: OverrideConfig,
    val form: NodeSourceForm,
    val state: NodeSourceState,
    val boundProfileIds: Set<String>,
) {
    val id: String
        get() = config.id
}

/** A subscription downloaded and checked, before it becomes (or refreshes) a node source. */
class FetchedSource(
    val url: String,
    val bytes: ByteArray,
    val meta: SubscriptionMeta,
    val check: ContentCheck,
    val fetchedAt: Long,
) {
    fun info(): NodeSourceInfo = NodeSourceInfo.fromDownload(meta, check.nodeCount, fetchedAt)
}

/**
 * KimiNoBox: subscription node sources the way remote subscriptions work (docs/plan-a-round2.md
 * §2.1). The App downloads a source once, at import, into its master copy; each bound profile gets
 * a copy of the newest one before its core starts (the compile hook in runtime/service), so the
 * core does not download it again. After that the core keeps it fresh by its interval, and the App
 * reads the update time, traffic and expiry back from the core. A manual update goes to the core
 * while it runs the source, and is a direct download otherwise.
 */
class NodeSourceManager(
    private val context: Context,
    private val configStore: OverrideConfigStore,
    private val bindings: ProfileBindingProvider,
    private val reloader: ActiveProfileOverrideReloader,
    private val proxyFacade: ProxyFacade,
    private val downloader: NodeSourceDownloader,
    private val stateStore: NodeSourceStateStore,
    private val defaults: NewProfileDefaultsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val copyLock = Mutex()
    private val _updating = MutableStateFlow<Set<String>>(emptySet())
    private val _kernelNodeNames = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Override ids of the sources being updated right now. */
    val updating: StateFlow<Set<String>> = _updating.asStateFlow()

    /**
     * Node names per provider as the running core last reported them (prefix applied), for the
     * duplicate hint and the grouping by source.
     */
    val kernelNodeNames: StateFlow<Map<String, List<String>>> = _kernelNodeNames.asStateFlow()

    val sources: StateFlow<List<NodeSource>> =
        combine(configStore.getAllFlow(), bindings.getAllBindingsFlow(), stateStore.states) { configs, all, states ->
                configs.mapNotNull { config ->
                    val form = NodeSourceTemplate.parse(config.content) ?: return@mapNotNull null
                    NodeSource(
                        config = config,
                        form = form,
                        state = states[config.id] ?: NodeSourceState(),
                        boundProfileIds = all.filter { config.id in it.overrideIds }.map { it.profileId }.toSet(),
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Provider names of every node source (subscriptions and self-hosted) by override id. */
    private val namesById: StateFlow<Map<String, Set<String>>> =
        configStore.getAllFlow()
            .map { configs ->
                configs.filter { NodeTemplateKind.of(it.content) != null }
                    .associate { it.id to NodeTemplates.providerNames(it.content) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The names a node source can't take: those of all the others ([exceptId] is the one edited). */
    fun takenNames(exceptId: String?): Set<String> =
        namesById.value.filterKeys { it != exceptId }.values.flatten().toSet()

    init {
        // §2.1 item 6: the core updates by interval on its own; a newer update time than known
        // (read here every few minutes while the VPN runs) sends its copy to the other profiles.
        scope.launch {
            proxyFacade.isRunning.collectLatest { running ->
                while (running) {
                    delay(KERNEL_POLL_MS)
                    runCatching { refreshKernelInfo() }.onFailure { Timber.d(it, "Node source poll failed") }
                }
            }
        }
        // §2.1 item 6: when the VPN stops, the copy it ran on may be the newest; spread it.
        scope.launch {
            var ranProfileId: String? = null
            combine(proxyFacade.isRunning, proxyFacade.currentProfile) { running, profile -> running to profile }
                .collect { (running, profile) ->
                    if (running) {
                        ranProfileId = profile?.uuid?.toString()
                    } else if (ranProfileId != null) {
                        val stopped = ranProfileId
                        ranProfileId = null
                        sources.value.filter { stopped in it.boundProfileIds }.forEach { syncCopies(it, stopped) }
                    }
                }
        }
    }

    fun source(id: String): NodeSource? = sources.value.firstOrNull { it.id == id }

    /** Downloads and checks [url]; throws [NodeSourceDownloadException] with the full reason. */
    suspend fun fetch(url: String): FetchedSource {
        val downloaded = downloader.download(url.trim())
        val check = SubscriptionContent.inspect(downloaded.bytes)
        if (!check.ok) throw NodeSourceDownloadException(check.error ?: NodeText.CONTENT_UNRECOGNIZED)
        return FetchedSource(
            url = url.trim(),
            bytes = downloaded.bytes,
            meta = SubscriptionHeaders.parse { downloaded.headers[it.lowercase()] },
            check = check,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Saves a new source from what [fetched] downloaded, and binds it to [bindProfileId]. */
    suspend fun create(form: NodeSourceForm, fetched: FetchedSource, bindProfileId: String?): OverrideConfig =
        withContext(Dispatchers.IO) {
            NodeSourceCopies.write(fetched.bytes, NodeSourceCopies.master(context, form.pathId))
            val now = System.currentTimeMillis()
            val config =
                OverrideConfig(
                    id = OverrideMetadata.generateId(),
                    name = form.name,
                    contentType = OverrideContentType.Yaml,
                    content = NodeSourceTemplate.render(form),
                    createdAt = now,
                    updatedAt = now,
                )
            configStore.save(config)
            stateStore.update(config.id) { NodeSourceState(info = fetched.info()) }
            bindProfileId?.let { bind(config.id, it) }
            config
        }

    /**
     * Saves an edited source. [fetched] is required when the URL changed: the new URL gets a new
     * cache path, so its content has to be downloaded once again, like an import.
     */
    suspend fun save(source: NodeSource, form: NodeSourceForm, fetched: FetchedSource?) =
        withContext(Dispatchers.IO) {
            if (fetched != null) {
                NodeSourceCopies.write(fetched.bytes, NodeSourceCopies.master(context, form.pathId))
                stateStore.update(source.id) { NodeSourceState(info = fetched.info()) }
            }
            configStore.save(
                source.config.copy(name = form.name, content = NodeSourceTemplate.render(form), updatedAt = System.currentTimeMillis())
            )
            reloader.reapplyActiveProfileIfUsingOverride(source.id)
        }

    suspend fun bind(sourceId: String, profileId: String) =
        withContext(Dispatchers.IO) {
            bindings.addOverride(profileId, sourceId)
            reapplyIfActive(profileId)
        }

    suspend fun unbind(sourceId: String, profileId: String) =
        withContext(Dispatchers.IO) {
            bindings.removeOverride(profileId, sourceId)
            reapplyIfActive(profileId)
        }

    suspend fun delete(source: NodeSource) =
        withContext(Dispatchers.IO) {
            val active = activeProfileId()
            bindings.removeOverrideFromAllBindings(source.id)
            configStore.delete(source.id)
            stateStore.remove(source.id)
            if (active in source.boundProfileIds) reloader.reapplyActiveProfileOverride()
            // The reload has dropped the provider, so no core reads these any more.
            (profileCopies(source) + NodeSourceCopies.master(context, source.form.pathId)).forEach(File::delete)
        }

    /**
     * §2.1 item 5: through the core while it runs this source (its own download, its own rules),
     * a direct download into every copy otherwise. Throws with the full reason; the reason is
     * also kept as the source's last error.
     */
    suspend fun update(source: NodeSource) {
        _updating.value = _updating.value + source.id
        try {
            val runningProfileId = runningProfileId()
            if (runningProfileId != null && runningProfileId in source.boundProfileIds) {
                updateThroughKernel(source, runningProfileId)
            } else {
                updateDirectly(source)
            }
            stateStore.update(source.id) { it.copy(lastError = null) }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            stateStore.update(source.id) { it.copy(lastError = error.message) }
            throw error
        } finally {
            _updating.value = _updating.value - source.id
        }
    }

    private suspend fun updateThroughKernel(source: NodeSource, runningProfileId: String) {
        try {
            RuntimeAccess.connect(context)
            RuntimeAccess.core().updateProvider(Provider.Type.Proxy, source.form.name)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw NodeSourceDownloadException(
                NodeSourceDownloader.withoutUrls(error.message ?: error::class.java.simpleName) +
                    "\n" + NodeText.KERNEL_UPDATE_HINT,
                error,
            )
        }
        refreshKernelInfo()
        syncCopies(source, runningProfileId)
    }

    private suspend fun updateDirectly(source: NodeSource) =
        withContext(Dispatchers.IO) {
            val fetched = fetch(source.form.url)
            copyLock.withLock {
                // One write, then copies: every copy keeps the same time, so none looks newer.
                val master = NodeSourceCopies.master(context, source.form.pathId)
                NodeSourceCopies.write(fetched.bytes, master)
                profileCopies(source).forEach { NodeSourceCopies.copy(master, it) }
            }
            stateStore.update(source.id) { it.copy(info = fetched.info()) }
        }

    /**
     * §2.1 item 4: reads update time, node count, traffic and expiry of the running profile's
     * sources from the core. A newer update time than known means the core downloaded the source
     * by its interval, so its copy goes to the other profiles.
     */
    suspend fun refreshKernelInfo() {
        val runningProfileId = runningProfileId() ?: return
        val details =
            runCatching { CoreProcess.proxyProviderDetails(context) }
                .onFailure { Timber.d(it, "Provider details unavailable") }
                .getOrNull() ?: return
        _kernelNodeNames.value = details.associate { it.name to it.nodeNames }
        val byName = details.associateBy { it.name }
        val now = System.currentTimeMillis()
        sources.value.filter { runningProfileId in it.boundProfileIds }.forEach { source ->
            val detail = byName[source.form.name] ?: return@forEach
            val kernel =
                NodeSourceInfo(
                    updatedAt = detail.updatedAt,
                    upload = detail.upload,
                    download = detail.download,
                    total = detail.total,
                    expire = detail.expire,
                    nodeCount = detail.nodeNames.size,
                    fetchedAt = now,
                    origin = NodeSourceInfo.Origin.Kernel,
                )
            val previous = source.state.info
            stateStore.update(source.id) { it.copy(info = previous?.mergedWith(kernel) ?: kernel) }
            val known = previous?.updatedAt
            val reported = detail.updatedAt
            if (known != null && reported != null && reported > known) {
                syncCopies(source, runningProfileId)
            }
        }
    }

    /**
     * Makes the 「默认配置」 override the first time 「从节点源新建配置」 needs it, and never again:
     * once the user deletes it, it stays deleted (docs/plan-a-round4.md E3).
     */
    suspend fun ensureDefaultConfig() =
        withContext(Dispatchers.IO) {
            try {
                val exists = configStore.exists(NewProfileDefaults.DEFAULT_CONFIG_ID)
                if (NewProfileDefaults.makeDefaultConfig(defaults.defaultConfigMade.value, exists)) {
                    val acl4ssr = configStore.getById(NewProfileDefaults.ACL4SSR_ID)?.content
                    val now = System.currentTimeMillis()
                    configStore.save(
                        OverrideConfig(
                            id = NewProfileDefaults.DEFAULT_CONFIG_ID,
                            name = NewProfileDefaults.DEFAULT_CONFIG_NAME,
                            contentType = OverrideContentType.Yaml,
                            content = NewProfileDefaults.defaultConfig(acl4ssr),
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }
                defaults.defaultConfigMade.set(true)
            } catch (error: Exception) {
                // The page still opens without it; the next opening tries again
                if (error is kotlinx.coroutines.CancellationException) throw error
                Timber.w(error, "Could not make the default config override")
            }
        }

    /** Every override other than the subscription sources, for the profile's 覆写 section. */
    suspend fun otherOverrides(): List<OverrideConfig> =
        withContext(Dispatchers.IO) {
            configStore.getBuiltInConfigs() + configStore.getUserConfigs().filter { NodeSourceTemplate.parse(it.content) == null }
        }

    fun boundOverrideIds(profileId: String) = bindings.getBindingFlow(profileId)

    /**
     * Writes [profileId]'s whole override chain at once (a reorder, docs/plan-a-round3.md D5), and
     * applies it again if the profile is the running one.
     */
    suspend fun setOverrideChain(profileId: String, chain: List<String>) =
        withContext(Dispatchers.IO) {
            val binding = bindings.getBinding(profileId)
            bindings.setBinding(binding?.setOverrides(chain) ?: ProfileBinding.withOverrides(profileId, chain))
            reapplyIfActive(profileId)
        }

    /**
     * Provider names of [profileId]'s node sources (subscriptions and self-hosted) in chain order.
     * The core lists `include-all` providers by name whatever the chain says, so the proxy page
     * takes its section order from here.
     */
    fun providerOrder(profileId: String): Flow<List<String>> =
        combine(bindings.getBindingFlow(profileId), namesById) { binding, names ->
            binding?.overrideIds.orEmpty().flatMap { names[it].orEmpty() }
        }

    /** Binds or unbinds any override of [profileId]; a new one goes to the end of the chain. */
    suspend fun setOverrideBound(profileId: String, overrideId: String, bound: Boolean) =
        withContext(Dispatchers.IO) {
            if (bound) bindings.addOverride(profileId, overrideId) else bindings.removeOverride(profileId, overrideId)
            reapplyIfActive(profileId)
        }

    /** §2.1 item 6: the newest copy (the running profile's first on a tie) over the stale ones. */
    suspend fun syncCopies(source: NodeSource, preferredProfileId: String?) =
        withContext(Dispatchers.IO) {
            copyLock.withLock {
                val preferred = preferredProfileId?.let { copyOf(it, source) }
                val candidates =
                    (listOfNotNull(preferred) + NodeSourceCopies.master(context, source.form.pathId) + profileCopies(source))
                        .distinctBy(File::getCanonicalPath)
                val plan =
                    NodeSourceSync.plan(candidates.map { NodeSourceSync.Copy(it, it.takeIf(File::isFile)?.lastModified()) })
                        ?: return@withLock
                plan.to.forEach { target ->
                    runCatching { NodeSourceCopies.copy(plan.from, target) }
                        .onFailure { Timber.w(it, "Node source copy of %s not written", source.form.name) }
                }
                // A copy newer than the known update time was written by the core, possibly just
                // before the VPN stopped, when there is no core left to ask for the time.
                val newest = plan.from.lastModified()
                stateStore.update(source.id) { state ->
                    val info = state.info
                    val known = info?.updatedAt
                    if (info != null && known != null && newest > known) state.copy(info = info.copy(updatedAt = newest)) else state
                }
            }
        }

    private fun profileCopies(source: NodeSource): List<File> = source.boundProfileIds.map { copyOf(it, source) }

    private fun copyOf(profileId: String, source: NodeSource): File =
        NodeSourceCopies.profileCopy(NodeSourceCopies.profileDir(context, profileId), source.form.pathId)

    private suspend fun reapplyIfActive(profileId: String) {
        if (activeProfileId() == profileId) reloader.reapplyActiveProfileOverride()
    }

    private fun activeProfileId(): String? = proxyFacade.currentProfile.value?.uuid?.toString()

    /** The profile the local core runs, or null when the VPN is off or a remote core is in use. */
    fun runningProfileId(): String? =
        activeProfileId().takeIf { proxyFacade.isRunning.value && !RemoteControllerStore.isActive() }

    private companion object {
        const val KERNEL_POLL_MS = 5 * 60_000L
    }
}
