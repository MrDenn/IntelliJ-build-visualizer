package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
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
     * Placeholder UI for the Build Visualizer tool window.
     *
     * Queries the project's module dependency graph via [buildDependencyGraph] and renders
     * it as a plain-text adjacency list inside a scrollable text area. This is a diagnostic
     * view used to verify that module and dependency data is being read correctly before
     * a proper graph visualisation is implemented.
     *
     * @param project the currently open IntelliJ [Project], used to query module structure
     */
    class MyToolWindow(project: Project) {

        // SimpleToolWindowPanel is used instead of JBPanel because of strange behavior when combined
        // with JBScrollPane. Since this is a temporary solution, this will be reworked later anyway.
        private val content = SimpleToolWindowPanel(true).apply {
            val graph = buildDependencyGraph(project)

            // Format the adjacency list as human-readable text: one module per block,
            // listing each direct dependency by name, or "(none)" if there are none.
            val sb = StringBuilder()
            for ((node, deps) in graph) {
                sb.appendLine("${node.name} depends on:")
                if (deps.isEmpty()) {
                    sb.appendLine("  (none)")
                } else {
                    deps.forEach { dep -> sb.appendLine("  - ${dep.name}") }
                }
                sb.appendLine()
            }

            // wrapStyleWord ensures wrapping occurs at word boundaries rather than
            // mid-word, which matters for long fully-qualified module names.
            val textArea = JBTextArea(sb.toString()).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true  // wrap at word boundaries, not mid-word
            }

            add(JBScrollPane(textArea), BorderLayout.CENTER)
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