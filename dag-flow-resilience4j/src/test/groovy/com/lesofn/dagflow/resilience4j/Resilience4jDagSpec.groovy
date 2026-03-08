package com.lesofn.dagflow.resilience4j

import com.lesofn.dagflow.api.context.DagFlowContext
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Resilience4j 集成测试
 */
class Resilience4jDagSpec extends Specification {

    static class SimpleContext extends DagFlowContext {
        String name
    }

    // 正常执行
    def "normal execution through Resilience4j command"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "hello_r4j" } as Function)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("r4jNode", command)
                .run(ctx)

        then:
        runner.getResult("r4jNode") == "hello_r4j"
    }

    // CircuitBreaker - 正常放行
    def "circuit breaker allows normal execution"() {
        given:
        def ctx = new SimpleContext(name: "cb-test")
        def cb = CircuitBreaker.of("test-allow", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(5)
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "cb_result" } as Function)
                .withCircuitBreaker(cb)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("cbNode", command)
                .run(ctx)

        then:
        runner.getResult("cbNode") == "cb_result"
        cb.state == CircuitBreaker.State.CLOSED
    }

    // Retry - 失败后重试直到成功
    def "retry retries on failure and eventually succeeds"() {
        given:
        def ctx = new SimpleContext(name: "retry-test")
        def counter = new AtomicInteger(0)
        def retry = Retry.of("test-retry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c ->
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("fail")
            }
            return "retry_success"
        } as Function).withRetry(retry)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("retryNode", command)
                .run(ctx)

        then:
        runner.getResult("retryNode") == "retry_success"
        counter.get() == 3
    }

    // Resilience4j 节点配合 DAG 依赖
    def "Resilience4j node with DAG dependencies"() {
        given:
        def ctx = new SimpleContext(name: "dag-r4j")
        def cb = CircuitBreaker.of("test-dag", CircuitBreakerConfig.ofDefaults())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "protected_result" } as Function)
                .withCircuitBreaker(cb)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .node("upstream", { c -> "upstream_result" } as Function)
                .addResilience4jNode("r4jNode", command).depend("upstream")
                .run(ctx)

        then:
        runner.getResult("upstream") == "upstream_result"
        runner.getResult("r4jNode") == "protected_result"
    }

    // 多个装饰器组合
    def "multiple decorators combined"() {
        given:
        def ctx = new SimpleContext(name: "multi-decorator")
        def cb = CircuitBreaker.of("test-multi", CircuitBreakerConfig.ofDefaults())
        def retry = Retry.of("test-multi", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "multi_result" } as Function)
                .withCircuitBreaker(cb)
                .withRetry(retry)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("multiNode", command)
                .run(ctx)

        then:
        runner.getResult("multiNode") == "multi_result"
    }

    // CircuitBreaker 打开状态
    def "circuit breaker opens after repeated failures"() {
        given:
        def cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build()
        def cb = CircuitBreaker.of("test-open", cbConfig)

        // 记录失败
        4.times {
            try {
                cb.executeSupplier({ throw new RuntimeException("fail") })
            } catch (ignored) {
            }
        }

        expect:
        cb.state == CircuitBreaker.State.OPEN
    }

    // Bulkhead 限制并发
    def "bulkhead limits concurrent execution"() {
        given:
        def ctx = new SimpleContext(name: "bulkhead-test")
        def bulkhead = Bulkhead.of("test-bh", BulkheadConfig.custom()
                .maxConcurrentCalls(5)
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "bh_result" } as Function)
                .withBulkhead(bulkhead)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("bhNode", command)
                .run(ctx)

        then:
        runner.getResult("bhNode") == "bh_result"
    }

    // RateLimiter 限流
    def "rate limiter allows within limit"() {
        given:
        def ctx = new SimpleContext(name: "rl-test")
        def rl = RateLimiter.of("test-rl", RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "rl_result" } as Function)
                .withRateLimiter(rl)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("rlNode", command)
                .run(ctx)

        then:
        runner.getResult("rlNode") == "rl_result"
    }

    // TimeLimiter 超时控制 - 正常执行
    def "time limiter allows fast execution"() {
        given:
        def ctx = new SimpleContext(name: "tl-test")
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)
        def tl = TimeLimiter.of("test-tl", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "tl_result" } as Function)
                .withTimeLimiter(tl, scheduler)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("tlNode", command)
                .run(ctx)

        then:
        runner.getResult("tlNode") == "tl_result"

        cleanup:
        scheduler.shutdown()
    }

    // 多个 Resilience4j 节点组成 DAG
    def "multiple Resilience4j nodes in DAG"() {
        given:
        def ctx = new SimpleContext(name: "multi-r4j")
        def cb1 = CircuitBreaker.of("cb1", CircuitBreakerConfig.ofDefaults())
        def cb2 = CircuitBreaker.of("cb2", CircuitBreakerConfig.ofDefaults())
        def cmd1 = new Resilience4jCommand<SimpleContext, String>({ c -> "node1_result" } as Function)
                .withCircuitBreaker(cb1)
        def cmd2 = new Resilience4jCommand<SimpleContext, String>({ c -> "node2_result" } as Function)
                .withCircuitBreaker(cb2)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("r4j1", cmd1)
                .addResilience4jNode("r4j2", cmd2).depend("r4j1")
                .run(ctx)

        then:
        runner.getResult("r4j1") == "node1_result"
        runner.getResult("r4j2") == "node2_result"
    }
}
