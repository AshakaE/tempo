import kotlin.random.Random
import kotlin.test.*

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() function.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
  /** The number of nodes in the hierarchy. */
  val size: Int

  /**
   * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun nodeId(index: Int): Int

  /**
   * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  val keptIds = IntArray(size)
  val keptDepths = IntArray(size)
  var kept = 0

  // Depth of the closest rejected node on the current DFS path, or -1 if there is none.
  // Thanks to DFS order, every following node that is deeper than it belongs to its subtree,
  // so the whole subtree can be skipped without even evaluating the predicate.
  var rejectedDepth = -1

  for (i in 0 until size) {
    val depth = depth(i)
    if (rejectedDepth != -1 && depth > rejectedDepth) continue // inside a rejected subtree

    val id = nodeId(i)
    if (nodeIdPredicate(id)) {
      rejectedDepth = -1
      // A kept node has all of its ancestors kept, so its depth does not change.
      keptIds[kept] = id
      keptDepths[kept] = depth
      kept++
    } else {
      rejectedDepth = depth
    }
  }

  return ArrayBasedHierarchy(keptIds.copyOf(kept), keptDepths.copyOf(kept))
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {
  init {
    require(myNodeIds.size == myDepths.size) { "nodeIds and depths must have the same length" }
  }

  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

class FilterTest {
  // The forest from the Hierarchy documentation above.
  private val forest: Hierarchy = hierarchy(
    ids = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
    depths = intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2))

  @Test
  fun testFilter() {
    val filteredActual: Hierarchy = forest.filter { nodeId -> nodeId % 3 != 0 }
    val filteredExpected: Hierarchy = hierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2))
    assertSameHierarchy(filteredExpected, filteredActual)
  }

  @Test
  fun emptyHierarchyStaysEmpty() {
    val empty = hierarchy(intArrayOf(), intArrayOf())
    assertSameHierarchy(empty, empty.filter { true })
    assertSameHierarchy(empty, empty.filter { false })
  }

  @Test
  fun keepEverythingReturnsEqualHierarchy() {
    assertSameHierarchy(forest, forest.filter { true })
  }

  @Test
  fun rejectEverythingReturnsEmptyHierarchy() {
    assertSameHierarchy(hierarchy(intArrayOf(), intArrayOf()), forest.filter { false })
  }

  @Test
  fun rejectingRootRemovesWholeTreeEvenIfDescendantsPass() {
    // Root 1 is rejected; 2, 3, 4, 5 would pass but must go with it. Trees 6 and 8 are untouched.
    val actual = forest.filter { it != 1 }
    val expected = hierarchy(
      intArrayOf(6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 0, 1, 1, 2))
    assertSameHierarchy(expected, actual)
  }

  @Test
  fun rejectingMiddleNodeRemovesOnlyItsSubtree() {
    // 2 is rejected, so 3 and 4 go; 5 is a sibling of 2 and survives at the same depth.
    val actual = forest.filter { it != 2 }
    val expected = hierarchy(
      intArrayOf(1, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 0, 1, 0, 1, 1, 2))
    assertSameHierarchy(expected, actual)
  }

  @Test
  fun rejectingLeavesKeepsTheRestIntact() {
    val actual = forest.filter { it != 4 && it != 11 }
    val expected = hierarchy(
      intArrayOf(1, 2, 3, 5, 6, 7, 8, 9, 10),
      intArrayOf(0, 1, 2, 1, 0, 1, 0, 1, 1))
    assertSameHierarchy(expected, actual)
  }

  @Test
  fun rejectingTheLastNodeWorks() {
    val actual = forest.filter { it != 11 }
    val expected = hierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
      intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1))
    assertSameHierarchy(expected, actual)
  }

  @Test
  fun rejectingNestedNodesKeepsTheSubtreeHidden() {
    // 2 is rejected, so 3 and 4 are skipped without ever being checked. Rejecting 3 as well
    // must change nothing, and 5 (sibling of 2) must still survive.
    val actual = forest.filter { it != 2 && it != 3 }
    val expected = hierarchy(
      intArrayOf(1, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 0, 1, 0, 1, 1, 2))
    assertSameHierarchy(expected, actual)
  }

  @Test
  fun predicateIsNotEvaluatedInsideRejectedSubtrees() {
    val visited = mutableListOf<Int>()
    forest.filter { id -> visited += id; id != 1 && id != 9 }
    // Nothing under 1 (2, 3, 4, 5) is checked, and 9 is a leaf, so only the rest is visited.
    assertEquals(listOf(1, 6, 7, 8, 9, 10, 11), visited)
  }

  @Test
  fun filteringTwiceEqualsFilteringWithCombinedPredicate() {
    val twice = forest.filter { it % 2 == 1 }.filter { it < 9 }
    val once = forest.filter { it % 2 == 1 && it < 9 }
    assertSameHierarchy(once, twice)
    assertSameHierarchy(hierarchy(intArrayOf(1, 5), intArrayOf(0, 1)), once)
  }

  @Test
  fun resultAlwaysSatisfiesDepthInvariants() {
    val predicates = listOf<(Int) -> Boolean>({ it % 2 == 0 }, { it % 3 != 0 }, { it > 5 }, { it != 8 })
    for (p in predicates) {
      val h = forest.filter(p)
      if (h.size > 0) assertEquals(0, h.depth(0), "first depth must be 0 for ${h.formatString()}")
      for (i in 1 until h.size) {
        assertTrue(h.depth(i) <= h.depth(i - 1) + 1, "depth jump at $i in ${h.formatString()}")
      }
    }
  }

  @Test
  fun randomForestsMatchNaiveReference() {
    val random = Random(42)
    repeat(2000) {
      val n = random.nextInt(0, 30)
      val ids = IntArray(n) { it + 1 }
      val depths = IntArray(n)
      for (i in 1 until n) depths[i] = random.nextInt(0, depths[i - 1] + 2) // 0..previous+1
      val forest = hierarchy(ids, depths)
      val keep = (1..n).filter { random.nextInt(3) != 0 }.toSet()
      assertSameHierarchy(naiveFilter(forest) { it in keep }, forest.filter { it in keep })
    }
  }

  // Reference implementation: keep a node only if every id on its root-to-node path passes.
  private fun naiveFilter(h: Hierarchy, predicate: (Int) -> Boolean): Hierarchy {
    val ids = mutableListOf<Int>()
    val depths = mutableListOf<Int>()
    val path = mutableListOf<Int>()
    for (i in 0 until h.size) {
      while (path.size > h.depth(i)) path.removeAt(path.size - 1)
      path += h.nodeId(i)
      if (path.all(predicate)) {
        ids += h.nodeId(i)
        depths += h.depth(i)
      }
    }
    return hierarchy(ids.toIntArray(), depths.toIntArray())
  }

  private fun hierarchy(ids: IntArray, depths: IntArray): Hierarchy = ArrayBasedHierarchy(ids, depths)

  private fun assertSameHierarchy(expected: Hierarchy, actual: Hierarchy) {
    assertEquals(expected.formatString(), actual.formatString())
  }
}
