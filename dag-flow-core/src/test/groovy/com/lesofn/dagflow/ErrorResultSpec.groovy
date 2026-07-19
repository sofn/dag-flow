package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.exception.DagFlowRunException
import spock.lang.Specification

import java.util.function.Function

/**
 * P0-2: 错误不再静默吞没，getResult 严格化测试
 */
class ErrorResultSpec extends Specification {

    static class SimpleContext extends DagFlowContext {
        String name
    }

    static class NullJob implements AsyncCommand<SimpleContext, Object> {
        @Override
        Object run(SimpleContext context) {
            return null
        }
    }

    static class DependByName implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            return "got_" + context.getResult("upstream")
        }
    }

    def "getResult on unregistered node throws DagFlowRunException"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def runner = new JobBuilder<SimpleContext>()
                .node("ok", { c -> "ok" } as Function)
                .run(ctx)

        when:
        runner.getResult("missing")

        then:
        thrown(DagFlowRunException)
    }

    def "getResult returns null when node successfully returns null"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def runner = new JobBuilder<SimpleContext>()
                .node(NullJob.class)
                .run(ctx)

        expect:
        runner.getResult(NullJob.class) == null
    }

    def "tryGetResult returns empty for unregistered node"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def runner = new JobBuilder<SimpleContext>()
                .node("ok", { c -> "ok" } as Function)
                .run(ctx)

        expect:
        !runner.tryGetResult("missing").isPresent()
    }

    def "getResultOrDefault returns default for unregistered node"() {
        given:
        def ctx = new SimpleContext(name: "test")
        def runner = new JobBuilder<SimpleContext>()
                .node("ok", { c -> "ok" } as Function)
                .run(ctx)

        expect:
        runner.getResultOrDefault("missing", "fallback") == "fallback"
    }

    def "context getResult by name returns actual dependency value"() {
        given:
        def ctx = new SimpleContext(name: "test")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .node("upstream", { c -> "up" } as Function)
                .node(DependByName.class)
                .depend("upstream")
                .run(ctx)

        then:
        runner.getResult(DependByName.class) == "got_up"
    }
}
