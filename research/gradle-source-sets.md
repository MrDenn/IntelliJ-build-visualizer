# Build Tools API (BTA)

**Status:** Exploring

## Summary

Gradle is currently the most popular build tool for Java and Kotlin projects, and has many concepts it introduces on top
of the standard Java/Kotlin compilation process, one of them being the concept of **source sets**. IntelliJ IDEA has its
own concept to represent subprojects – **modules**, but the way the IDE converts Gradle source sets into modules
is not immediately obvious to me. So, I'm trying to get a complete picture of what **source sets** are and how they
should be handled in the logic behind the visualizer.

## Current understanding

### Source sets concept

- Source sets are not a Java feature, but rather a purely Gradle concept [[1]](#1)
- Source sets provide granularity within subprojects, allowing for separation of:
  - Compile-time Classpaths (dependencies seen by the compiler)
  - Runtime classpath (dependencies accessible during execution)
  - Compiler settings
  - Compilation outputs (and output location)
- Similar functionality is present in some other build systems / tools:
  - Scala / sbt – dependency "configurations" cover all functionality of Gradle source sets [[2]](#2)
  - Maven – no true equivalent, but dependency separation is reached through 6 fixed dependency scopes
    (`compile`, `runtime`, `test`, `provided`, `system`, `import`) [[3]](#3)
  - Bazel – an even more flexible system is seen in the form of "targets" [[4]](#4)

## How to handle conversion into IntelliJ modules

### Procedural toggles for each source set suffix

The most flexible and simple to implement solution would be to pass source set filtering completely onto the user.

- Individual toggles on the side panel for each source set suffix would accommodate for any possible workflow and
  application of source sets in different projects
  - If changing tests, the user can turn on the `test` source set suffix to see all test modules and their compilation
  - If changing production code, the user can turn off everything but the `main` source set suffix to focus on the
    production modules and their compilation
  - If working on a Kotlin Multiplatform project, where source sets have a wider use with more suffixes, the user can
    tune what is displayed in the visualizer completely to their needs

### Presets for common workflows

The best way to approach this idea would be to combine it with individual toggles, augmenting this functionality by
including simple, intuitive presets.

- Toggles for the most popular configurations of individual suffix toggles. This could include:
  - For various stages of production: new features, refactors, testing
  - For different platforms (in Kotlin multiplatform): Android, pure JVM
  - Leave overarching, simple presets: all suffixes, only `:main`, etc.
- These groups could be overridden by the user to include / exclude non-standard suffixes

## Open questions

- What other suffixes are present when IntelliJ scans Gradle source sets?
  - Are there any suffixes that have no relation to the source code at all, and are by-products of the conversion?
- How are source sets utilized in different projects?
  - Is there clear fragmentation between project types / languages / frameworks that lead to source sets being used
    in distinctly different ways?
- How should dependencies within subprojects (between source sets) be handled by the visualizer?
  - Would collapsible nodes solve this problem?
  - How could per-suffix visibility described above be accommodated?

## Notes and observations

- *TBD*

## Sources

<a id="1">[1]</a>  [Gradle Docs – JVM Projects guide](https://docs.gradle.org/current/userguide/building_java_projects.html)

<a id="2">[2]</a>  [sbt Docs – Dependency configurations](https://www.scala-sbt.org/1.x/docs/Custom-Dependency-Configuration.html)

<a id="3">[3]</a>  [Apache Maven Docs – Dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)

<a id="4">[4]</a>  [Bazel Docs – Build reference](https://bazel.build/concepts/build-ref)

