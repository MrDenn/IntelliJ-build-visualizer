package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

/**
 * IntelliJ appends this suffix to module names for the main source set.
 * Every Gradle subproject is split into multiple IntelliJ modules
 * (e.g. "app", "app.main", "app.test"). Only ".main" modules are production
 * compilation units whose changes trigger downstream recompilation.
 */
internal const val MAIN_MODULE_SUFFIX = ".main"

/**
 * IntelliJ appends this suffix to the external-system project ID for the main
 * source set (e.g. `ExternalSystemApiUtil.getExternalProjectId` returns
 * `:core-utils:main` for the `core-utils.main` IntelliJ module).
 * The real Gradle project path is `:core-utils` — this suffix is an
 * IntelliJ-internal artifact of the module-per-source-set convention.
 */
internal const val MAIN_EXTERNAL_ID_SUFFIX = ":main"

/**
 * Converts this IntelliJ [Module] to a [ModuleNode], or returns null if the
 * module is not a main source set module.
 *
 * Strips [MAIN_MODULE_SUFFIX] from the display name and [MAIN_EXTERNAL_ID_SUFFIX]
 * from the Gradle project path so that both fields reflect real Gradle concepts
 * rather than IntelliJ's internal module-per-source-set naming.
 */
internal fun Module.toModuleNode(): ModuleNode? {
    if (!name.endsWith(MAIN_MODULE_SUFFIX)) return null
    return ModuleNode(
        displayName = name.removeSuffix(MAIN_MODULE_SUFFIX),
        gradleProjectPath = ExternalSystemApiUtil.getExternalProjectId(this)
            ?.removeSuffix(MAIN_EXTERNAL_ID_SUFFIX)
    )
}

/**
 * Builds a directed graph of inter-module compilation dependencies.
 *
 * Only main source set modules are included — umbrella, test, and other source sets
 * are filtered out since they don't participate in the recompilation cascades.
 * An edge from A to B means "A depends on B".
 */
fun buildDependencyGraph(project: Project): Graph<ModuleNode, DefaultEdge> {
    val graph = DefaultDirectedGraph<ModuleNode, DefaultEdge>(DefaultEdge::class.java)
    val allModules = ModuleManager.getInstance(project).modules

    val mainModules = allModules
        .mapNotNull { module -> module.toModuleNode()?.let { node -> module to node } }
        .toMap()

    for (moduleNode in mainModules.values) {
        graph.addVertex(moduleNode)
    }

    for ((module, moduleNode) in mainModules) {
        for (dep in ModuleRootManager.getInstance(module).dependencies) {
            val depNode = mainModules[dep]
            if (depNode != null) {
                graph.addEdge(moduleNode, depNode)
            }
        }
    }

    return graph
}
