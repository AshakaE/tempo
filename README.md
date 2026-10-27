# Hierarchy filter

Think of a file explorer: hide every folder whose name starts with a dot, and everything inside a hidden folder disappears too, even if its own name is fine. `Hierarchy.filter` does exactly that for a forest of trees: a node survives only if it passes the predicate **and** every ancestor passed as well.

## Approach

The hierarchy is already stored in depth-first order with a parallel depth array, so one left-to-right pass is enough. The only state is the depth of the most recent rejected node on the current path (`rejectedDepth`). Any later node that is deeper than that depth is inside the rejected subtree and is skipped without evaluating the predicate; the first node at the same or a shallower depth leaves the subtree and resets the state. A kept node keeps its original depth because all of its ancestors were kept, so no depth needs to be recomputed. The whole thing runs in O(n) time with O(n) extra space, and the predicate is called at most once per node.

## Assumptions

- The input respects the depth invariants described in the file (first depth is 0, each depth increases by at most 1). Only the array lengths are validated.
- Node IDs are unique, as the interface states, so the predicate only needs the ID.
- The result is a new `ArrayBasedHierarchy`; the input is never modified.

## Running locally

You need a JDK 17 or newer; the Gradle wrapper downloads Gradle and Kotlin itself. The build compiles the assessment file in place, so no files have to be moved. Run `./gradlew test` (or `gradlew.bat test` on Windows) from this folder; the first run takes a minute to fetch dependencies. A green `BUILD SUCCESSFUL` with 13 tests means everything passed, and a readable report lands in `build/reports/tests/test/index.html`.

The SimpleCache code review from the same assessment is in `Take Home Assessment - SimpleCache.md`.
