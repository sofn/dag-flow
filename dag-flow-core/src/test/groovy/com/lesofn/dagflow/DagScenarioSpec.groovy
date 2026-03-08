package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.CalcCommand
import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.exception.DagFlowBuildException
import com.lesofn.dagflow.exception.DagFlowCycleException
import com.lesofn.dagflow.exception.DagFlowRunException
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function

/**
 * DAG 全面场景测试
 */
class DagScenarioSpec extends Specification {

    // ======================== 测试用 Context ========================

    static class SimpleContext extends DagFlowContext {
        String name
        Map<String, Object> data = new ConcurrentHashMap<>()
    }

    // ======================== 测试用 Command ========================

    static class AsyncJobA implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("A", Thread.currentThread().name)
            return "resultA"
        }
    }

    static class AsyncJobB implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("B", Thread.currentThread().name)
            return "resultB"
        }
    }

    static class AsyncJobC implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("C", Thread.currentThread().name)
            return "resultC"
        }
    }

    static class AsyncJobD implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("D", Thread.currentThread().name)
            return "resultD"
        }
    }

    static class AsyncJobE implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("E", Thread.currentThread().name)
            return "resultE"
        }
    }

    static class SyncJobA implements SyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("SyncA", Thread.currentThread().name)
            return "syncResultA"
        }
    }

    static class SyncJobB implements SyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            context.data.put("SyncB", Thread.currentThread().name)
            return "syncResultB"
        }
    }

    static class CalcJobA implements CalcCommand<SimpleContext, Integer> {
        @Override
        Integer run(SimpleContext context) {
            int sum = 0
            for (int i = 0; i < 1000; i++) sum += i
            context.data.put("CalcA", sum)
            return sum
        }
    }

    static class SlowJobA implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) throws Exception {
            TimeUnit.MILLISECONDS.sleep(200)
            context.data.put("SlowA_time", System.currentTimeMillis())
            return "slowA"
        }
    }

    static class SlowJobB implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) throws Exception {
            TimeUnit.MILLISECONDS.sleep(200)
            context.data.put("SlowB_time", System.currentTimeMillis())
            return "slowB"
        }
    }

    static class SlowJobC implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) throws Exception {
            TimeUnit.MILLISECONDS.sleep(50)
            context.data.put("SlowC_time", System.currentTimeMillis())
            return "slowC"
        }
    }

    static class ErrorJob implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            throw new RuntimeException("ErrorJob failed")
        }
    }

    static class NullResultJob implements AsyncCommand<SimpleContext, Object> {
        @Override
        Object run(SimpleContext context) {
            return null
        }
    }

    static class CountingJob implements AsyncCommand<SimpleContext, Integer> {
        static final AtomicInteger counter = new AtomicInteger(0)
        @Override
        Integer run(SimpleContext context) {
            return counter.incrementAndGet()
        }
    }

    // ======================== 辅助方法: 解决 Groovy 闭包对 Function/Consumer 的歧义 ========================

    private static Function<SimpleContext, ?> func(Closure<?> closure) {
        return { SimpleContext c -> closure(c) } as Function<SimpleContext, ?>
    }

    // ======================== 场景测试 ========================

    // --- 1. 单节点场景 ---

    // 单个 AsyncCommand 节点执行
    // [AsyncJobA]
    def "single async command node"() {
        given:
        def ctx = new SimpleContext(name: "test")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
    }

    // 单个 SyncCommand 节点执行
    // [SyncJobA]
    def "single sync command node"() {
        given:
        def ctx = new SimpleContext(name: "test")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(SyncJobA.class)
                .run(ctx)

        then:
        runner.getResult(SyncJobA.class) == "syncResultA"
    }

    // 单个 CalcCommand 节点执行
    // [CalcJobA]
    def "single calc command node"() {
        given:
        def ctx = new SimpleContext(name: "test")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(CalcJobA.class)
                .run(ctx)

        then:
        runner.getResult(CalcJobA.class) == 499500
    }

    // --- 2. 并行执行场景 ---

    // 无依赖的多个节点并行执行
    // A  B  C  (parallel, no dependencies)
    def "independent nodes execute in parallel"() {
        given:
        def ctx = new SimpleContext(name: "parallel")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(AsyncJobC.class)
                .run(ctx)

        then: "all nodes complete and return correct results"
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
    }

    // 两个慢节点并行，总耗时应接近单个最慢节点的耗时
    // SlowA  SlowB  (parallel, no dependencies)
    def "slow nodes run in parallel with reduced total time"() {
        given:
        def ctx = new SimpleContext(name: "timing")

        when:
        long start = System.currentTimeMillis()
        def runner = new JobBuilder<SimpleContext>()
                .node(SlowJobA.class)
                .node(SlowJobB.class)
                .run(ctx)
        long elapsed = System.currentTimeMillis() - start

        then: "parallel execution, total time should be less than sum of both (400ms), normally ~200ms"
        runner.getResult(SlowJobA.class) == "slowA"
        runner.getResult(SlowJobB.class) == "slowB"
        elapsed < 400
    }

    // --- 3. 依赖关系场景 ---

    // 线性依赖链: A → B → C
    // A → B → C
    def "linear dependency chain"() {
        given:
        def ctx = new SimpleContext(name: "chain")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class).depend(AsyncJobA.class)
                .node(AsyncJobC.class).depend(AsyncJobB.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
    }

    // 菱形依赖: A → B, A → C, B → D, C → D
    //   A
    //  / \
    // B   C
    //  \ /
    //   D
    def "diamond dependency"() {
        given:
        def ctx = new SimpleContext(name: "diamond")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class).depend(AsyncJobA.class)
                .node(AsyncJobC.class).depend(AsyncJobA.class)
                .node(AsyncJobD.class).depend(AsyncJobB.class, AsyncJobC.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
        runner.getResult(AsyncJobD.class) == "resultD"
    }

    // 扇出: A → B, A → C, A → D
    //     A
    //   / | \
    //  B  C  D
    def "fan-out dependency"() {
        given:
        def ctx = new SimpleContext(name: "fanout")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class).depend(AsyncJobA.class)
                .node(AsyncJobC.class).depend(AsyncJobA.class)
                .node(AsyncJobD.class).depend(AsyncJobA.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
        runner.getResult(AsyncJobD.class) == "resultD"
    }

    // 扇入: A → D, B → D, C → D
    // A  B  C
    //  \ | /
    //    D
    def "fan-in dependency"() {
        given:
        def ctx = new SimpleContext(name: "fanin")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class)
                .node(AsyncJobC.class)
                .node(AsyncJobD.class).depend(AsyncJobA.class, AsyncJobB.class, AsyncJobC.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobD.class) == "resultD"
    }

    // 下游等待上游慢节点完成后才执行
    // SlowA → SlowC
    def "downstream waits for slow upstream"() {
        given:
        def ctx = new SimpleContext(name: "wait")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(SlowJobA.class)
                .node(SlowJobC.class).depend(SlowJobA.class)
                .run(ctx)

        then: "SlowC starts after SlowA completes"
        runner.getResult(SlowJobA.class) == "slowA"
        runner.getResult(SlowJobC.class) == "slowC"
        (ctx.data["SlowC_time"] as Long) >= (ctx.data["SlowA_time"] as Long)
    }

    // --- 4. Lambda / Function 节点场景 ---

    // Function 节点可以返回结果
    // [square]
    def "function node returns result"() {
        given:
        def ctx = new SimpleContext(name: "func")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("square", func { c -> 42 * 42 })
                .run(ctx)

        then:
        runner.getResult("square") == 1764
    }

    // 多个 Function 节点有依赖关系
    // AsyncJobA → func1 → func2
    def "multiple function nodes with dependencies"() {
        given:
        def ctx = new SimpleContext(name: "multi-func")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node("func1", func { c -> "from_func1" }).depend(AsyncJobA.class)
                .node("func2", func { c -> "from_func2" }).depend("func1")
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult("func1") == "from_func1"
        runner.getResult("func2") == "from_func2"
    }

    // Consumer 节点返回 null
    // [myConsumer]
    def "consumer node returns null"() {
        given:
        def ctx = new SimpleContext(name: "consumer")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("myConsumer", { c -> } as Consumer)
                .run(ctx)

        then:
        noExceptionThrown()
        runner.getResult("myConsumer") == null
    }

    // --- 5. 混合类型节点 ---

    // 混合 Sync + Async + Calc 节点执行
    // SyncJobA → AsyncJobA → CalcJobA
    def "mixed sync async calc nodes"() {
        given:
        def ctx = new SimpleContext(name: "mixed")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(SyncJobA.class)
                .node(AsyncJobA.class).depend(SyncJobA.class)
                .node(CalcJobA.class).depend(AsyncJobA.class)
                .run(ctx)

        then:
        runner.getResult(SyncJobA.class) == "syncResultA"
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(CalcJobA.class) == 499500
    }

    // --- 6. 循环检测场景 ---

    // DAG 存在循环时抛出 DagFlowCycleException
    // AsyncJobA ⇄ AsyncJobB (cycle)
    def "cycle in DAG throws DagFlowCycleException"() {
        given: "build cycle: AsyncJobA -> AsyncJobB -> AsyncJobA"
        def ctx = new SimpleContext(name: "cycle")
        def builder = new JobBuilder<SimpleContext>()
        builder.node(AsyncJobA.class).depend(AsyncJobB.class)
        builder.node(AsyncJobB.class).depend(AsyncJobA.class)

        when:
        builder.run(ctx)

        then:
        thrown(DagFlowCycleException)
    }

    // 依赖未注册的名称节点时抛出 DagFlowBuildException
    def "depend on unregistered named node throws DagFlowBuildException"() {
        given:
        def ctx = new SimpleContext(name: "unregistered")
        def builder = new JobBuilder<SimpleContext>()
        builder.node("nodeA", func { c -> "a" })

        when:
        builder.node("nodeB", func { c -> "b" }).depend("nonExistentNode")

        then:
        thrown(DagFlowBuildException)
    }

    // --- 7. 错误传播场景 ---

    // 节点执行异常时，DAG 抛出 ExecutionException
    // [ErrorJob(throws)]
    def "node exception throws ExecutionException"() {
        given:
        def ctx = new SimpleContext(name: "error")

        when:
        new JobBuilder<SimpleContext>()
                .node(ErrorJob.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    // 上游节点异常，下游节点被取消
    // ErrorJob(throws) → AsyncJobA
    def "upstream exception cancels downstream"() {
        given:
        def ctx = new SimpleContext(name: "error-propagation")

        when:
        new JobBuilder<SimpleContext>()
                .node(ErrorJob.class)
                .node(AsyncJobA.class).depend(ErrorJob.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    // 多节点中仅一个出错，整个DAG报错
    // AsyncJobA  ErrorJob(throws)
    //               ↓
    //            AsyncJobB
    def "single node failure fails entire DAG"() {
        given:
        def ctx = new SimpleContext(name: "partial-error")

        when:
        new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(ErrorJob.class)
                .node(AsyncJobB.class).depend(ErrorJob.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    // --- 8. 节点结果为 null ---

    // 节点返回 null 不抛异常
    // [NullResultJob]
    def "node returning null does not throw"() {
        given:
        def ctx = new SimpleContext(name: "null")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(NullResultJob.class)
                .run(ctx)

        then:
        runner.getResult(NullResultJob.class) == null
        noExceptionThrown()
    }

    // --- 9. Builder 复用场景 ---

    // 同一个 Builder 多次 run 产生独立结果
    // [CountingJob] (run twice)
    def "builder reuse produces independent results"() {
        given:
        CountingJob.counter.set(0)
        def ctx1 = new SimpleContext(name: "run1")
        def ctx2 = new SimpleContext(name: "run2")
        def builder = new JobBuilder<SimpleContext>()
        builder.node(CountingJob.class)

        when:
        def runner1 = builder.run(ctx1)
        def runner2 = builder.run(ctx2)

        then: "each run re-executes the node"
        runner1.getResult(CountingJob.class) != null
        runner2.getResult(CountingJob.class) != null
        CountingJob.counter.get() == 2
    }

    // --- 10. 自定义节点名称 ---

    // 自定义节点名称注册和获取结果
    // [myCustomNode (AsyncJobA)]
    def "custom node name registration and result retrieval"() {
        given:
        def ctx = new SimpleContext(name: "custom-name")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("myCustomNode", AsyncJobA.class)
                .run(ctx)

        then:
        runner.getResult("myCustomNode") == "resultA"
    }

    // --- 11. 通过实例注册节点 ---

    // 通过 Command 实例注册节点
    // [instanceNode (AsyncJobA)]
    def "register node by command instance"() {
        given:
        def ctx = new SimpleContext(name: "instance")
        def cmd = new AsyncJobA()

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("instanceNode", cmd)
                .run(ctx)

        then:
        runner.getResult("instanceNode") == "resultA"
    }

    // --- 12. 获取不存在的节点结果 ---

    // 获取未注册节点的结果时抛 DagFlowRunException
    def "get result of unregistered node throws DagFlowRunException"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .run(ctx)

        when:
        runner.getResult(AsyncJobB.class)

        then:
        thrown(DagFlowRunException)
    }

    // --- 13. 未 addNode 直接 depend 抛异常 ---

    // 未 addNode 直接 depend(Class) 抛 DagFlowBuildException
    def "depend without addNode throws DagFlowBuildException"() {
        when:
        new JobBuilder<SimpleContext>().depend(AsyncJobA.class)

        then:
        thrown(DagFlowBuildException)
    }

    // 未 addNode 直接 depend(String) 抛 DagFlowBuildException
    def "depend by name without addNode throws DagFlowBuildException"() {
        when:
        new JobBuilder<SimpleContext>().depend("someNode")

        then:
        thrown(DagFlowBuildException)
    }

    // --- 14. 大规模并行场景 ---

    // 20个无依赖节点全部并行执行
    // node_0  node_1  node_2  ...  node_19  (all parallel)
    def "mass parallel with 20 independent nodes"() {
        given:
        def ctx = new SimpleContext(name: "mass-parallel")
        def builder = new JobBuilder<SimpleContext>()
        20.times { i ->
            builder.node("node_${i}" as String, func { c -> "result_${i}" })
        }

        when:
        def runner = builder.run(ctx)

        then:
        20.times { i ->
            assert runner.getResult("node_${i}" as String) == "result_${i}"
        }
    }

    // --- 15. 长链依赖 ---

    // 长链依赖(10层)正确顺序执行
    // chain_0 → chain_1 → chain_2 → ... → chain_9
    def "long dependency chain with 10 levels"() {
        given:
        def ctx = new SimpleContext(name: "long-chain")
        def builder = new JobBuilder<SimpleContext>()
        builder.node("chain_0", func { c -> 0 })
        (1..9).each { i ->
            builder.node("chain_${i}" as String, func { c -> i }).depend("chain_${i - 1}" as String)
        }

        when:
        def runner = builder.run(ctx)

        then:
        (0..9).each { i ->
            assert runner.getResult("chain_${i}" as String) == i
        }
    }

    // --- 16. 宽菱形: 多扇出 + 多扇入 ---

    // 宽菱形拓扑: root → [m1,m2,m3] → sink
    //      root
    //    / | \
    //  m1  m2  m3
    //    \ | /
    //     sink
    def "wide diamond topology"() {
        given:
        def ctx = new SimpleContext(name: "wide-diamond")
        def builder = new JobBuilder<SimpleContext>()
        builder.node("root", func { c -> "root" })
        builder.node("m1", func { c -> "m1" }).depend("root")
        builder.node("m2", func { c -> "m2" }).depend("root")
        builder.node("m3", func { c -> "m3" }).depend("root")
        builder.node("sink", func { c -> "sink" }).depend("m1", "m2", "m3")

        when:
        def runner = builder.run(ctx)

        then:
        runner.getResult("root") == "root"
        runner.getResult("m1") == "m1"
        runner.getResult("m2") == "m2"
        runner.getResult("m3") == "m3"
        runner.getResult("sink") == "sink"
    }

    // --- 17. 复杂 DAG: 混合菱形+扇出 ---

    // 复杂DAG拓扑: A→B, A→C, B→D, C→D, D→E, A→E
    //   A
    //  /|\
    // B  C \
    //  \/   |
    //   D   |
    //    \ /
    //     E
    def "complex DAG topology"() {
        given:
        def ctx = new SimpleContext(name: "complex")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class).depend(AsyncJobA.class)
                .node(AsyncJobC.class).depend(AsyncJobA.class)
                .node(AsyncJobD.class).depend(AsyncJobB.class, AsyncJobC.class)
                .node(AsyncJobE.class).depend(AsyncJobD.class, AsyncJobA.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
        runner.getResult(AsyncJobD.class) == "resultD"
        runner.getResult(AsyncJobE.class) == "resultE"
    }

    // --- 18. Context 传递数据 ---

    // 节点可以通过 Context 传递数据给下游
    // producer → consumer_node
    def "context data passing between nodes"() {
        given:
        def ctx = new SimpleContext(name: "context-data")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("producer", func { SimpleContext c ->
                    c.data.put("key", "value")
                    return "produced"
                })
                .node("consumer_node", func { SimpleContext c ->
                    return "got:" + c.data.get("key")
                }).depend("producer")
                .run(ctx)

        then:
        runner.getResult("producer") == "produced"
        runner.getResult("consumer_node") == "got:value"
    }

    // --- 19. 自定义 Executor ---

    // Function 节点使用自定义 Executor
    // [custom]
    def "function node with custom executor"() {
        given:
        def ctx = new SimpleContext(name: "custom-executor")
        def customExecutor = Executors.newSingleThreadExecutor({ r ->
            new Thread(r, "custom-thread-test")
        })

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("custom", func { c -> "custom_result" }, customExecutor)
                .run(ctx)

        then:
        runner.getResult("custom") == "custom_result"

        cleanup:
        customExecutor.shutdown()
    }

    // --- 20. 多层扇入扇出组合 ---

    // 多层扇入扇出: A→[B,C,D], [B,C]→E, [D,E]→F
    //      A
    //    / | \
    //   B  C  D
    //    \/   |
    //    E    |
    //     \  /
    //      F
    def "multi-level fan-in fan-out"() {
        given:
        def ctx = new SimpleContext(name: "multi-level")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node(AsyncJobA.class)
                .node(AsyncJobB.class).depend(AsyncJobA.class)
                .node(AsyncJobC.class).depend(AsyncJobA.class)
                .node(AsyncJobD.class).depend(AsyncJobA.class)
                .node(AsyncJobE.class).depend(AsyncJobB.class, AsyncJobC.class)
                .node("F", func { c -> "resultF" }).depend(AsyncJobD.class, AsyncJobE.class)
                .run(ctx)

        then:
        runner.getResult(AsyncJobA.class) == "resultA"
        runner.getResult(AsyncJobB.class) == "resultB"
        runner.getResult(AsyncJobC.class) == "resultC"
        runner.getResult(AsyncJobD.class) == "resultD"
        runner.getResult(AsyncJobE.class) == "resultE"
        runner.getResult("F") == "resultF"
    }
}
