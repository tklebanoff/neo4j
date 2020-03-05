/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.LogicalQuery
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.logical.plans.Ascending
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.cypher.result.OperatorProfile

abstract class ProfileMemoryTestBase[CONTEXT <: RuntimeContext](edition: Edition[CONTEXT], runtime: CypherRuntime[CONTEXT]) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  protected val SIZE: Int = 10

  test("should profile memory of sort") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .sort(Seq(Ascending("x")))
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of distinct") {
    given {
      nodePropertyGraph(SIZE, { case i => Map("p" -> i)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("xp")
      .distinct("x.p AS xp")
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of collect aggregation") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("collect(x) AS c"))
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of grouping aggregation - one large group") {
    given {
      nodePropertyGraph(SIZE, { case _ => Map("p" -> 0)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq("x.p AS xp"), Seq("collect(x.p) AS c"))
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of grouping aggregation - many groups") {
    given {
      nodePropertyGraph(SIZE, { case i => Map("p" -> i)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq("x.p AS xp"), Seq("collect(x.p) AS c"))
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of node hash join") {
    given {
      nodeGraph(SIZE)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .nodeHashJoin("x")
      .|.allNodeScan("x")
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 4, 1)
  }

  test("should profile memory of multi-column node hash join") {
    given {
      bipartiteGraph(SIZE, "X", "Y", "R")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .nodeHashJoin("x", "y")
      .|.expand("(y)--(x)")
      .|.nodeByLabelScan("y", "Y")
      .expand("(x)--(y)")
      .nodeByLabelScan("x", "X")
      .build()

    // then
    assertOnMemory(logicalQuery, 5, 1)
  }

  test("should profile memory of top n, where n < Int.MaxValue") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .top(Seq(Ascending("x")), Int.MaxValue - 1)
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of top n, where n > Int.MaxValue") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .top(Seq(Ascending("x")), Int.MaxValue + 1L)
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  //noinspection SameParameterValue
  protected def assertOnMemory(logicalQuery: LogicalQuery, numOperators: Int, allocatingOperators: Int*): Unit = {
    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    for(i <- 0 until numOperators) {
      withClue(s"Memory allocations of plan $i: ") {
        if (allocatingOperators.contains(i)) {
          queryProfile.operatorProfile(i).maxAllocatedMemory() should be > 0L
        } else {
          queryProfile.operatorProfile(i).maxAllocatedMemory() should be(OperatorProfile.NO_DATA)
        }
      }
    }
    queryProfile.maxAllocatedMemory() should be > 0L
  }
}

/**
 * Tests for runtime with full language support
 */
trait FullSupportProfileMemoryTestBase [CONTEXT <: RuntimeContext] {
  self: ProfileMemoryTestBase[CONTEXT] =>

  test("should profile memory of eager") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .eager()
      .allNodeScan("x")
      .build()

    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of stdDev aggregation") {
    given {
      nodePropertyGraph(SIZE, { case i => Map("p" -> i)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("stdev(x.p) AS c"))
      .allNodeScan("x")
      .build()


    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of percentileDisc aggregation") {
    given {
      nodePropertyGraph(SIZE, { case i => Map("p" -> i)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("percentileDisc(x.p, 0.1) AS c"))
      .allNodeScan("x")
      .build()


    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of percentileCont aggregation") {
    given {
      nodePropertyGraph(SIZE, { case i => Map("p" -> i)})
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("percentileCont(x.p, 0.1) AS c"))
      .allNodeScan("x")
      .build()


    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of distinct aggregation") {
    given {
      nodeGraph(SIZE)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("count(DISTINCT x) AS c"))
      .allNodeScan("x")
      .build()


    // then
    assertOnMemory(logicalQuery, 3, 1)
  }

  test("should profile memory of expand(into)") {
    given {
      bipartiteGraph(SIZE, "X", "Y", "R")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .expandInto("(x)-->(y)")
      .cartesianProduct()
      .|.nodeByLabelScan("y", "Y")
      .nodeByLabelScan("x", "X")
      .build()

    // then
    assertOnMemory(logicalQuery, 5, 1)
  }
}
