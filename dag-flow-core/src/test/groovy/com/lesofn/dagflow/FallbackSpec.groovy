package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fallback / default value tests.
 */
class FallbackSpec extends Specification {

    // ======================== Test Commands ========================

    static class FallbackJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            throw new RuntimeException("primary failed")
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            return "fallback_value"
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class NoFallbackJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            throw new RuntimeException("no fallback available")
        }
    }

    static class SuccessWithFallbackJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger fallbackCalls = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            return "primary_success"
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            fallbackCalls.incrementAndGet()
            return "should_not_be_called"
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class CaptureExceptionFallbackJob implements AsyncCommand<Test1Context, String> {
        static Throwable capturedCause = null

        @Override
        String run(Test1Context context) {
            throw new RuntimeException("specific error message")
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            capturedCause = cause
            return "recovered"
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class FallbackAlsoFailsJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            throw new RuntimeException("primary failed")
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            throw new RuntimeException("fallback also failed")
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class RetryThenFallbackJob implements AsyncCommand<Test1Context, String> {
        static final AtomicInteger attempts = new AtomicInteger(0)

        @Override
        String run(Test1Context context) {
            attempts.incrementAndGet()
            throw new RuntimeException("always fails")
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            return "fallback_after_retries"
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class NullFallbackJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            throw new RuntimeException("primary failed")
        }

        @Override
        String fallback(Test1Context context, Throwable cause) {
            return null
        }

        @Override
        boolean hasFallback() {
            return true
        }
    }

    static class DownstreamOfFallbackJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            def upstream = context.getResult(FallbackJob.class)
            return "downstream_got_" + upstream
        }
    }

    def setup() {
        SuccessWithFallbackJob.fallbackCalls.set(0)
        CaptureExceptionFallbackJob.capturedCause = null
        RetryThenFallbackJob.attempts.set(0)
    }

    // ======================== Tests ========================

    def "command with fallback returns fallback value on failure"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FallbackJob.class)
                .run(context)

        then:
        runner.getResult(FallbackJob.class) == "fallback_value"
    }

    def "command without fallback propagates exception"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(NoFallbackJob.class)
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "fallback receives the original exception"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CaptureExceptionFallbackJob.class)
                .run(context)

        then:
        runner.getResult(CaptureExceptionFallbackJob.class) == "recovered"
        CaptureExceptionFallbackJob.capturedCause != null
        CaptureExceptionFallbackJob.capturedCause.message == "specific error message"
    }

    def "fallback that also throws propagates as ExecutionException"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(FallbackAlsoFailsJob.class)
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "successful command does not trigger fallback"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(SuccessWithFallbackJob.class)
                .run(context)

        then:
        runner.getResult(SuccessWithFallbackJob.class) == "primary_success"
        SuccessWithFallbackJob.fallbackCalls.get() == 0
    }

    def "retry exhausted then fallback succeeds"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(RetryThenFallbackJob.class).retry(2, Duration.ofMillis(50))
                .run(context)

        then:
        runner.getResult(RetryThenFallbackJob.class) == "fallback_after_retries"
        RetryThenFallbackJob.attempts.get() == 3  // 1 initial + 2 retries, then fallback
    }

    def "fallback in dependency chain - downstream sees fallback value"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FallbackJob.class)
                .node(DownstreamOfFallbackJob.class).depend(FallbackJob.class)
                .run(context)

        then:
        runner.getResult(FallbackJob.class) == "fallback_value"
        runner.getResult(DownstreamOfFallbackJob.class) == "downstream_got_fallback_value"
    }

    def "fallback returns null - valid result, no exception"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(NullFallbackJob.class)
                .run(context)

        then:
        runner.getResult(NullFallbackJob.class) == null
        notThrown(ExecutionException)
    }
}
