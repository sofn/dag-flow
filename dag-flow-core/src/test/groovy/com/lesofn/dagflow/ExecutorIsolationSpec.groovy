package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.executor.DagFlowExecutor
import com.lesofn.dagflow.executor.DagFlowExecutorConfig
import spock.lang.Specification

import java.util.concurrent.Executors
/**
 * P0-1: 线程池隔离与可配置性测试
 */
class ExecutorIsolationSpec extends Specification {

    static class SimpleContext extends DagFlowContext {
        String name
    }

    static class CaptureAsync implements AsyncCommand<SimpleContext, String> {
        @Override
        String run(SimpleContext context) {
            return Thread.currentThread().name
        }
    }

    def "default executor is not shared between builders"() {
        when:
        def e1 = DagFlowExecutor.defaultExecutor()
        def e2 = DagFlowExecutor.defaultExecutor()

        then:
        e1.asyncExecutor() != e2.asyncExecutor()
        e1.calcExecutor() != e2.calcExecutor()
        e1.syncExecutor() != e2.syncExecutor()
    }

    def "custom executor is used by async node"() {
        given:
        def customAsync = Executors.newSingleThreadExecutor({ r ->
            def t = new Thread(r, "custom-async")
            t.setDaemon(true)
            t
        })
        def customCalc = Executors.newSingleThreadExecutor({ r ->
            def t = new Thread(r, "custom-calc")
            t.setDaemon(true)
            t
        })
        def customSync = Executors.newSingleThreadExecutor({ r ->
            def t = new Thread(r, "custom-sync")
            t.setDaemon(true)
            t
        })

        def dagExecutor = DagFlowExecutor.custom(
                new DagFlowExecutorConfig()
                        .asyncExecutor(customAsync)
                        .calcExecutor(customCalc)
                        .syncExecutor(customSync)
        )
        def ctx = new SimpleContext(name: "test")

        when:
        def runner = new JobBuilder<SimpleContext>()
                .executor(dagExecutor)
                .node(CaptureAsync.class)
                .run(ctx)

        then:
        runner.getResult(CaptureAsync.class) == "custom-async"

        cleanup:
        dagExecutor?.close()
    }
}
