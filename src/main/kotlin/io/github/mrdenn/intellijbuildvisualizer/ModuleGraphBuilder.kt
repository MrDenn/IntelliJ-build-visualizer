package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

/**
 * Builds a JGraphT directed graph of inter-module dependencies for the given [project].
 *
 * Each vertex is a module name (String). A directed edge from A to B means
 * "module A depends on module B". Only dependencies between modules within
 * the same project are included — library, SDK, and transitive dependencies
 * are excluded.
 *
 * @param project the currently open IntelliJ [Project]
 * @return a directed graph where vertices are module names and edges represent dependencies
 */
fun buildDependencyGraph(project: Project): Graph<String, DefaultEdge> {
    val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
    val modules = ModuleManager.getInstance(project).modules

    // Index module names for O(1) membership checks when resolving dependencies
    val moduleNames = modules.associate { it to it.name }

    // Add all modules as vertices
    for (module in modules) {
        graph.addVertex(moduleNames[module])
    }

    // Add dependency edges
    for (module in modules) {
        val moduleName = moduleNames[module] ?: continue
        for (dep in ModuleRootManager.getInstance(module).dependencies) {
            val depName = moduleNames[dep]
            // Only add edges to modules within the project (skip unloaded/synthetic modules)
            if (depName != null) {
                graph.addEdge(moduleName, depName)
            }
        }
    }

    return graph
}
