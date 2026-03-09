package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * Timeout control tests: node-level, command-level, and DAG-level timeout.
 */
class TimeoutSpec extends Specification {

    // ======================== Test Commands ========================

    static class FastJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "fast_result"
        }
    }

    static class SlowJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            Thread.sleep(3000)
            return "slow_result"
        }
    }

    static class CommandTimeoutJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            Thread.sleep(3000)
            return "should_not_return"
        }

        @Override
        Duration timeout() {
            return Duration.ofMillis(500)
        }
    }

    static class CommandLongTimeoutJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            Thread.sleep(3000)
            return "should_not_return"
        }

        @Override
        Duration timeout() {
            return Duration.ofSeconds(5)
        }
    }

    static class MediumSlowJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            Thread.sleep(800)
            return "medium_result"
        }
    }

    static class DownstreamJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "downstream_result"
        }
    }

    // ======================== Tests ========================

    def "node without timeout completes normally"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FastJob.class)
                .run(context)

        then:
        runner.getResult(FastJob.class) == "fast_result"
    }

    def "node-level timeout via builder triggers on slow node"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(SlowJob.class).timeout(Duration.ofMillis(500))
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "command-declared timeout triggers on slow node"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(CommandTimeoutJob.class)
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "builder timeout overrides command timeout"() {
        given:
        def context = new Test1Context(name: "test")

        when: "command declares 5s timeout, builder overrides with 500ms"
        new JobBuilder<Test1Context>()
                .node(CommandLongTimeoutJob.class).timeout(Duration.ofMillis(500))
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "DAG-level timeout triggers when total execution exceeds limit"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(SlowJob.class)
                .dagTimeout(Duration.ofMillis(500))
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "fast node with timeout does not timeout"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FastJob.class).timeout(Duration.ofSeconds(5))
                .run(context)

        then:
        runner.getResult(FastJob.class) == "fast_result"
    }

    def "timeout on upstream cancels downstream"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(SlowJob.class).timeout(Duration.ofMillis(500))
                .node(DownstreamJob.class).depend(SlowJob.class)
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "DAG timeout with multiple sequential slow nodes"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(MediumSlowJob.class)
                .node(DownstreamJob.class).depend(MediumSlowJob.class)
                .dagTimeout(Duration.ofMillis(200))
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "DAG timeout allows completion when fast enough"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(FastJob.class)
                .dagTimeout(Duration.ofSeconds(5))
                .run(context)

        then:
        runner.getResult(FastJob.class) == "fast_result"
    }
}
