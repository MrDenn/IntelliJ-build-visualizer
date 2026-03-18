# Gradle Build Visualizer

[![LinkedIn Follow](https://img.shields.io/badge/LinkedIn-%40Denis%20Shaikhatarov-1DA1F2?logo=twitter)](https://www.linkedin.com/in/denis-shaikhatarov-8b91002b5/)

## The problem
In large codebases with multiple modules, a single source change can trigger recompilation of a large number of these
modules. This usually happens because build systems (like Gradle) track inter-module dependencies and propagate 
recompilation whenever a change is ABI-incompatible - that is, when it modifies the public interface of a module 
that other modules depend on. The cascade can be wide: a change to a shared core module may invalidate dozens of downstream modules, even if the 
actual diff is small.

This behavior is entirely correct and by design, but the details behind the decisions that the build system makes 
are not at all effectively communicated to the developer making a change. The build system logs which tasks ran, 
which were recompiled, and which were skipped, bud does not explain causality - what dependency edges caused a given 
module to be pulled into the build.

This plugin aims to solve this issue through two separate visualization elements:
- First, a clear indication of whether the change list at any time is ABI-compatible or not. If it is not, various 
  warnings could be given to the user in order to make dependency cascades during compilation as predictable and 
  transparent as possible.
- Second, a directed graph-based visualization of the dependency tree. In particular, the graph of dependencies 
  between modules, which will need to be recompiled if a build is launched with the current change list.

## Approach

The plugin will be contained within the **Dependency Graph** tool window in the bottom left of IntelliJ. Within this 
UI, there will be two main elements to properly inform the user of the scope of the upcoming build.

### ABI-compatibility warning

Include two types of warnings in order to inform the user of possibly long compilation times:
- Passive warning at the top of the **Dependency Graph** tab, which will light up in the form of a "warning" 
  banner in case the current changelist is not ABI-compatible;
- "Are you sure?" dialogue window that will pop up when the user launches a build with a changelist that is 
  ABI-incompatible.

### Module dependency graph

Being the centerpiece of the **Dependency Graph**, will represent inter-module dependencies through a directed 
graph. The graph will only show dependencies between the changed and affected (dependent on the former) modules.  
This visualization will both be a passive and an active indicator of build execution scope:
- Before the build, it will display all modules, which are affected by current changes and will need to be recompiled;
- During the build, the colors of each node in the graph will be updated in real time to represent modules being 
  recompiled, skipped, and completed.

## Planned Features [MoSCoW]

### Must
- **Dependency Graph** tool window showing inter-module dependencies as a directed graph
- Graceful handling of non-Gradle projects (empty state message)
- Post-build node coloring reflecting per-module outcome (compiled / skipped / failed)
- Pre-build impact preview: highlight changed modules and their transitive dependents based on the current changelist
- ABI-incompatibility warning banner when a changelist contains public API changes

### Should
- Real-time node state updates during the build (compiling / done / failed as tasks progress)
- Graph refresh on Gradle project reload / sync
- PSI-based ABI heuristic distinguishing public signature changes from internal-only edits

### Could
- Node detail panel on click (module name, Gradle path, last build status)
- View toggle between structure-only, last-build overlay, and changelist impact overlay
- Toolbar actions for zoom in / zoom out / fit to screen

### Won't
- Support for build systems other than Gradle
  - This would likely require a complete rewrite and is too complex for a proof of concept
- Exact ABI equivalence
  - Gradle uses a heuristic system, and replicating its incremental compilation logic is out of scope for a prototype

## Design Decisions [Current implementation]

### Dependency data gathering
- ModuleManager is used to query all available modules
- Only ".main" modules are kept, as they should represent the module code itself
- ModuleRootManager is used to query dependencies for each remaining module
- Modules and dependencies do not update unless plugin is restarted
- JGraphT is used to store the data instead of a custom data type, since it works well with JGraphX and is 
  sufficient for a prototype
### Graph visualization
- JGraphX is used to display the JGraphT object
  - Even though it's not as customizeable as a custom rendering engine, it provides enough flexibility to make the 
    proof of concept look good
- Nodes represent modules, directed edges represent dependencies
- JBUI is used for (somewhat) dynamic coloring that adapts to the selected theme (Light/Dark)