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

This plugin aims to solve this issue through two separate visualisation elements:
- First, a clear indication of whether the change list at any time is ABI-compatible or not. If it is not, various 
  warnings could be given to the user in order to make dependency cascades during compilation as predictable and 
  transparent as possible.
- Second, a directed graph-based visualisation of the dependency tree. In particular, the graph of dependencies 
  between modules, which will need to be recompiled if a build is launched with the current change list.
