package io.github.mrdenn.intellijbuildvisualizer

/**
 * Immutable vertex type for the module dependency graph.
 *
 * @param displayName human-readable label shown in graph vertices (e.g. "core-utils")
 * @param gradleProjectPath Gradle project path (e.g. ":core-utils") used to map
 *   build task events to graph vertices; null if the module has no Gradle linkage
 */
data class ModuleNode(
    val displayName: String,
    val gradleProjectPath: String?
)
