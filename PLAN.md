# Development Plan

This document describes the stages in which this prototype will be developed.
Functionality is implemented incrementally, in phases.

## Phase 0 - Create static dependency graph

Already completed.

1. **Gather dependency information**
- [x] All active modules are gathered using `ModuleManager`.
- [x] Modules that do not end with `.main` are filtered out, as they are typically automatically created to represent 
  auxiliary sources rather than the main code.
- [x] Dependency information for each remaining module is gathered from `ModuleRootManager`.
2. **Draw a static module dependency graph**
- [x] Dependency data is formatted into a JGraphT object
- [x] JGraphT objects is then used to construct the graph using JGraphX.

## Phase 1 - Harden and polish the static graph

Goal: Make the existing static graph robust and presentable.

Tasks:

1. **Graceful behaviour when there's no Gradle project**
- [ ] Detect if the project has no Gradle modules (e.g., `ModuleManager` returns only IntelliJ modules not ending 
  in `.main`).
- [ ] In that case, show a simple message panel in the tool window:
  "No Gradle 'main' modules detected. Open a Gradle project to view the build graph."
2. **Legend / minimal explanation inside the panel**
- [ ] Add a small legend area (label to the side of the graph) stating:
  "Nodes = modules (Gradle main source sets). Edges = 'depends on' (A -> B means A depends on B)."
3. **Basic interaction affordances**
- [ ] **Fit to screen on open:** call `graphComponent.fit()` after `applyLayout()` completes so the graph fills the 
  visible area without manual zooming on first render.
- [x] **Mouse wheel zoom:** `mxGraphComponent` exposes `zoomIn()` and `zoomOut()` - wire these to a 
  `MouseWheelListener` (disable the default scroll-to-pan behaviour first with `setWheelScrollingEnabled(false)`).
- [x] **Panning:** `isPanning = true` is already set, but currently doesn't work.
  - [x] Configure `mxPanningHandler` to require a modifier key (Ctrl) or middle-mouse button could resolve this.
- [ ] Optionally expose zoom in / zoom out / fit actions via a small toolbar in the `SimpleToolWindowPanel`.
4. **Graph refresh on project reload**
- [ ] Subscribe to `ModuleRootListener` or the external system sync event so the graph is rebuilt whenever Gradle 
  modules are added, removed, or reloaded.
- [ ] Without this, the graph will be stale after any structural change to the project.

Deliverable: A static, usable module dependency graph that behaves sensibly in any project and is self-explanatory.

## Phase 2 - Post-build status overlay (after build completes)

Goal: After a Gradle build finishes, color nodes based on what happened to each module in that build.
This will link the graph with the actual build process and establish the visualization infrastructure
that will later be used for real-time updates.

Tasks:

1. **Listen to Gradle/External System build events**
- [ ] Use `BuildProgressListener` to gather build and compilation progress information in real-time using 
  `StartEvent` and `FinishEvent` events (the latter is more important for post-build feedback).
- [ ] Tags within each event can then be used to determine module compilation outcome.
2. **Map tasks to graph vertices**
- [ ] Map a task path like `:message-dashboard:compileKotlin` to the corresponding graph vertex
  `my-root-project.message-dashboard`. This can be done somewhat deterministically by:
  - Removing `compileKotlin` or `compileJava` from the end,
  - Replacing all semicolons with full stops (dots)
  - Appending the project name to the beginning
3. **Apply coloring post-build**
- [ ] When the build finishes, update the JGraphX styles:
    - Green for "compiled this build"
    - Grey for "skipped / up-to-date"
    - Red for "compile failed"
- [ ] Trigger a repaint of the graph component.
4. **UI feedback (maybe?)**
- [ ] Add a small status label above the graph summarizing the outcome: e.g. "Last build: 42 modules compiled, 10 
  skipped, 1 failed."

Deliverable: The graph is recolored after each build to reflect that build's per-module outcome.

## Phase 3 - Live build status (while build is running)

Goal: Make the graph reflect the live state of the build, updating in real time as compilation progresses.

Tasks:

1. **Handle start and finish events**
- [ ] In the build listener, on `StartEvent`, transition the node to a "compiling" state (e.g., yellow).
- [ ] On `FinishEvent`, transition to compiled / failed / skipped as established in Phase 2.
2. **Threading**
- [ ] All UI updates (`graph` style changes, repaint) must happen on IntelliJ's EDT via
  `ApplicationManager.getApplication().invokeLater {...}`.
3. **Batch processing for updates (if needed?)**
- [ ] For large projects with many concurrent tasks, batch updates every 100-200ms so to avoid excessive repaints.

Deliverable: Nodes transition through colors as their compilation tasks start and finish during a build.

## Phase 4 - Pre-build "changed and affected" preview (no ABI yet)

Goal: Before the build runs, highlight modules that are currently changed and modules that would be affected by a rebuild.

Tasks:

1. **Identify changed modules**
- [ ] Use `ChangeListManager` to get currently changed files.
- [ ] Map files to modules via `ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)`.
- [ ] Map the resulting module to its JGraphT vertex name using the same strategy as in `buildDependencyGraph`.
2. **Compute transitive dependents**
- [ ] Using JGraphT, traverse the graph in reverse to find all modules that depend (directly or transitively) on 
  any changed module.
- [ ] Distinguish between:
   - "changed" modules (directly contain modified files)
   - "affected" modules (dependents that will likely be recompiled)
- [ ] Represent these with distinct node styles (solid fill vs. outline-only).
3. **Update on changelist changes**
- [ ] Subscribe to `ChangeListListener` so that the preview is recomputed and the graph refreshed whenever files are added to or removed from the changelist.
4. **Banner / hint**
- [ ] Display a banner above the graph summarizing the impact ("Current changelist touches 3 modules and 
  potentially affects 9 dependent modules.")

Deliverable: The graph reflects the scope of the pending build based on the current changelist, updating as files are modified.