package com.lesofn.dagflow

import com.lesofn.dagflow.test1.Test1Context
import com.lesofn.dagflow.testerror.Job1 as ErrorTestJob1
import com.lesofn.dagflow.testerror.JobError1
import spock.lang.Specification

import java.util.concurrent.ExecutionException

/**
 * 错误处理测试（迁移自 testerror/TestStarter）
 */
class ErrorHandlingSpec extends Specification {

    // 依赖节点抛出异常时，执行抛出 ExecutionException
    // JobError1(throws) → Job1
    def "dependency node exception throws ExecutionException"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
        builder.addNode(ErrorTestJob1.class).depend(JobError1.class)

        when:
        builder.run(request)

        then:
        thrown(ExecutionException)
    }

    // 节点自身抛出异常时，执行抛出 ExecutionException
    // JobError1(throws)
    def "node self exception throws ExecutionException"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
        builder.addNode(JobError1.class)

        when:
        builder.run(request)

        then:
        thrown(ExecutionException)
    }
}
