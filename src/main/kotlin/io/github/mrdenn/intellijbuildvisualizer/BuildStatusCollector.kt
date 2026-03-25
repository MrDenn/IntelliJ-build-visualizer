package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.SkippedResult
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.SuccessResult
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent

/**
 * Accumulates per-module build outcomes from Gradle compile task events.
 *
 * Thread safety: [onStatusChange] and [onEnd] run on background threads;
 * [drainStatuses] is called on the EDT. Both sides synchronize on [lock].
 *
 * @param pathIndexProvider called at event time to get the current Gradle-path-to-node
 *   index, so it always reflects the latest state after a Gradle sync
 * @param onBuildStarted called when a Gradle execution begins
 * @param onBuildFinished called when the Gradle execution ends
 */
class BuildStatusCollector(
    private val pathIndexProvider: () -> Map<String, ModuleNode>,
    private val onBuildStarted: () -> Unit,
    private val onBuildFinished: () -> Unit
) : ExternalSystemTaskNotificationListener {

    private val lock = Any()
    private val statusMap = mutableMapOf<ModuleNode, BuildStatus>()

    /**
     * [ExternalSystemBuildEvent] is `@ApiStatus.Experimental` but is the only API
     * that provides typed build events for all Gradle executions (see PLAN.md).
     */
    @Suppress("UnstableApiUsage")
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        if (event !is ExternalSystemBuildEvent) return
        val buildEvent = event.buildEvent

        // StartBuildEvent/FinishBuildEvent must precede StartEvent/FinishEvent
        // because the Build variants extend the base variants
        when (buildEvent) {
            is StartBuildEvent -> onBuildStarted()
            is FinishBuildEvent -> onBuildFinished()
            is StartEvent -> {
                val taskPath = buildEvent.message
                if (!isCompileTask(taskPath)) return
                val gradlePath = extractGradlePath(taskPath) ?: return
                val node = pathIndexProvider()[gradlePath] ?: return
                recordStatus(node, BuildStatus.COMPILING)
            }
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
     * Fallback in case [StartBuildEvent] is not delivered via [onStatusChange].
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onStart(id: ExternalSystemTaskId) {
        onBuildStarted()
    }

    /**
     * Fallback in case [FinishBuildEvent] is not delivered via [onStatusChange].
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onEnd(id: ExternalSystemTaskId) {
        onBuildFinished()
    }

    /**
     * Atomically snapshots and clears all accumulated statuses since the last drain.
     */
    fun drainStatuses(): Map<ModuleNode, BuildStatus> {
        synchronized(lock) {
            val snapshot = HashMap(statusMap)
            statusMap.clear()
            return snapshot
        }
    }

    /**
     * Uses [maxOf] so [BuildStatus.FAILED] wins when multiple tasks target the same module.
     */
    private fun recordStatus(node: ModuleNode, status: BuildStatus) {
        synchronized(lock) {
            statusMap.merge(node, status) { old, new -> maxOf(old, new) }
        }
    }

    private fun isCompileTask(taskPath: String): Boolean {
        val taskName = taskPath.substringAfterLast(':')
        return taskName.startsWith("compile") &&
                !taskName.startsWith("compileTest") &&
                (taskName.endsWith("Kotlin") || taskName.endsWith("Java"))
    }

    /**
     * Example: `:core-utils:compileKotlin` -> `:core-utils`, `:compileKotlin` -> `:`
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
