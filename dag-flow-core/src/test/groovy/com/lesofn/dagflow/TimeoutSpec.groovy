package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * P0-3: DAG / 节点超时测试
 */
class TimeoutSpec extends Specification {

    static class SimpleContext extends DagFlowContext {
        String name
    }

    static class SlowSync implements SyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) throws Exception {
            TimeUnit.SECONDS.sleep(5)
            return "ok"
        }
    }

    static class SlowAsync implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) throws Exception {
            TimeUnit.SECONDS.sleep(5)
            return "ok"
        }
    }

    def "DAG timeout throws when sync node blocks"() {
        given:
        def ctx = new SimpleContext(name: "timeout")

        when:
        new JobBuilder<SimpleContext>()
                .timeout(100, TimeUnit.MILLISECONDS)
                .node(SlowSync.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    def "DAG timeout throws when async node blocks"() {
        given:
        def ctx = new SimpleContext(name: "async-timeout")

        when:
        new JobBuilder<SimpleContext>()
                .timeout(100, TimeUnit.MILLISECONDS)
                .node(SlowAsync.class)
                .run(ctx)

        then:
        thrown(ExecutionException)
    }

    def "DAG timeout does not throw when node finishes in time"() {
        given:
        def ctx = new SimpleContext(name: "timeout-ok")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .timeout(2, TimeUnit.SECONDS)
                .node("quick", { c -> "ok" } as java.util.function.Function)
                .run(ctx)

        then:
        runner.getResult("quick") == "ok"
    }
}
