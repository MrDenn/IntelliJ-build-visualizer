[![GitHub](https://img.shields.io/badge/Github-MrDenn-blue?logo=github)](https://github.com/MrDenn)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Denis%20Shaikhatarov-1DA1F2)](https://www.linkedin.com/in/denis-shaikhatarov-8b91002b5/)
[![Email](https://img.shields.io/badge/Email-den.shaikhatarov@gmail.com-orange)](https://mail.google.com/mail/?view=cm&fs=1&to=den.shaikhatarov@gmail.com)

<a>
    <picture>
        <source srcset="assets/title.svg"  width="600" media="(prefers-color-scheme: dark)">
        <img src="assets/title.svg" alt="Gradle Build Visualizer" width="600">
    </picture>
</a>

An IntelliJ plugin that shows Gradle's incremental compilation decisions on a module dependency graph - live,
during the build.
![Build Visualizer graph](assets/graph_dynamic_(gradle-multiple-project).gif)
Real-time build tracking via dependency graph for the `Allali84/gradle-multiple-project` repository. The build is 
executed two times in a row, first compiling all modules (coloring them green), then confirming that they are all 
up-to-date (making them blue).

![Build Visualizer graph](assets/graph_static_(exposed).png)
Static visualization of dependencies for the `JetBrains/Exposed` repository.

# The problem
In large multi-module codebases, build slowdowns are a common issue, and they are hidden behind text logs that are 
hard to read and understand. But the deeper issue is that build logs only show *what* ran, but now *why* a given 
module was pulled into the build and recompilation at all. Gradle tracks dependencies and makes decisions based 
on the ABI-compatibility of changes, but this information is not effectively communicated to the user.

This plugin aims to address both the lack of visibility and the lack of causality in build logs through two main 
features:
- **Visibility** – Through clear indication of whether the change list at any time is ABI-compatible or not. If 
  it is not, various warnings could be given to the user in order to make dependency cascades during compilation as 
  predictable and transparent as possible.
- **Causality** – Through a directed graph-based visualization of the dependency tree. This visualization represents 
  project modules as vertices and dependencies as directed edges, and it will be updated in real time during the 
  build to reflect the current state of the build and the decisions made by Gradle.

# Implementation status

- [x] Basic graph visualization of modules and dependencies
- [x] Graph refresh on Gradle project reload / sync
- [x] Graph updates during the build to reflect module states (compiling / done / failed)
- [ ] PSI-based ABI heuristic distinguishing public signature changes from internal-only edits
- [ ] ABI-incompatibility warning banner when a changelist contains public API changes
- [ ] "Are you sure?" dialogue window when launching a build with an ABI-incompatible changelist
- [ ] Pre-build impact preview based on the current changelist

More details are provided in dedicated [plan document](PLAN.md).

# Design approach

The plugin will be contained within the **Dependency Graph** tool window in the bottom left of IntelliJ. Within this 
UI, there will be two main elements to properly inform the user of the scope of the upcoming build.

### Module dependency graph

Being the centerpiece of the **Dependency Graph**, will represent inter-module dependencies through a directed 
graph. The graph will be able to show dependencies between all modules, or between changed and affected modules (a 
chocice given to the user through a toggle). This visualization will both be a passive and an active indicator of 
build execution scope:
- Before the build, it will display all modules, which are affected by current changes and will need to be recompiled;
- During the build, the colors of each node in the graph will be updated in real time to represent modules being 
  recompiled, skipped, and completed.

### ABI-compatibility warning

Include two types of warnings in order to inform the user of possibly long compilation times:
- Passive warning at the top of the **Dependency Graph** tab, which will light up in the form of a "warning"
  banner in case the current changelist is not ABI-compatible;
- "Are you sure?" dialogue window that will pop up when the user launches a build with a changelist that is
  ABI-incompatible.

# Technical decisions

### Module and dependency data gathering
- For non-Gradle projects, the tool window added by the Plugin will not appear at all. This is implemented by checking 
  `GradleProjectResolverUtil.isGradleProject()` in the `BuildVisualizerToolWindowFactory.isApplicableAsync()`.
  - Both of these are used because they are built-in, single-purpose utilities that provide exactly what is needed.
- Modules are retrieved using `ModuleManager`, then filtered to only keep those that end with ".main".
  - `ModuleManager` was chosen because it is built into IntelliJ and possesses the exact functionality needed in 
    this case: retrieving all modules in a given project.
  - Only `.main` modules are kept because IntelliJ represents each Gradle subproject as multiple modules (`.main`, 
    `.test`, etc.) and the dependency structure and compilation is tied exclusively to the `.main` source set.
- For each remaining module, its compile-scope dependencies are queried using `ModuleRootManager`.
  - `ModuleRootManager` was chosen because it provides a convenient API to get all 
    dependencies of a given module, and it works well with ModuleManager.
- `ExternalSystemApiUtil.getExternalProjectId()` is used to obtain each module's Gradle project path
  while stripping the `:main` suffix from them. These are used for mapping build events to graph nodes when 
  recoloring the graph.

### Module and dependency data storage
- After the module and dependency data is gathered, it is stored in a `JGraphT` `Graph` object, with custom `ModuleNode`
  nodes to represent modules, and default `JGraphT` edges representing dependencies between them.
  - The `Graph` data structure was chosen due to its compatibility with `JGraphX` for visualization, and
    its clean API that does not require a custom directed graph implementation.
  - `ModuleNode` is an immutable data class, that was created to bridge module display information with the Gradle API.
    In order to do that, it is used as a key for various maps and contains:
    - Module name – for display purposes (derived from the Gradle path)
    - Gradle path – for mapping build events to graph nodes when recoloring the graph.

### Build event listening
- An `ExternalSystemTaskNotificationListener` registered via `ExternalSystemProgressNotificationManager`
  is used to collect build events. `ExternalSystemBuildEvent` is used to unwrap notification events, which are then
  used to determine which modules are being compiled and recolor the graph accordingly.
  - `ExternalSystemTaskNotificationListener` was chosen over `ProjectTaskListener.TOPIC` because the latter only fires
    for IDE-intialized builds, while the former fires for all Gradle executions unconditionally, including those started
    from the command line or Gradle tool window, which is worth the added complexity.
  - `ExternalSystemBuildEvent` is marked as `@ApiStatus.Experimental`, but its use here is appropriate, since
    this plugin is merely a prototype, and there is no stable alternative with the same coverage.
    Its usage is documented, and related warnings are explicitly suppressed.
  - The listener is owned by `BuildStatusService`, which is project-level rather than attached the tool window,
    so build events are never missed regardless of whether the tool window has been opened.

### Graph visualization
- `JGraphX` (a.k.a. `mxGraph`) is used for rendering, with the `JGraphT` object being converted to an `mxGraph` jazily, 
  only when the user opens the **Dependency Graph** tab for the first time.
  - A third-party rendering library was chosen as a compromise between ease of use and customizability. Writing a custom
    renderer from scratch would be way too complex and time-consuming for a prototype.
  - `JGraphX` was chosen because it is widely used in Java applications, and therefore compatible with Kotlin
     and IntelliJ plugin development, and provides a convenient, but still reasonably customizeable API.
  - Lazy conversion was chosen to decouple module data storage from the visualization, since the former changes 
    only on Gradle refreshes, while the latter updates on Gradle build finishes and in real time during builds.
- Use of JBUI colors was chosen to implement (somewhat) dynamic coloring that adapts to both light and dark themes.

### Graph real-time updates
- During a build, separation of concerns is implemented by splitting the functionality into three classes / services:
  `BuildStatusCollector` catches raw build events while running on a background thread, `BuildStatusService` 
  periodically drains these events and merges them into a graph snapshot on the EDT, and `DependencyGraphPanel` applies 
  the merged snapshot to graph cells.
  - The former and the latter were separated to allow for a lock-free event collection, while the UI updates, which 
    take more time, are performed asynchronously, in batches.
  - The `BuildStatusService` is separated to allow for a completely independent "source of truth" that stores a 
    stable, relatively up-to-date snapshot of the current build/compilation state.
- Flushes to the UI are scheduled using coroutines on the EDT every 100 ms, non-destructively reading the states 
  from the `BuildStatusService`.
  - Coroutines were chosen as the most up-to-date implementation of concurrency in Kotlin, and `invokeLater` was 
    chosen as the most straightforward way to schedule work on the EDT.
  - The 100 ms interval was chosen as a sweet spot between UI responsiveness and overhead of too frequent updates, 
    but it can be easily tweaked later, as it's stored in a companion object.
- `BuildStatus` severity ordering (`COMPILING < UP_TO_DATE < COMPILED < FAILED`) is enforced
  via `merge(maxOf)` at two levels — within a single flush in the `BuildStatusCollector`, and across drains in
  the `BuildStatusService`.
  - This is necessary in the Collector because a single drain can contain multiple events for the same module. For 
    instance, `COMPILING` and `COMPILED` events can be processed in the wrong order, so `maxOf` is there to ensure 
    that the more "severe" status is kept.
  - This is necessary in the Service because Gradle emits separate compile tasks per language (`compileKotlin` 
    finishes as `COMPILED`, then `compileJava` finishes as `UP_TO_DATE` for the same module), and these can land in 
    different drain ticks. Without cross-drain `maxOf`, a later `UP_TO_DATE` would silently overwrite an earlier 
    `COMPILED`.
- The `AtomicBoolean.compareAndSet` gates the launch of the flush coroutine from `BuildStatusCollector`, being set to 
  `true` during the build, and reset to `false` on build finish.
  - This is necessary because during a regular build, both `StartBuildEvent` (via a caught `onStatusChange` event) and 
    `onStart(id)` (lifecycle fallback) fire, which would cause two parallel flush loops would run, doubling EDT work.
    The `AtomicBoolean` gate ensures that only one of these launches the flush loop.
- Statuses, accumulated in `BuildStatusService` during the build, are cleared on next build start.
  - This approach was chosen to prevent a stale `FAILED` status from a previous build from blocking the `COMPILED` 
    status of the next build due to severity ordering.
  - This also means that, conveniently, the `gradle clean` command clears all graph states.

# Feature Scope [MoSCoW]

### Must
- **Dependency Graph** tool window showing inter-module dependencies as a directed graph
- Graph refresh on Gradle project reload / sync
- Post-build node coloring reflecting per-module outcome (compiled / skipped / failed)
- Real-time node state updates during the build (compiling / done / failed)

### Should
- PSI-based ABI heuristic distinguishing public signature changes from internal-only edits
- ABI-incompatibility warning banner when a changelist contains public API changes
- Pre-build impact preview: highlight changed modules and their transitive dependents based on the current changelist

### Could
- Graceful handling of non-Gradle projects (empty state message)
- Toolbar actions for zoom in / zoom out / fit to screen
- Node detail panel on click (module name, Gradle path, last build status)
- View toggle between structure-only, last-build overlay, and changelist impact overlay

### Won't
- Support for build systems other than Gradle
  - This would likely require a complete rewrite and is too complex for a proof of concept
- Custom graph renderer
  - Most of the visual downsides of the `mxGraph` renderer can be worked around, and the level of customization
    it provides is sufficient for a prototype, while the overhead of a custom renderer would be unreasonable for a
    proof of concept
- Custom graph layout algorithms
  - Even though the `mxGraph` hierarchical layout may not be perfectly readable for large codebases, it is good enough 
    for a prototype, and implementing custom layouts would be too complex for the marginal gain in clarity
  - Instead, other features can be implemented that can improve readability without a complete overhaul of the layout
- Exact ABI equivalence
  - Gradle uses a heuristic system, which is more than enough for this proof-of-concept prototype despite not being 
    perfectly accurate