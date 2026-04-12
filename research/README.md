# Research and Design notes

This directory contains research and design notes for the future direction of the Build Visualizer concept.
It is separate from the current prototype / proof-of-concept (see the repository root) and intended as a
working space for thinking through how this project could evolve.

The documents in this directory are exploratory, meant to compile and organize my research on the possible technologies,
design decisions, and, later, implementation choices.

## Contents

- *TBD*

## Current focus

Following up on a recent, very informative discussion, I'm currently focused on exploring:
- Possible integrations with the promising `Build tools API` system for `Kotlin`
- The `Skyscope` and `Skyframe` dependency graph system for `Bazel` with a variety of useful UX-focused features
- Particularities of how `source sets` in `Gradle` are generated and ways of accurately tying them to the Gradle 
  subprojects created by the user
- Possible strategies and best practices regarding how the functionality of this project should be compartmentalized and
  decoupled (`UI` components, `API` component, `ABI` component, etc.)

## Status

Work in progress. Documents here will be added and revised as my understanding develops.