package com.lesofn.dagflow

import com.lesofn.dagflow.test2.*
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.LongStream

/**
 * 批量命令测试（迁移自 test2/TestStarter）
 */
class BatchDagSpec extends Specification {

    // 批量任务正常执行并返回结果 Map
    // BatchJob1 → CommonJob1
    def "batch job executes and returns result map"() {
        given:
        def request = new Test2Context()
        request.setName("hello")
        request.setParams(LongStream.range(0, 10).boxed().collect(Collectors.toList()))

        when:
        def runner = new JobBuilder<Test2Context>()
                .addNode(BatchJob1.class)
                .addNode(CommonJob1.class).depend(BatchJob1.class)
                .run(request)

        then:
        def batchResult = runner.getResult(BatchJob1.class)
        batchResult instanceof Map
        ((Map) batchResult).size() == 10
        runner.getResult(CommonJob1.class) instanceof Long
    }

    // 批量任务中某项抛异常，整个 DAG 传播异常
    // BatchExceptionJob2 → CommonJob1
    def "batch job exception propagates to DAG"() {
        given:
        def request = new Test2Context()
        request.setName("hello")
        request.setParams(LongStream.range(0, 2).boxed().collect(Collectors.toList()))

        when:
        new JobBuilder<Test2Context>()
                .addNode(BatchExceptionJob2.class)
                .addNode(CommonJob1.class).depend(BatchExceptionJob2.class)
                .run(request)

        then:
        thrown(Exception)
    }
}
