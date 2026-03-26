package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import kotlin.time.Duration.Companion.milliseconds

class BuildVisualizerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val buildVisualizerToolWindow = BuildVisualizerToolWindow(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(buildVisualizerToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * UI controller for the Build Visualizer tool window.
 *
 * Renders the module dependency graph via [DependencyGraphPanel], auto-rebuilds on
 * Gradle sync, and applies build status coloring from [BuildStatusService].
 * On open, catches up with any build statuses accumulated while the window was closed.
 */
class BuildVisualizerToolWindow(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val graphPanel: DependencyGraphPanel
    private val content: SimpleToolWindowPanel
    private val cs = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    private var rebuildJob: Job? = null
    private val buildStatusService = project.getService(BuildStatusService::class.java)

    private val uiListener = BuildStatusService.UiListener { statuses ->
        graphPanel.applyBuildStatuses(statuses)
    }

    init {
        Disposer.register(parentDisposable, this)

        graphPanel = DependencyGraphPanel(buildDependencyGraph(project))
        content = SimpleToolWindowPanel(true).apply {
            add(graphPanel, BorderLayout.CENTER)
        }

        buildStatusService.addUiListener(uiListener)
        graphPanel.applyBuildStatuses(buildStatusService.getStatuses())

        // Rebuild the graph on Gradle sync; debounced because a single sync fires many events
        project.messageBus.connect(this).subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    if (event.isCausedByFileTypesChange) return
                    scheduleRebuild()
                }
            }
        )
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        rebuildJob = cs.launch {
            delay(DEBOUNCE_DELAY_MS.toLong().milliseconds)
            rebuild()
        }
    }

    private fun rebuild() {
        graphPanel.rebuild(buildDependencyGraph(project))
        // New cells start with default styles — reapply last-known statuses
        graphPanel.applyBuildStatuses(buildStatusService.getStatuses())
    }

    fun getContent(): SimpleToolWindowPanel = content

    override fun dispose() {
        cs.cancel()
        buildStatusService.removeUiListener(uiListener)
    }

    companion object {
        private const val DEBOUNCE_DELAY_MS = 500
    }
}
