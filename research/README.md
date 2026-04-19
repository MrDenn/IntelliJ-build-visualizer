# Research and Design notes

This directory contains research and design notes for the future direction of the Build Visualizer concept.
It is separate from the current prototype / proof-of-concept (see the repository root) and intended as a
working space for thinking through how this project could evolve.

The documents in this directory are exploratory, meant to compile and organize my research on the possible technologies,
design decisions, and, later, implementation choices.

## Contents

- [Build Tools API](build-tools-api.md) – Research into the new API layer and potential benefits from
  integrating with it
- [Gradle Source Sets](gradle-source-sets.md) – Research into how Gradle source sets work and how they could
  be interpreted in the visualizer

## Current focus

Following up on a recent, very informative discussion, I'm currently focused on exploring:
- Possible integrations with the promising **Build Tools API** system for **Kotlin**
- The `Skyscope` + `Skyframe` dependency graph system for **Bazel** with a variety of useful UX-focused features
- Particularities of how source sets and artifacts are generated from subprojects / modules in **Gradle**,
  and where among these the information needed for the visualization can be found
- Possible strategies and best practices regarding how the functionality of this project should be compartmentalized and
  decoupled (`UI` components, `API` component, `ABI` component, etc.)