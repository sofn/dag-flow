package com.lesofn.dagflow.hystrix

import com.lesofn.dagflow.model.DagNodeFactory
import spock.lang.Specification

import java.util.function.Function

/**
 * Hystrix 集成测试（迁移自 hystrix/TestStarter）
 */
class HystrixDagSpec extends Specification {

    // 原生 HystrixCommand 直接执行
    def "native HystrixCommand direct execution"() {
        given:
        def job = new OriginHystrixJob()

        when:
        def result = job.run()

        then:
        result == "OriginHystrixJobResult"
    }

    // 单个 HystrixNode 在 DAG 中执行
    def "single HystrixNode in DAG"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .run(request)

        then:
        runner.getResult("originHystrixJob") == "OriginHystrixJobResult"
        HystrixJobBuilder.getHystrixResult(runner, OriginHystrixJob.class) == "OriginHystrixJobResult"
    }

    // 多个 HystrixNode 有依赖关系时按序执行
    // OriginHystrixJob → HystrixWrapperJob
    def "multiple HystrixNodes with dependency execute in order"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .addHystrixNode(HystrixWrapperJob.class)
                .depend(DagNodeFactory.getClassNodeName(OriginHystrixJob.class))
                .run(request)

        then:
        runner.getResult("originHystrixJob") == "OriginHystrixJobResult"
        HystrixJobBuilder.getHystrixResult(runner, OriginHystrixJob.class) == "OriginHystrixJobResult"
        runner.getResult("hystrixWrapperJob") == "HystrixWrapperJobResult"
        HystrixJobBuilder.getHystrixResult(runner, HystrixWrapperJob.class) == "HystrixWrapperJobResult"
    }

    // HystrixCommand 实例方式添加节点
    def "add HystrixNode by instance"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(new OriginHystrixJob())
                .run(request)

        then:
        runner.getResult("originHystrixJob") == "OriginHystrixJobResult"
    }

    // HystrixNode 与普通 DagFlow 节点混合使用
    def "mix HystrixNode with regular DagFlow nodes"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .node("downstream", { c -> "downstream_" + c.getResult("originHystrixJob") } as Function)
                .depend("originHystrixJob")
                .run(request)

        then:
        runner.getResult("originHystrixJob") == "OriginHystrixJobResult"
    }
}
