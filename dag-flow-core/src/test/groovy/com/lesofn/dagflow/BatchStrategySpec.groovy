package com.lesofn.dagflow

import com.lesofn.dagflow.api.BatchCommand
import com.lesofn.dagflow.api.BatchStrategy
import com.lesofn.dagflow.api.context.DagFlowContext
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.LongStream

/**
 * BatchStrategy 执行策略测试
 */
class BatchStrategySpec extends Specification {

    static class Ctx extends DagFlowContext {
        List<Long> params
    }

    // ======================== ALL 策略测试 ========================

    // ALL 策略：全部子任务完成后返回完整结果
    def "ALL strategy returns results from all items"() {
        given:
        def ctx = new Ctx(params: LongStream.range(0, 5).boxed().collect(Collectors.toList()))

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new AllBatchJob())
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() == 5
        (0L..4L).every { result.containsKey(it) }
    }

    // ALL 策略：某子任务异常时整个节点异常
    def "ALL strategy propagates exception when any item fails"() {
        given:
        def ctx = new Ctx(params: [0L, 1L, 2L])

        when:
        new JobBuilder<Ctx>()
                .node("batch", new FailAtBatchJob(1L, BatchStrategy.ALL))
                .run(ctx)

        then:
        thrown(Exception)
    }

    // ALL 策略：默认策略（不覆盖 batchStrategy）
    def "default strategy is ALL"() {
        given:
        def cmd = new AllBatchJob()

        expect:
        cmd.batchStrategy().isAll()
        cmd.batchStrategy() == BatchStrategy.ALL
    }

    // ======================== ANY 策略测试 ========================

    // ANY 策略：至少1个完成就返回，结果 map 大小 >= 1
    def "ANY strategy returns as soon as 1 item completes"() {
        given:
        // 用 latch 控制慢任务：param=0 立刻完成，其余等待 latch
        def latch = new CountDownLatch(1)
        def ctx = new Ctx(params: LongStream.range(0, 10).boxed().collect(Collectors.toList()))

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new LatchBatchJob(latch, [0L] as Set, BatchStrategy.ANY))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() >= 1
        result.size() < 10

        cleanup:
        latch.countDown()
    }

    // ANY 策略：快速完成的子项结果在 map 中
    def "ANY strategy contains result of the fast item"() {
        given:
        def latch = new CountDownLatch(1)
        def ctx = new Ctx(params: [0L, 1L, 2L, 3L, 4L])

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new LatchBatchJob(latch, [0L] as Set, BatchStrategy.ANY))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.containsKey(0L)

        cleanup:
        latch.countDown()
    }

    // ANY 策略：只有1个参数时正常返回
    def "ANY strategy with single param returns that result"() {
        given:
        def ctx = new Ctx(params: [42L])

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new FastAnyBatchJob())
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() == 1
        result.containsKey(42L)
    }

    // ANY 策略：全部失败时传播异常
    def "ANY strategy propagates exception when all items fail"() {
        given:
        def ctx = new Ctx(params: [0L, 1L, 2L])

        when:
        new JobBuilder<Ctx>()
                .node("batch", new AllFailBatchJob(BatchStrategy.ANY))
                .run(ctx)

        then:
        thrown(Exception)
    }

    // ======================== AT_LEAST_N 策略测试 ========================

    // AT_LEAST_N 策略：至少N个完成就返回，结果 map 大小 >= N
    def "AT_LEAST_N strategy returns when N items complete"() {
        given:
        def latch = new CountDownLatch(1)
        def fastParams = [0L, 1L, 2L] as Set
        def ctx = new Ctx(params: LongStream.range(0, 10).boxed().collect(Collectors.toList()))

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new LatchBatchJob(latch, fastParams, BatchStrategy.atLeast(3)))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() >= 3
        result.size() < 10

        cleanup:
        latch.countDown()
    }

    // AT_LEAST_N 策略：快速完成的子项全部在结果中
    def "AT_LEAST_N strategy contains results of all fast items"() {
        given:
        def latch = new CountDownLatch(1)
        def fastParams = [0L, 1L, 2L] as Set
        def ctx = new Ctx(params: [0L, 1L, 2L, 3L, 4L])

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new LatchBatchJob(latch, fastParams, BatchStrategy.atLeast(3)))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() >= 3
        [0L, 1L, 2L].every { result.containsKey(it) }

        cleanup:
        latch.countDown()
    }

    // AT_LEAST_N 策略：N 等于总数时等同 ALL
    def "AT_LEAST_N with N equals total acts like ALL"() {
        given:
        def ctx = new Ctx(params: [0L, 1L, 2L])

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new FastAtLeastNBatchJob(3))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() == 3
    }

    // AT_LEAST_N 策略：N > 总数时报错
    def "AT_LEAST_N with N greater than total fails with exception"() {
        given:
        def ctx = new Ctx(params: [0L, 1L])

        when:
        new JobBuilder<Ctx>()
                .node("batch", new FastAtLeastNBatchJob(5))
                .run(ctx)

        then:
        thrown(Exception)
    }

    // AT_LEAST_N 策略：部分失败但达标仍成功
    def "AT_LEAST_N succeeds when enough items complete despite some failures"() {
        given:
        // 5 个参数，param=3 和 param=4 会失败，需要至少 2 个成功
        def ctx = new Ctx(params: [0L, 1L, 2L, 3L, 4L])

        when:
        def runner = new JobBuilder<Ctx>()
                .node("batch", new PartialFailBatchJob(2, [3L, 4L] as Set))
                .run(ctx)

        then:
        def result = (Map<Long, String>) runner.getResult("batch")
        result.size() >= 2
    }

    // AT_LEAST_N 策略：失败太多无法达标时传播异常
    def "AT_LEAST_N fails when too many items fail to meet threshold"() {
        given:
        // 3 个参数，param=1 和 param=2 会失败，需要至少 2 个成功
        def ctx = new Ctx(params: [0L, 1L, 2L])

        when:
        new JobBuilder<Ctx>()
                .node("batch", new PartialFailBatchJob(2, [1L, 2L] as Set))
                .run(ctx)

        then:
        thrown(Exception)
    }

    // ======================== BatchStrategy 构建测试 ========================

    def "BatchStrategy.atLeast(1) returns ANY"() {
        expect:
        BatchStrategy.atLeast(1).is(BatchStrategy.ANY)
    }

    def "BatchStrategy.atLeast with n < 1 throws"() {
        when:
        BatchStrategy.atLeast(0)

        then:
        thrown(IllegalArgumentException)
    }

    // ======================== DAG 中组合测试 ========================

    // ANY 策略节点作为依赖，下游节点可以正常获取部分结果
    def "ANY strategy node as dependency works in DAG"() {
        given:
        def ctx = new Ctx(params: LongStream.range(0, 5).boxed().collect(Collectors.toList()))

        when:
        def runner = new JobBuilder<Ctx>()
                .node("fast", new FastAnyBatchJob())
                .node("downstream", { c -> "done" } as java.util.function.Function)
                .depend("fast")
                .run(ctx)

        then:
        runner.getResult("downstream") == "done"
        def batchResult = (Map<Long, String>) runner.getResult("fast")
        batchResult.size() >= 1
    }

    // ======================== 内部测试 BatchCommand 实现 ========================

    /**
     * ALL 策略，快速执行
     */
    static class AllBatchJob implements BatchCommand<Ctx, Long, String> {
        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) { "result_" + param }
    }

    /**
     * ANY 策略，快速执行
     */
    static class FastAnyBatchJob implements BatchCommand<Ctx, Long, String> {
        @Override
        BatchStrategy batchStrategy() { BatchStrategy.ANY }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) { "result_" + param }
    }

    /**
     * 通过 CountDownLatch 控制快慢的 BatchCommand。
     * fastParams 中的参数立刻返回，其余等待 latch 释放。
     */
    static class LatchBatchJob implements BatchCommand<Ctx, Long, String> {
        final CountDownLatch latch
        final Set<Long> fastParams
        final BatchStrategy strategy

        LatchBatchJob(CountDownLatch latch, Set<Long> fastParams, BatchStrategy strategy) {
            this.latch = latch
            this.fastParams = fastParams
            this.strategy = strategy
        }

        @Override
        BatchStrategy batchStrategy() { strategy }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) throws Exception {
            if (!fastParams.contains(param)) {
                // 等待 latch，最多 5 秒防止测试挂起
                latch.await(5, TimeUnit.SECONDS)
            }
            return "result_" + param
        }
    }

    /**
     * AT_LEAST_N 快速执行版
     */
    static class FastAtLeastNBatchJob implements BatchCommand<Ctx, Long, String> {
        final int n

        FastAtLeastNBatchJob(int n) { this.n = n }

        @Override
        BatchStrategy batchStrategy() { BatchStrategy.atLeast(n) }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) { "result_" + param }
    }

    /**
     * 指定参数失败
     */
    static class FailAtBatchJob implements BatchCommand<Ctx, Long, String> {
        final Long failParam
        final BatchStrategy strategy

        FailAtBatchJob(Long failParam, BatchStrategy strategy) {
            this.failParam = failParam
            this.strategy = strategy
        }

        @Override
        BatchStrategy batchStrategy() { strategy }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) {
            if (param == failParam) {
                throw new RuntimeException("fail at " + param)
            }
            return "result_" + param
        }
    }

    /**
     * 全部失败
     */
    static class AllFailBatchJob implements BatchCommand<Ctx, Long, String> {
        final BatchStrategy strategy

        AllFailBatchJob(BatchStrategy strategy) { this.strategy = strategy }

        @Override
        BatchStrategy batchStrategy() { strategy }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) {
            throw new RuntimeException("all fail")
        }
    }

    /**
     * 部分失败，策略 AT_LEAST_N
     */
    static class PartialFailBatchJob implements BatchCommand<Ctx, Long, String> {
        final int n
        final Set<Long> failParams

        PartialFailBatchJob(int n, Set<Long> failParams) {
            this.n = n
            this.failParams = failParams
        }

        @Override
        BatchStrategy batchStrategy() { BatchStrategy.atLeast(n) }

        @Override
        Set<Long> batchParam(Ctx context) { new LinkedHashSet<>(context.params) }

        @Override
        String run(Ctx context, Long param) throws Exception {
            if (failParams.contains(param)) {
                throw new RuntimeException("fail at " + param)
            }
            return "result_" + param
        }
    }
}
