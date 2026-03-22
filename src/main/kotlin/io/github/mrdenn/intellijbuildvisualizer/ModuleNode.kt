package io.github.mrdenn.intellijbuildvisualizer

/**
 * Immutable vertex type for the module dependency graph.
 *
 * Used as JGraphT vertex — [equals]/[hashCode] are auto-generated from both
 * fields, which is correct since two modules with the same display name but
 * different Gradle paths are distinct.
 *
 * @param displayName human-readable name shown in the graph (e.g. "my-project.core-utils")
 * @param gradleProjectPath Gradle project path (e.g. ":core-utils") for task-to-vertex
 *   mapping in Phase 2; null if the module is not linked to Gradle
 */
data class ModuleNode(
    val displayName: String,
    val gradleProjectPath: String?
)
