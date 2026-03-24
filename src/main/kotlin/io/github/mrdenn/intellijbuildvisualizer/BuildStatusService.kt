package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

/**
 * Project-level service that collects per-module build outcomes from Gradle.
 *
 * Registered as a lightweight project service in `plugin.xml`, so it is available
 * as soon as the project opens — independent of whether the tool window has been
 * opened. This ensures build events are never missed due to lazy tool window init.
 *
 * Owns:
 * - A Gradle-project-path-to-[ModuleNode] index, kept fresh via [ModuleRootListener]
 * - A [BuildStatusCollector] registered with [ExternalSystemProgressNotificationManager]
 * - A set of UI listeners notified (on the EDT) whenever a build finishes
 */
@Service(Service.Level.PROJECT)
class BuildStatusService(private val project: Project) : Disposable {

    /** Notified on the EDT after each build finishes. */
    fun interface UiListener {
        fun onBuildStatusesAvailable(statuses: Map<ModuleNode, BuildStatus>)
    }

    private val uiListeners = mutableListOf<UiListener>()

    private val statusLock = Any()

    /**
     * Persistent map of the last-known build status for each module, accumulated
     * across all builds since the project was opened. Entries are only replaced,
     * never removed, so the graph always reflects the most recent outcome per module.
     *
     * Written on background threads (under [statusLock]) by [notifyUiListeners];
     * read on the EDT by [getStatuses] and [MyToolWindow.rebuild].
     */
    private val accumulatedStatuses = mutableMapOf<ModuleNode, BuildStatus>()

    @Volatile
    private var gradlePathIndex: Map<String, ModuleNode> = buildPathIndex()

    private val collector = BuildStatusCollector(
        pathIndexProvider = { gradlePathIndex },
        onBuildFinished = { notifyUiListeners() }
    )

    init {
        // Register build event listener for the lifetime of this service
        ExternalSystemProgressNotificationManager.getInstance()
            .addNotificationListener(collector, this)

        // Keep path index fresh when modules change (e.g. after Gradle sync)
        project.messageBus.connect(this).subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    if (event.isCausedByFileTypesChange) return
                    gradlePathIndex = buildPathIndex()
                }
            }
        )
    }

    /**
     * Returns a snapshot of the last-known build status for every module seen so far.
     *
     * Non-destructive: the accumulated map is not cleared. Safe to call at any time,
     * including on tool window open for catch-up and after every rebuild.
     */
    fun getStatuses(): Map<ModuleNode, BuildStatus> {
        synchronized(statusLock) { return HashMap(accumulatedStatuses) }
    }

    /** Registers [listener] to be notified on the EDT after each build finishes. */
    fun addUiListener(listener: UiListener) {
        synchronized(uiListeners) { uiListeners.add(listener) }
    }

    /** Removes a previously registered [listener]. */
    fun removeUiListener(listener: UiListener) {
        synchronized(uiListeners) { uiListeners.remove(listener) }
    }

    private fun notifyUiListeners() {
        val newStatuses = collector.drainStatuses()
        if (newStatuses.isEmpty()) return
        // Merge new results into the persistent map and capture a snapshot for the UI.
        // putAll replaces per-module across builds; within-build maxOf is already
        // handled by BuildStatusCollector before the drain.
        val snapshot: Map<ModuleNode, BuildStatus>
        synchronized(statusLock) {
            accumulatedStatuses.putAll(newStatuses)
            snapshot = HashMap(accumulatedStatuses)
        }
        ApplicationManager.getApplication().invokeLater {
            val listenerSnapshot = synchronized(uiListeners) { uiListeners.toList() }
            listenerSnapshot.forEach { it.onBuildStatusesAvailable(snapshot) }
        }
    }

    /**
     * Builds a Gradle-project-path -> [ModuleNode] index from the current project modules.
     *
     * Uses [ModuleManager] only — no edge traversal via [com.intellij.openapi.roots.ModuleRootManager].
     * Modules without a Gradle project path are excluded (gradleProjectPath == null).
     */
    private fun buildPathIndex(): Map<String, ModuleNode> =
        ModuleManager.getInstance(project).modules
            .mapNotNull { it.toModuleNode() }
            .mapNotNull { node -> node.gradleProjectPath?.let { it to node } }
            .toMap()

    /**
     * No-op: all resources are tied to this service's [Disposable] lifetime.
     * The [ExternalSystemProgressNotificationManager] listener and message bus
     * connection are auto-cleaned when IntelliJ disposes this service.
     */
    override fun dispose() {}
}
