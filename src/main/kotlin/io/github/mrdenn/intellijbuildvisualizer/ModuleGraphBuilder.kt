package io.github.mrdenn.intellijbuildvisualizer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.project.Project

/**
 * Represents a single module in the project's dependency graph.
 *
 * Wraps IntelliJ's [Module] to pair it with display name
 * (and any additional data that might be needed in the future).
 *
 * @property name the module's display name as registered in IntelliJ's project model
 * @property module the underlying IntelliJ [Module] object, retained for later API calls
 *   (e.g. querying source roots, SDK, or file membership)
 */
data class ModuleNode(
    val name: String,
    val module: Module
)

/**
 * A directed adjacency list representing inter-module dependencies.
 *
 * Each key is a [ModuleNode], and its associated value is the list of [ModuleNode]s
 * that the key module directly depends on. An empty list means the module has no
 * module-level dependencies within the codebase/project (library dependencies are not stored).
 */
typealias ModuleDependencyGraph = Map<ModuleNode, List<ModuleNode>>

/**
 * Builds a [ModuleDependencyGraph] for all modules in the given [project].
 *
 * For each module, [ModuleRootManager.getDependencies] is used to retrieve
 * its direct module-to-module dependencies. Library, SDK, and transitive dependencies
 * are excluded - only ones between modules within the same project are stored.
 *
 * Modules are first indexed into a lookup map keyed by their [Module] identity,
 * so that dependency information in the form of dependent modules returned by
 * [ModuleRootManager] can be resolved to corresponding [ModuleNode]s in constant time.
 * Dependencies that cannot be resolved are silently dropped via [mapNotNull].
 *
 * @param project the currently open IntelliJ [Project]
 * @return a [ModuleDependencyGraph] mapping each module to its direct dependencies
 *                                   [a.k.a. adjacency list of dependencies]
 */
fun buildDependencyGraph(project: Project): ModuleDependencyGraph {
    val modules = ModuleManager.getInstance(project).modules

    // Build a lookup map from the raw IntelliJ Module object -> ModuleNode wrapper,
    // so that when we encounter a Module in a dependency list we can resolve it back
    // to a node without a second linear scan through all modules.
    val nodesByModule: Map<Module, ModuleNode> = modules.associate { module ->
        module to ModuleNode(name = module.name, module = module)
    }

    // For each node, ask ModuleRootManager for its direct module dependencies,
    // then resolve each raw Module back to a ModuleNode via the lookup map.
    // mapNotNull silently drops any dependency that isn't in our module set
    // (e.g. unloaded or synthetic modules).
    return nodesByModule.values.associateWith { node ->
        ModuleRootManager.getInstance(node.module)
            .dependencies                              // retrieve direct module-to-module dependencies only
            .mapNotNull { dep -> nodesByModule[dep] }  // resolve Module -> ModuleNode
    }
}
