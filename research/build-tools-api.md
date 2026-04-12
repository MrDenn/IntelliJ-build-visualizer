# Build Tools API (BTA)

**Status:** Exploring

## Summary

The **Build Tools API** is Kotlin's official API layer for build systems to interact cleanly with the Kotlin compiler.
This document is a collection of what I've learned about it, what's still unclear, and how it might fit into
future versions of the visualizer.

## Current understanding

### Overall concept

- The **Build Tools API** proposed to be a unified, stable API layer between various build tools and the Kotlin compiler [[1]](#1)
  - Currently JVM-based, but with plans to be fully multiplatform in the future
  - Split into **API** and **Implementation** modules, which are decoupled from each other and should be interchangeable
- The **BTA** is not yet available to the public, but is a part of the **Kotlin Evolution and Enhancement Process**,
  being one of the most recent proposals [[1]](#1)

### Advantages of BTA

- Unifies and simplifies interactions between **build tools** and the **Kotlin compiler** [[1]](#1)
  - Build tools don't need to reach into the internals of the compiler to integrate with it
  - Each build tool doesn't need to implement its own complicated integration logic from scratch
  - Will make integration of Kotlin into different build tools easier, widening Kotlin support and user freedom
- Allows for **Kotlin** compiler versions to be disjoint from **Gradle** and **Kotlin Gradle Plugin** versions [[2]](#2)
  - Since the BTA will allow for the compiler to be decoupled from the KGP, it will be possible
    to update Kotlin and Gradle versions independently, and even use different Kotlin versions for different tasks in
    the same project
  - Currently, due to a lack of an API layer, the Kotlin compiler ships as a part of the Kotlin Gradle Plugin,
    so the KGP, the compiler, and Gradle versions are "inseparable", and can't be updated independently of each other
- Implements significant QoL improvements for interacting with the Kotlin compiler [[1]](#1)
  - KEEP proposal mentions "type-safe representation for compiler arguments" 

### Potential use cases for the visualizer

- *TBD*

## Open questions

- What exact problems does the BTA aim to solve?
- How could this API be used to enhance the visualizer?
  - Could it provide more detailed information about the incremental compilation process?
  - Does it allow to abstract more from the particular Build tool being used?
  - Does it resolve any Kotlin-specific difficulties with PSI-based ABI detection?
- To what extent can the BTA be used to analyze not only the Kotlin compilation process, but Java compilation as well?

## Notes and observations

- *TBD*

## Sources

<a id="1">[1]</a>  [Kotlin Docs – BTA](https://kotlinlang.org/docs/build-tools-api.html)

<a id="2">[2]</a>  [The KEEP proposal](https://github.com/Kotlin/KEEP/blob/build-tools-api/proposals/extensions/build-tools-api.md)

<a id="3">[3]</a>  [Kotlin Docs – Compiler execution strategy](https://kotlinlang.org/docs/compiler-execution-strategy.html)

