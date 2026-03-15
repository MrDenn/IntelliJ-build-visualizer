package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
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
        val myToolWindow = MyToolWindow(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * UI in the form of directed graph for the Build Visualizer tool window.
     *
     * Queries the project's module dependency graph via [buildDependencyGraph] and renders
     * it as a directed graph using JGraphX inside a [DependencyGraphPanel].
     *
     * @param project the currently open IntelliJ [Project], used to query module structure
     */
    class MyToolWindow(project: Project) {

        private val content = SimpleToolWindowPanel(true).apply {
            val graph = buildDependencyGraph(project)
            val graphPanel = DependencyGraphPanel(graph)
            add(graphPanel, BorderLayout.CENTER)
        }

        /**
         * Returns the root panel to be registered as tool window content.
         *
         * Called by [MyToolWindowFactory.createToolWindowContent] immediately after
         * construction. The returned panel is owned by IntelliJ's content manager
         * after that point.
         */
        fun getContent(): SimpleToolWindowPanel = content
    }
}
