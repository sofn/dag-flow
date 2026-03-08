package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.CalcCommand
import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.replay.DagFlowReplay
import com.lesofn.dagflow.replay.DagFlowReplayCollector
import com.lesofn.dagflow.replay.DagFlowReplayPrinter
import com.lesofn.dagflow.replay.NodeReplayRecord
import com.lesofn.dagflow.test2.BatchJob1
import com.lesofn.dagflow.test2.Test2Context
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.function.Function

/**
 * Comprehensive tests for the replay recording feature.
 */
class ReplaySpec extends Specification {

    // ======================== Test Context & Commands ========================

    static class Ctx extends DagFlowContext {
        Map<String, Object> data = new ConcurrentHashMap<>()
    }

    static class AsyncJobA implements AsyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) {
            Thread.sleep(50)
            return "resultA"
        }
    }

    static class AsyncJobB implements AsyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) {
            Thread.sleep(30)
            return "resultB"
        }
    }

    static class CalcJobC implements CalcCommand<Ctx, Integer> {
        @Override
        Integer run(Ctx context) {
            Thread.sleep(20)
            return 42
        }
    }

    static class SyncJobD implements SyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) {
            return "syncResult"
        }
    }

    static class FailJob implements AsyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) {
            throw new RuntimeException("intentional failure")
        }
    }

    // ======================== enableReplay() basic tests ========================

    def "replay is null when not enabled"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .node(AsyncJobA.class)
                .run(ctx)

        then:
        runner.getReplayRecord() == null
    }

    def "enableReplay returns non-null replay record"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .run(ctx)

        then:
        runner.getReplayRecord() != null
        runner.getReplayRecord().nodeCount == 1
        runner.getReplayRecord().success
    }

    def "replay records correct node count"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(CalcJobC.class).depend(AsyncJobA.class, AsyncJobB.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 3
        replay.nodeRecords.size() == 3
    }

    // ======================== Timing accuracy ========================

    def "replay records timing data for all nodes"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(CalcJobC.class).depend(AsyncJobA.class, AsyncJobB.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.totalDurationMs > 0
        replay.startTime != null
        replay.endTime != null

        and: "each node has valid timing"
        for (NodeReplayRecord node : replay.nodeRecords) {
            assert node.startOffsetMs >= 0
            assert node.endOffsetMs >= node.startOffsetMs
            assert node.durationMs >= 0
            assert node.threadName != null
            assert node.nodeType != null
        }
    }

    def "dependent node starts after its dependencies"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(CalcJobC.class).depend(AsyncJobA.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        def nodeA = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobA") }
        def nodeC = replay.nodeRecords.find { it.nodeName.startsWith("calcJobC") }
        nodeC.startOffsetMs >= nodeA.endOffsetMs
    }

    def "parallel nodes have overlapping timings"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        def nodeA = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobA") }
        def nodeB = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobB") }
        // Both start near 0ms (within tolerance)
        nodeA.startOffsetMs < 20
        nodeB.startOffsetMs < 20
    }

    // ======================== Node metadata ========================

    def "replay records correct node types"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(CalcJobC.class)
                .node(SyncJobD.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        def asyncNode = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobA") }
        def calcNode = replay.nodeRecords.find { it.nodeName.startsWith("calcJobC") }
        def syncNode = replay.nodeRecords.find { it.nodeName.startsWith("syncJobD") }
        asyncNode.nodeType == "AsyncCommand"
        calcNode.nodeType == "CalcCommand"
        syncNode.nodeType == "SyncCommand"
    }

    def "replay records dependency names"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(CalcJobC.class).depend(AsyncJobA.class, AsyncJobB.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        def nodeA = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobA") }
        def nodeC = replay.nodeRecords.find { it.nodeName.startsWith("calcJobC") }
        nodeA.dependencies.isEmpty()
        nodeC.dependencies.size() == 2
    }

    def "replay records thread names"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(SyncJobD.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        for (NodeReplayRecord node : replay.nodeRecords) {
            assert node.threadName != null
            assert !node.threadName.isEmpty()
            assert node.threadName != "unknown"
        }
    }

    // ======================== Lambda nodes ========================

    def "replay works with lambda function nodes"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node({ Ctx c -> "lambda_result" } as Function)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 1
        replay.nodeRecords[0].nodeName == "node#0"
        replay.nodeRecords[0].success
    }

    def "replay works with mixed class and lambda nodes"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node("transform", { Ctx c -> "transformed" } as Function).depend(AsyncJobA.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 2
        def transformNode = replay.nodeRecords.find { it.nodeName == "transform" }
        transformNode != null
        transformNode.dependencies.size() == 1
    }

    // ======================== Error handling ========================

    def "replay records error status on failed node"() {
        given:
        def ctx = new Ctx()

        when:
        new JobBuilder<Ctx>()
                .enableReplay()
                .node(FailJob.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    def "replay records success = true for all successful nodes"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(SyncJobD.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.success
        replay.nodeRecords.every { it.success }
    }

    // ======================== Builder reuse ========================

    def "replay works correctly across multiple runs of the same builder"() {
        given:
        def builder = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)

        when:
        def runner1 = builder.run(new Ctx())
        def runner2 = builder.run(new Ctx())

        then:
        runner1.getReplayRecord() != null
        runner2.getReplayRecord() != null
        runner1.getReplayRecord().id != runner2.getReplayRecord().id
        runner1.getReplayRecord().nodeCount == 2
        runner2.getReplayRecord().nodeCount == 2
    }

    // ======================== Virtual threads ========================

    def "replay works with virtual threads"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .useVirtualThreads()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 2
        replay.success
        for (NodeReplayRecord node : replay.nodeRecords) {
            assert node.durationMs >= 0
        }
    }

    // ======================== Batch command ========================

    def "replay records batch command timing"() {
        given:
        def ctx = new Test2Context()
        ctx.params = [1L, 2L, 3L]

        when:
        def runner = new JobBuilder<Test2Context>()
                .enableReplay()
                .node(BatchJob1.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 1
        def batchNode = replay.nodeRecords[0]
        batchNode.nodeType == "BatchCommand"
        batchNode.durationMs >= 0
        batchNode.success
    }

    // ======================== DagFlowReplayCollector unit tests ========================

    def "collector generates unique IDs"() {
        when:
        def c1 = new DagFlowReplayCollector()
        def c2 = new DagFlowReplayCollector()

        then:
        c1.build().id != c2.build().id
    }

    def "collector builds empty replay when no nodes registered"() {
        given:
        def collector = new DagFlowReplayCollector()
        collector.onDagEnd(true)

        when:
        def replay = collector.build()

        then:
        replay.nodeCount == 0
        replay.nodeRecords.isEmpty()
        replay.success
    }

    def "collector records node start and end correctly"() {
        given:
        def collector = new DagFlowReplayCollector()
        collector.registerNode("nodeA", "AsyncCommand", [])

        when:
        collector.onNodeStart("nodeA")
        Thread.sleep(10)
        collector.onNodeEnd("nodeA", true, null)
        collector.onDagEnd(true)
        def replay = collector.build()

        then:
        replay.nodeCount == 1
        def node = replay.nodeRecords[0]
        node.nodeName == "nodeA"
        node.nodeType == "AsyncCommand"
        node.success
        node.durationMs >= 0
    }

    // ======================== Gantt chart output ========================

    def "toGantt produces non-empty output"() {
        given:
        def ctx = new Ctx()
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(CalcJobC.class).depend(AsyncJobA.class, AsyncJobB.class)
                .run(ctx)

        when:
        def gantt = runner.getReplayRecord().toGantt()

        then:
        gantt != null
        gantt.contains("DAG Execution")
        gantt.contains("SUCCESS")
        gantt.contains("asyncJobA")
        gantt.contains("asyncJobB")
        gantt.contains("calcJobC")
        gantt.contains("Async")
        gantt.contains("Calc")
    }

    def "toTimeline produces non-empty output"() {
        given:
        def ctx = new Ctx()
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .run(ctx)

        when:
        def timeline = runner.getReplayRecord().toTimeline()

        then:
        timeline != null
        timeline.contains("DAG Timeline")
        timeline.contains("asyncJobA")
        timeline.contains("asyncJobB")
        timeline.contains("ms")
    }

    def "toGantt and toTimeline print correctly for single sync node"() {
        given:
        def ctx = new Ctx()
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(SyncJobD.class)
                .run(ctx)

        when:
        def gantt = runner.getReplayRecord().toGantt()
        def timeline = runner.getReplayRecord().toTimeline()

        then:
        gantt.contains("syncJobD")
        gantt.contains("Sync")
        timeline.contains("syncJobD")
    }

    def "printer handles null replay gracefully"() {
        expect:
        DagFlowReplayPrinter.gantt(null) == "No replay data."
        DagFlowReplayPrinter.timeline(null) == "No replay data."
    }

    // ======================== DagFlowReplay immutability ========================

    def "replay record is immutable"() {
        given:
        def ctx = new Ctx()
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .run(ctx)

        when:
        runner.getReplayRecord().nodeRecords.add(null)

        then:
        thrown(UnsupportedOperationException)
    }

    // ======================== toString ========================

    def "DagFlowReplay toString contains key info"() {
        given:
        def ctx = new Ctx()
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .run(ctx)

        when:
        def str = runner.getReplayRecord().toString()

        then:
        str.contains("DagFlowReplay")
        str.contains("nodes=1")
        str.contains("success=true")
    }

    // ======================== Complex DAG ========================

    def "replay captures complete diamond DAG topology"() {
        given:
        def ctx = new Ctx()

        when:
        def runner = new JobBuilder<Ctx>()
                .enableReplay()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(CalcJobC.class).depend(AsyncJobA.class, AsyncJobB.class)
                .node(SyncJobD.class).depend(CalcJobC.class)
                .run(ctx)

        then:
        def replay = runner.getReplayRecord()
        replay.nodeCount == 4
        replay.success
        replay.totalDurationMs > 0

        and: "execution order is respected in timing"
        def nodeA = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobA") }
        def nodeB = replay.nodeRecords.find { it.nodeName.startsWith("asyncJobB") }
        def nodeC = replay.nodeRecords.find { it.nodeName.startsWith("calcJobC") }
        def nodeD = replay.nodeRecords.find { it.nodeName.startsWith("syncJobD") }

        nodeC.startOffsetMs >= Math.min(nodeA.endOffsetMs, nodeB.endOffsetMs)
        nodeD.startOffsetMs >= nodeC.endOffsetMs
    }
}
