package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.milliseconds

class BuildVisualizerToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * Only show the tool window if the project has at least one linked Gradle module
     */
    override suspend fun isApplicableAsync(project: Project) =
        GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()

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

    private var graphPanel: DependencyGraphPanel? = null
    private val content: SimpleToolWindowPanel
    private val cs = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    private var rebuildJob: Job? = null
    private val buildStatusService = project.getService(BuildStatusService::class.java)

    private val statusLabel = JLabel("Waiting for Gradle to load all modules\u2026", SwingConstants.CENTER).apply {
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getInactiveTextColor()
    }

    private val uiListener = BuildStatusService.UiListener { statuses ->
        graphPanel?.applyBuildStatuses(statuses)
    }

    init {
        Disposer.register(parentDisposable, this)

        val graph = buildDependencyGraph(project)
        content = SimpleToolWindowPanel(true)

        // Dumb mode "Wating for Gradle..." message
        if (graph.vertexSet().isEmpty()) {
            content.add(statusLabel, BorderLayout.CENTER)
        } else {
            showGraph(graph)
        }

        buildStatusService.addUiListener(uiListener)
        graphPanel?.applyBuildStatuses(buildStatusService.getStatuses())

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

    private fun showGraph(graph: org.jgrapht.Graph<ModuleNode, org.jgrapht.graph.DefaultEdge>) {
        val panel = DependencyGraphPanel(graph)
        graphPanel = panel
        content.remove(statusLabel)
        content.add(panel, BorderLayout.CENTER)
        content.revalidate()
        content.repaint()
    }

    private fun rebuild() {
        val graph = buildDependencyGraph(project)
        if (graph.vertexSet().isEmpty()) return
        val existing = graphPanel
        if (existing != null) {
            existing.rebuild(graph)
        } else {
            showGraph(graph)
        }
        // New cells start with default styles — reapply last-known statuses
        graphPanel?.applyBuildStatuses(buildStatusService.getStatuses())
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
