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
 * Builds a JGraphT directed graph of inter-module dependencies for the given [project].
 *
 * Only modules representing Gradle **main source sets** (names ending in ".main")
 * are included. Umbrella modules (no suffix), test modules (".test"), and other
 * source sets are filtered out, since they are not part of the production
 * dependency chain that causes recompilation cascades.
 *
 * Each vertex is a [ModuleNode] carrying both a human-readable display name and
 * the Gradle project path (for task-to-vertex mapping in later phases).
 * A directed edge from A to B means "module A depends on module B".
 *
 * @param project the currently open IntelliJ [Project]
 * @return a directed graph where vertices are [ModuleNode]s and edges
 *         represent compilation dependencies
 */
fun buildDependencyGraph(project: Project): Graph<ModuleNode, DefaultEdge> {
    val graph = DefaultDirectedGraph<ModuleNode, DefaultEdge>(DefaultEdge::class.java)
    val allModules = ModuleManager.getInstance(project).modules

    // Filter to main source set modules and build a lookup map from the raw
    // Module object to a ModuleNode. This map doubles as the dependency lookup
    // when resolving edges below.
    val mainModules = allModules
        .mapNotNull { module -> module.toModuleNode()?.let { node -> module to node } }
        .toMap()

    // Add all main modules as vertices
    for (moduleNode in mainModules.values) {
        graph.addVertex(moduleNode)
    }

    // Add dependency edges between main modules
    for ((module, moduleNode) in mainModules) {
        for (dep in ModuleRootManager.getInstance(module).dependencies) {
            val depNode = mainModules[dep]
            // Only add edges to other main modules within the project
            if (depNode != null) {
                graph.addEdge(moduleNode, depNode)
            }
        }
    }

    return graph
}
