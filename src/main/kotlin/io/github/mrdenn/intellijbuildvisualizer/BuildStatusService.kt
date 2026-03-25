package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.util.Alarm

/**
 * Project-level service that collects per-module build outcomes from Gradle.
 *
 * Lives for the project lifetime, independent of the tool window, so build
 * events are captured even before the tool window is first opened.
 */
@Service(Service.Level.PROJECT)
class BuildStatusService(private val project: Project) : Disposable {

    /**
     * Notified on the EDT when build statuses change (during and after builds).
     */
    fun interface UiListener {
        fun onBuildStatusesAvailable(statuses: Map<ModuleNode, BuildStatus>)
    }

    private val uiListeners = mutableListOf<UiListener>()

    private val statusLock = Any()

    /**
     * Last-known status per module, accumulated across builds. Guarded by [statusLock].
     */
    private val accumulatedStatuses = mutableMapOf<ModuleNode, BuildStatus>()

    /**
     * Periodically flushes accumulated statuses to the UI during a build.
     */
    private val flushAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    @Volatile private var buildActive = false

    @Volatile
    private var gradlePathIndex: Map<String, ModuleNode> = buildPathIndex()

    private val collector = BuildStatusCollector(
        pathIndexProvider = { gradlePathIndex },
        onBuildStarted = { onBuildStarted() },
        onBuildFinished = { onBuildFinished() }
    )

    init {
        ExternalSystemProgressNotificationManager.getInstance()
            .addNotificationListener(collector, this)

        // Rebuild path index on Gradle sync so new/removed modules are picked up
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
     * Non-destructive snapshot of the last-known build status for every module.
     */
    fun getStatuses(): Map<ModuleNode, BuildStatus> {
        synchronized(statusLock) { return HashMap(accumulatedStatuses) }
    }

    /**
     * Registers [listener] to be notified on the EDT after each build finishes.
     */
    fun addUiListener(listener: UiListener) {
        synchronized(uiListeners) { uiListeners.add(listener) }
    }

    /**
     * Removes a previously registered [listener].
     */
    fun removeUiListener(listener: UiListener) {
        synchronized(uiListeners) { uiListeners.remove(listener) }
    }

    private fun onBuildStarted() {
        buildActive = true
        schedulePeriodicFlush()
    }

    private fun onBuildFinished() {
        buildActive = false
        flushAlarm.cancelAllRequests()
        flushToUi()
    }

    /**
     * Drains the collector, merges into [accumulatedStatuses], and notifies UI listeners.
     * Called both periodically during a build and once after it finishes.
     */
    private fun flushToUi() {
        val newStatuses = collector.drainStatuses()
        if (newStatuses.isEmpty()) return
        // putAll replaces per-module across builds; within-build maxOf is handled by the collector
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

    private fun schedulePeriodicFlush() {
        flushAlarm.addRequest({
            flushToUi()
            if (buildActive) schedulePeriodicFlush()
        }, FLUSH_INTERVAL_MS)
    }

    /**
     * Builds a Gradle-project-path -> [ModuleNode] index from main source set modules.
     */
    private fun buildPathIndex(): Map<String, ModuleNode> =
        ModuleManager.getInstance(project).modules
            .mapNotNull { it.toModuleNode() }
            .mapNotNull { node -> node.gradleProjectPath?.let { it to node } }
            .toMap()

    override fun dispose() {}

    companion object {
        private const val FLUSH_INTERVAL_MS = 100
    }
}
