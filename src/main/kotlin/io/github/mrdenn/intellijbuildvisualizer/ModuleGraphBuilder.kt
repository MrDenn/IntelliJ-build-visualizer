package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

/**
 * Suffix that IntelliJ appends to the module representing a Gradle source set's
 * main compilation unit. IntelliJ's module-per-source-set architecture splits
 * every Gradle subproject into multiple IntelliJ modules (e.g. "app", "app.main",
 * "app.test"). Only the ".main" modules correspond to production source sets -
 * the actual compilation units whose changes trigger downstream recompilation.
 */
private const val MAIN_SOURCE_SET_SUFFIX = ".main"

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

    // Filter to main source modules only and build a lookup map
    // from the raw Module object to a ModuleNode (display name + Gradle path).
    // This map doubles as the dependency lookup when resolving edges.
    val mainModules = allModules
        .filter { it.name.endsWith(MAIN_SOURCE_SET_SUFFIX) }
        .associateWith { module ->
            ModuleNode(
                displayName = module.name.removeSuffix(MAIN_SOURCE_SET_SUFFIX),
                gradleProjectPath = ExternalSystemApiUtil.getExternalProjectId(module)
            )
        }

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
