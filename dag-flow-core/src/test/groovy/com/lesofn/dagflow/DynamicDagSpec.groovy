package com.lesofn.dagflow

import com.lesofn.dagflow.api.CalcCommand
import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.exception.DagFlowBuildException
import com.lesofn.dagflow.exception.DagFlowCycleException
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * DynamicDag 动态 DAG 构建测试
 */
class DynamicDagSpec extends Specification {

    static class Ctx extends DagFlowContext {
        String name
    }

    // ======================== 基本功能 ========================

    // 单节点动态 DAG
    def "single funcNode runs and returns result"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> "hello" } as Function)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("a") == "hello"
    }

    // 两个独立节点并行执行
    def "two independent nodes run in parallel"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> "A" } as Function)
        dag.funcNode("b", { c -> "B" } as Function)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("a") == "A"
        runner.getResult("b") == "B"
    }

    // 线性依赖链 a → b → c
    def "linear dependency chain executes in order"() {
        given:
        def order = Collections.synchronizedList(new ArrayList<String>())
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> order.add("a"); "A" } as Function)
        dag.funcNode("b", { c -> order.add("b"); "B" } as Function, "a")
        dag.funcNode("c", { c -> order.add("c"); "C" } as Function, "b")

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("c") == "C"
        order.indexOf("a") < order.indexOf("b")
        order.indexOf("b") < order.indexOf("c")
    }

    // 菱形依赖 a → b, a → c, b+c → d
    def "diamond dependency works correctly"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> 1 } as Function)
        dag.funcNode("b", { c -> 2 } as Function, "a")
        dag.funcNode("c", { c -> 3 } as Function, "a")
        dag.funcNode("d", { c -> 4 } as Function, "b", "c")

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("a") == 1
        runner.getResult("b") == 2
        runner.getResult("c") == 3
        runner.getResult("d") == 4
    }

    // 多个依赖
    def "node with multiple dependencies waits for all"() {
        given:
        def latch = new CountDownLatch(1)
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("slow", { c ->
            latch.await(5, TimeUnit.SECONDS)
            "slow_done"
        } as Function)
        dag.funcNode("fast", { c -> "fast_done" } as Function)
        dag.funcNode("join", { c -> "joined" } as Function, "slow", "fast")

        when:
        // 释放慢节点
        latch.countDown()
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("join") == "joined"
    }

    // ======================== 实例对象支持 ========================

    // 添加命令实例（非 Class）
    def "addNode with command instance works"() {
        given:
        def command = new SimpleCalcCommand("result_x")
        def dag = new DynamicDag<Ctx>()
        dag.addNode("x", command)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("x") == "result_x"
    }

    // 添加命令实例 + 依赖
    def "addNode with command instance and dependencies"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> "A" } as Function)
        dag.addNode("b", new SimpleCalcCommand("B"), "a")

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("b") == "B"
    }

    // 通过 Class 添加节点
    def "addNode with Class creates instance via reflection"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.addNode(SimpleCalcCommand.class)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("simpleCalcCommand") == "default"
    }

    // Consumer 节点
    def "consumerNode executes side effect"() {
        given:
        def result = new AtomicInteger(0)
        def dag = new DynamicDag<Ctx>()
        dag.consumerNode("effect", { c -> result.set(42) })

        when:
        dag.run(new Ctx(name: "test"))

        then:
        result.get() == 42
    }

    // SyncCommand 实例
    def "addNode with SyncCommand runs on caller thread"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.addNode("sync", new SimpleSyncCommand())

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("sync") == "sync_result"
    }

    // ======================== 增量环检测 ========================

    // 直接自环 b → b（节点创建后依赖解析到自身，增量环检测捕获并回滚）
    def "self-cycle is detected and rolled back"() {
        given:
        def dag = new DynamicDag<Ctx>()

        when:
        dag.funcNode("b", { c -> "B" } as Function, "b")

        then:
        thrown(DagFlowCycleException)

        and:
        dag.size() == 0  // 节点被回滚
    }

    // 无环的链式依赖正常工作
    def "chain dependency a -> b -> c has no cycle"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> "A" } as Function)
        dag.funcNode("b", { c -> "B" } as Function, "a")

        when:
        dag.funcNode("c", { ctx -> "C" } as Function, "b")

        then:
        dag.size() == 3
        notThrown(DagFlowCycleException)
    }

    // 依赖不存在时报错
    def "dependency not found throws exception"() {
        given:
        def dag = new DynamicDag<Ctx>()

        when:
        dag.funcNode("a", { c -> "A" } as Function, "nonexistent")

        then:
        def e = thrown(DagFlowBuildException)
        e.message.contains("dependency not found: nonexistent")
    }

    // 依赖不存在时节点被回滚
    def "failed addNode is rolled back"() {
        given:
        def dag = new DynamicDag<Ctx>()

        when:
        dag.funcNode("a", { c -> "A" } as Function, "nonexistent")

        then:
        thrown(DagFlowBuildException)

        and:
        dag.size() == 0  // 节点 "a" 应该被回滚移除
    }

    // 重复节点名报错
    def "duplicate node name throws exception"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("a", { c -> "A" } as Function)

        when:
        dag.funcNode("a", { c -> "A2" } as Function)

        then:
        thrown(DagFlowBuildException)
    }

    // ======================== 复杂 DAG 场景 ========================

    // 大扇出：多个节点依赖同一个
    def "fan-out: multiple nodes depend on one"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("root", { c -> "ROOT" } as Function)
        (1..5).each { i ->
            dag.funcNode("child_${i}", { c -> "child_${i}" } as Function, "root")
        }

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("root") == "ROOT"
        (1..5).every { runner.getResult("child_${it}") == "child_${it}" }
    }

    // 大扇入：一个节点依赖多个
    def "fan-in: one node depends on many"() {
        given:
        def dag = new DynamicDag<Ctx>()
        (1..5).each { i ->
            dag.funcNode("src_${i}", { c -> i } as Function)
        }
        dag.funcNode("sink", { c -> "done" } as Function, "src_1", "src_2", "src_3", "src_4", "src_5")

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("sink") == "done"
    }

    // 节点异常传播
    def "node exception propagates correctly"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("fail", { c -> throw new RuntimeException("boom") } as Function)

        when:
        dag.run(new Ctx(name: "test"))

        then:
        thrown(Exception)
    }

    // 并行执行验证：独立节点真正并行
    def "independent nodes execute concurrently"() {
        given:
        def startLatch = new CountDownLatch(3)
        def proceedLatch = new CountDownLatch(1)
        def concurrentCount = new AtomicInteger(0)
        def maxConcurrent = new AtomicInteger(0)

        def dag = new DynamicDag<Ctx>()
        (1..3).each { i ->
            dag.funcNode("p_${i}", { c ->
                int cur = concurrentCount.incrementAndGet()
                maxConcurrent.updateAndGet({ old -> Math.max(old, cur) })
                startLatch.countDown()
                proceedLatch.await(5, TimeUnit.SECONDS)
                concurrentCount.decrementAndGet()
                "result_${i}"
            } as Function)
        }

        when:
        proceedLatch.countDown()
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("p_1") == "result_1"
        runner.getResult("p_2") == "result_2"
        runner.getResult("p_3") == "result_3"
    }

    // 虚拟线程模式
    def "virtual threads mode works"() {
        given:
        def dag = new DynamicDag<Ctx>()
        dag.useVirtualThreads()
        dag.funcNode("vt", { c -> Thread.currentThread().isVirtual() ? "virtual" : "platform" } as Function)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("vt") == "virtual"
    }

    // 多次 run 复用 DynamicDag
    def "DynamicDag can be run multiple times"() {
        given:
        def counter = new AtomicInteger(0)
        def dag = new DynamicDag<Ctx>()
        dag.funcNode("count", { c -> counter.incrementAndGet() } as Function)

        when:
        dag.run(new Ctx(name: "run1"))
        dag.run(new Ctx(name: "run2"))
        dag.run(new Ctx(name: "run3"))

        then:
        counter.get() == 3
    }

    // 分散构建：不同方法/模块各自 addNode
    def "nodes can be added from different methods"() {
        given:
        def dag = new DynamicDag<Ctx>()
        addModuleA(dag)
        addModuleB(dag)

        when:
        def runner = dag.run(new Ctx(name: "test"))

        then:
        runner.getResult("moduleA") == "A"
        runner.getResult("moduleB") == "B"
    }

    // size 方法
    def "size reflects registered node count"() {
        given:
        def dag = new DynamicDag<Ctx>()

        expect:
        dag.size() == 0

        when:
        dag.funcNode("a", { c -> "A" } as Function)
        dag.funcNode("b", { c -> "B" } as Function, "a")

        then:
        dag.size() == 2
    }

    // ======================== 辅助方法和类 ========================

    void addModuleA(DynamicDag<Ctx> dag) {
        dag.funcNode("moduleA", { c -> "A" } as Function)
    }

    void addModuleB(DynamicDag<Ctx> dag) {
        dag.funcNode("moduleB", { c -> "B" } as Function, "moduleA")
    }

    static class SimpleCalcCommand implements CalcCommand<Ctx, String> {
        String value

        SimpleCalcCommand() { this.value = "default" }
        SimpleCalcCommand(String value) { this.value = value }

        @Override
        String run(Ctx context) { return value }
    }

    static class SimpleSyncCommand implements SyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) { return "sync_result" }
    }
}
