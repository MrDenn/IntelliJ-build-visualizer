package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.SkippedResult
import com.intellij.build.events.SuccessResult
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent

/**
 * Collects per-module build outcomes from Gradle build events.
 *
 * Implements [ExternalSystemTaskNotificationListener] and is registered with
 * [com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager],
 * which fires for all Gradle executions regardless of whether any UI is open.
 *
 * Events are filtered to compile tasks only, mapped to [ModuleNode] via
 * [pathIndexProvider] (the current Gradle-path -> ModuleNode index from
 * [BuildStatusService]), and accumulated in a synchronized map.
 * When the build ends, [onBuildFinished] is called so the service can
 * flush accumulated state to registered UI listeners.
 *
 * Thread safety: [onStatusChange] and [onEnd] are called on background threads;
 * [drainStatuses] is called on the EDT. Both sides synchronize on [lock].
 *
 * @param pathIndexProvider returns the current path-to-node index; called at event
 *   time so the collector always uses the latest index after a Gradle sync
 * @param onBuildFinished called when the Gradle execution ends
 */
class BuildStatusCollector(
    private val pathIndexProvider: () -> Map<String, ModuleNode>,
    private val onBuildFinished: () -> Unit
) : ExternalSystemTaskNotificationListener {

    private val lock = Any()
    private val statusMap = mutableMapOf<ModuleNode, BuildStatus>()

    /**
     * Receives all Gradle build notifications. Unwraps [ExternalSystemBuildEvent]
     * to access typed [com.intellij.build.events.BuildEvent] instances.
     */
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        if (event !is ExternalSystemBuildEvent) return
        val buildEvent = event.buildEvent

        when (buildEvent) {
            is FinishBuildEvent -> onBuildFinished()
            is FinishEvent -> {
                val taskPath = buildEvent.message
                if (!isCompileTask(taskPath)) return

                val gradlePath = extractGradlePath(taskPath) ?: return
                val node = pathIndexProvider()[gradlePath] ?: return
                val status = classifyResult(buildEvent.result)
                recordStatus(node, status)
            }
        }
    }

    /**
     * Called when the entire Gradle execution ends. Reliable fallback in case
     * [FinishBuildEvent] is not delivered via [onStatusChange].
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onEnd(id: ExternalSystemTaskId) {
        onBuildFinished()
    }

    /**
     * Atomically snapshots and clears all accumulated statuses.
     *
     * Returns statuses collected since the last drain. The internal map is empty
     * after this call. Phase 3 reuses this for periodic flushing during builds.
     */
    fun drainStatuses(): Map<ModuleNode, BuildStatus> {
        synchronized(lock) {
            val snapshot = HashMap(statusMap)
            statusMap.clear()
            return snapshot
        }
    }

    /**
     * Records a build status for a module, using [maxOf] to resolve conflicts
     * when multiple compile tasks fire for the same module (e.g. compileKotlin
     * followed by compileJava). [BuildStatus.FAILED] always takes priority.
     */
    private fun recordStatus(node: ModuleNode, status: BuildStatus) {
        synchronized(lock) {
            statusMap.merge(node, status) { old, new -> maxOf(old, new) }
        }
    }

    private fun isCompileTask(taskPath: String): Boolean {
        val taskName = taskPath.substringAfterLast(':')
        return taskName.startsWith("compile") &&
                (taskName.endsWith("Kotlin") || taskName.endsWith("Java"))
    }

    /**
     * Extracts the Gradle project path from a full task path.
     *
     * Example: `:core-utils:compileKotlin` -> `:core-utils`
     * Root project task: `:compileKotlin` -> `:`
     */
    private fun extractGradlePath(taskPath: String): String? {
        val lastColon = taskPath.lastIndexOf(':')
        if (lastColon < 0) return null
        if (lastColon == 0) return ":"
        return taskPath.substring(0, lastColon)
    }

    private fun classifyResult(result: EventResult): BuildStatus {
        return when (result) {
            is FailureResult -> BuildStatus.FAILED
            is SkippedResult -> BuildStatus.UP_TO_DATE
            is SuccessResult -> if (result.isUpToDate) BuildStatus.UP_TO_DATE else BuildStatus.COMPILED
            else -> BuildStatus.COMPILED
        }
    }
}
