package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.BatchCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.test2.BatchJob1
import com.lesofn.dagflow.test2.Test2Context
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * P1-2: BatchCommand 不应继承 AsyncCommand，且保持独立执行语义
 */
class BatchCommandApiSpec extends Specification {

    def "BatchCommand is not an AsyncCommand"() {
        given:
        def batchJob = new BatchJob1()

        expect:
        !(batchJob instanceof AsyncCommand)
    }

    def "BatchCommand can provide its own executor"() {
        given:
        def request = new Test2Context()
        request.setName("hello")
        request.setParams([0L])

        def customExecutor = Executors.newSingleThreadExecutor({ r ->
            def t = new Thread(r, "custom-batch")
            t.setDaemon(true)
            t
        })

        def batchWithExecutor = new BatchCommand<Test2Context, Long, String>() {
            @Override
            Set<Long> batchParam(Test2Context context) {
                return [0L] as Set
            }

            @Override
            String run(Test2Context context, Long param) {
                return Thread.currentThread().name
            }

            @Override
            Executor executor() {
                return customExecutor
            }
        }

        when:
        def runner = new JobBuilder<Test2Context>()
                .node("batch", batchWithExecutor)
                .run(request)

        then:
        ((Map) runner.getResult("batch")).get(0L) == "custom-batch"

        cleanup:
        customExecutor?.shutdownNow()
    }

    def "BatchCommand still executes with default executor"() {
        given:
        def request = new Test2Context()
        request.setName("hello")
        request.setParams([0L, 1L, 2L])

        when:
        def runner = new JobBuilder<Test2Context>()
                .node(BatchJob1.class)
                .run(request)

        then:
        def result = runner.getResult(BatchJob1.class) as Map
        result.size() == 3
    }
}
