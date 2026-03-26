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
- [x] **Fit to screen on open:** call `graphComponent.fit()` after `applyLayout()` completes so the graph fills the 
  visible area without manual zooming on first render.
- [x] **Mouse wheel zoom:** `mxGraphComponent` exposes `zoomIn()` and `zoomOut()` - wire these to a 
  `MouseWheelListener` (disable the default scroll-to-pan behaviour first with `setWheelScrollingEnabled(false)`).
- [x] **Panning:** `isPanning = true` is already set, but currently doesn't work.
  - [x] Configure `mxPanningHandler` to require a modifier key (Ctrl) or middle-mouse button could resolve this.
- [x] Optionally expose zoom in / zoom out / fit actions via a small toolbar in the `SimpleToolWindowPanel`.
4. **Graph refresh on project reload**
- [x] Subscribe to `ModuleRootListener.TOPIC` on the project message bus; filter out file-type-only events.
  - [x] Debounce rapid-fire events (e.g. during Gradle sync) using `Alarm` with a 500ms delay on SWING_THREAD.
- [x] `DependencyGraphPanel.rebuild()` clears the mxGraph model and repopulates from a fresh JGraphT graph,
  preserving styles, zoom level, toolbar, and interaction handlers.

Deliverable: A static, usable module dependency graph that behaves sensibly in any project and is self-explanatory.

## Phase 2 - Post-build status overlay (after build completes)

Goal: After a Gradle build finishes, color nodes based on what happened to each module in that build.
This will link the graph with the actual build process and establish the state management infrastructure
that Phase 3 will reuse for real-time updates.

Tasks:

1. **Listen to Gradle build events**
- [x] Register an `ExternalSystemTaskNotificationListener` with `ExternalSystemProgressNotificationManager` for the 
  project lifetime.
- [x] Collect `FinishEvent` events for compile tasks (`compileKotlin`, `compileJava`).
- [x] Classify each module's outcome from the typed `FinishEvent.result`:
  - `SuccessResult` for successful compilation
  - `FailureResult` for failed compilation
  - `SkippedResult` and `isUpToDate` for when compilation was skipped
2. **Map tasks to graph vertices**
- [x] Build a lookup `Map<String, ModuleNode>` keyed by `ModuleNode.gradleProjectPath`
- [x] To resolve a task event: strip the task name from the path and look up the corresponding `ModuleNode`.
3. **Accumulate state, flush on build completion**
- [x] Define a `BuildStatus` enum: `COMPILED`, `UP_TO_DATE`, `FAILED`.
- [x] During the build, accumulate status into a `Map<ModuleNode, BuildStatus>`.
- [x] On `FinishBuildEvent`, flush accumulated state information into the UI: use `DependencyGraphPanel.cellMap` to 
  look up the mxGraph cell for each module, apply the appropriate fill color (green / gray / red), then repaint.
4. **UI feedback** (Optional)
- [ ] Add a small status label above the graph summarizing the outcome: e.g. "Last build: 42 modules compiled, 10 
  skipped, 1 failed."

Deliverable: The graph is recolored after each build to reflect that build's per-module outcome.

## Phase 3 - Live build status (while build is running)

Goal: Make the graph reflect the live state of the build, updating in real time as compilation progresses.
This phase is additive on top of Phase 2 - same listener, same state map, same coloring logic.
The only change is *when* accumulated state is flushed to the UI.

Tasks:

1. **Handle start events**
- [x] Extend the existing `ExternalSystemTaskNotificationListener` to also handle `StartEvent` for compile tasks,
  transitioning the module to a "compiling" state (e.g. yellow).
- [x] Add `COMPILING` to the `BuildStatus` enum.
- [x] `FinishEvent` handling remains unchanged from Phase 2.
2. **Periodic batch flushing**
- [x] Periodically flush accumulated build statuses to the UI every 100ms during a build via a coroutine loop on 
  `Dispatchers.EDT`.
- [x] On each tick, flush all accumulated state changes since the last tick to the UI (same cell lookup + restyle +
  repaint logic as Phase 2's end-of-build flush).
- [x] Cancel the alarm when `FinishBuildEvent` arrives (after a final flush).
- [x] This reuses the same `Map<ModuleNode, BuildStatus>` from Phase 2 - the only difference is that changes are now
  also flushed periodically during the build, not only at the end.
3. **Threading**
- [x] All UI updates (mxGraph style changes, repaint) run on the event dispatch thread, with all flushes being 
  scheduled with `Dispatchers.EDT` in the context.

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
- [ ] Subscribe to `ChangeListListener` so that the preview is recomputed and the graph refreshed whenever files are
  added to or removed from the changelist.
4. **Banner / hint**
- [ ] Display a banner above the graph summarizing the impact ("Current changelist touches 3 modules and 
  potentially affects 9 dependent modules.")

Deliverable: The graph reflects the scope of the pending build based on the current changelist, updating as files 
are modified.

## Phase 5 - ABI-aware refinement

Goal: Refine the Phase 4 preview so that ABI-compatible changes do not mark all transitive dependents as affected.

Deliverable: An ABI-compatible change in a leaf module highlights only that module; a public API change in a core module propagates a cascade and triggers a warning.

## Phase 6 - UX & documentation polish

Goal: Tie the project together into a final, polished prototype.
