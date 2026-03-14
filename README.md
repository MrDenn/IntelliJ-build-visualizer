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
- **Dependency Graph** in the bottom right
- A directed graph, showing module dependencies
- ABI-incompatibility warning
### Should
- Real-time updates to graph during the build
### Could
- _
### Won't
- _
