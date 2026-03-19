package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm
import java.awt.BorderLayout

/**
 * Entry point for the Build Visualizer tool window.
 *
 * Registered in `plugin.xml` via the `com.intellij.toolWindow` extension point.
 * IntelliJ calls [createToolWindowContent] lazily the first time the tool window
 * is opened, passing in the current [Project] and the [ToolWindow] frame to populate.
 */
class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    /**
     * Constructs the tool window UI and registers it with IntelliJ's content manager.
     *
     * Creates a [MyToolWindow] for the given [project], wraps its root panel in a
     * [com.intellij.ui.content.Content] object, and adds it to [toolWindow]'s
     * content manager. After this call, IntelliJ owns rendering of the panel.
     *
     * @param project the currently open project, passed through to [MyToolWindow]
     * @param toolWindow the tool window frame provided by the IntelliJ platform
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * UI controller for the Build Visualizer tool window.
 *
 * Queries the project's module dependency graph via [buildDependencyGraph] and renders
 * it as a directed graph using JGraphX inside a [DependencyGraphPanel]. Subscribes to
 * [ModuleRootListener] so the graph is automatically rebuilt when modules are added,
 * removed, or their dependencies change (e.g. after a Gradle sync).
 *
 * @param project the currently open IntelliJ [Project], used to query module structure
 * @param parentDisposable lifecycle anchor - typically [ToolWindow.getDisposable];
 *   when disposed, the message bus subscription and debounce alarm are cleaned up
 */
class MyToolWindow(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val graphPanel: DependencyGraphPanel
    private val content: SimpleToolWindowPanel
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        Disposer.register(parentDisposable, this)

        // Setup dependency graph panel
        graphPanel = DependencyGraphPanel(buildDependencyGraph(project))
        content = SimpleToolWindowPanel(true).apply {
            add(graphPanel, BorderLayout.CENTER)
        }

        // Register a listener for Gradle updates
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

    /**
     * Schedules a [rebuild] after [DEBOUNCE_DELAY_MS], cancelling any previously
     * scheduled request first.
     *
     * A single Gradle sync fires many [ModuleRootListener] events in rapid succession,
     * and debouncing combines these into a single rebuild once the burst has settled.
     */
    private fun scheduleRebuild() {
        alarm.cancelAllRequests()
        alarm.addRequest(::rebuild, DEBOUNCE_DELAY_MS)
    }

    /**
     * Re-queries the project's module graph and pushes the result to [graphPanel].
     *
     * Runs on the EDT (guaranteed by [Alarm.ThreadToUse.SWING_THREAD]) so all
     * downstream mxGraph mutations are thread-safe.
     */
    private fun rebuild() {
        graphPanel.rebuild(buildDependencyGraph(project))
    }

    /**
     * Returns the root panel to be registered as tool window content.
     *
     * Called by [MyToolWindowFactory.createToolWindowContent] immediately after
     * construction. The returned panel is owned by IntelliJ's content manager
     * after that point.
     */
    fun getContent(): SimpleToolWindowPanel = content

    /**
     * No-op: [MyToolWindow] owns no resources directly.
     *
     * Cleanup is handled automatically via the [Disposer] tree - the [alarm] and
     * message connection are both registered as children of `this`, so they are
     * disposed when IntelliJ disposes the parent, [ToolWindow].
     */
    override fun dispose() {}

    // Companion object for storing constants related to MyToolWindow.
    // (This could be a local or global variable, but a companion object keeps it neatly scoped within the class)
    companion object {
        private const val DEBOUNCE_DELAY_MS = 500
    }
}
