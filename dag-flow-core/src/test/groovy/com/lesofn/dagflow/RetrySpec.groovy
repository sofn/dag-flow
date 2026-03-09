package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight retry mechanism tests.
 */
class RetrySpec extends Specification {

    // ======================== Test Commands ========================

    static class AlwaysFailJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger attempts = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            attempts.incrementAndGet()
            throw new RuntimeException("always fails")
        }
    }

    static class FailThenSucceedJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger attempts = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            int attempt = attempts.incrementAndGet()
            if (attempt < 3) {
                throw new RuntimeException("fail attempt " + attempt)
            }
            return "success_on_attempt_" + attempt
        }
    }

    static class SuccessJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger attempts = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            attempts.incrementAndGet()
            return "immediate_success"
        }
    }

    static class FailOnceJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger attempts = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            int attempt = attempts.incrementAndGet()
            if (attempt == 1) {
                throw new RuntimeException("first attempt fails")
            }
            return "success"
        }
    }

    static class DownstreamResultJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            def upstream = context.getResult(FailOnceJob.class)
            return "downstream_got_" + upstream
        }
    }

    def setup() {
        AlwaysFailJob.attempts.set(0)
        FailThenSucceedJob.attempts.set(0)
        SuccessJob.attempts.set(0)
        FailOnceJob.attempts.set(0)
    }

    // ======================== Tests ========================

    def "node without retry fails on first exception"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(AlwaysFailJob.class)
                .run(context)

        then:
        thrown(ExecutionException)
        AlwaysFailJob.attempts.get() == 1
    }

    def "node with retry succeeds after initial failures"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FailThenSucceedJob.class).retry(3, Duration.ofMillis(50))
                .run(context)

        then:
        runner.getResult(FailThenSucceedJob.class) == "success_on_attempt_3"
        FailThenSucceedJob.attempts.get() == 3
    }

    def "node with retry exhausts all retries and fails"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(AlwaysFailJob.class).retry(2, Duration.ofMillis(50))
                .run(context)

        then:
        thrown(ExecutionException)
        AlwaysFailJob.attempts.get() == 3  // 1 initial + 2 retries
    }

    def "retry delay is respected"() {
        given:
        def context = new Test1Context(name: "test")
        def start = System.currentTimeMillis()

        when:
        try {
            new JobBuilder<Test1Context>()
                    .node(AlwaysFailJob.class).retry(2, Duration.ofMillis(100))
                    .run(context)
        } catch (ExecutionException ignored) {}

        then:
        def elapsed = System.currentTimeMillis() - start
        elapsed >= 180  // 2 retries * 100ms delay (with some tolerance)
    }

    def "retry with zero delay works"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(AlwaysFailJob.class).retry(2, Duration.ZERO)
                .run(context)

        then:
        thrown(ExecutionException)
        AlwaysFailJob.attempts.get() == 3
    }

    def "retry does not affect successful node"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(SuccessJob.class).retry(3, Duration.ofMillis(50))
                .run(context)

        then:
        runner.getResult(SuccessJob.class) == "immediate_success"
        SuccessJob.attempts.get() == 1
    }

    def "retry in dependency chain - upstream retries then downstream succeeds"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FailOnceJob.class).retry(1, Duration.ofMillis(50))
                .node(DownstreamResultJob.class).depend(FailOnceJob.class)
                .run(context)

        then:
        runner.getResult(FailOnceJob.class) == "success"
        runner.getResult(DownstreamResultJob.class) == "downstream_got_success"
        FailOnceJob.attempts.get() == 2
    }
}
