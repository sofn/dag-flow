package com.lesofn.dagflow

import com.lesofn.dagflow.test1.*
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function

/**
 * 基本DAG执行测试（迁移自 test1/TestStarter）
 */
class BasicDagSpec extends Specification {

    // 依赖链执行: Job1 → Job2 → Job3
    // Job1 → Job2 → Job3
    def "dependency chain execution"() {
        given:
        def request = new Test1Context()
        request.setName("hello")

        when:
        def runner = new JobBuilder<Test1Context>()
                .addNode(Job2.class).depend(Job1.class)
                .addNode(Job3.class).depend(Job2.class)
                .run(request)

        then:
        runner.getResult(Job3.class) != null
        runner.getResult(Job2.class) == "job2"
        runner.getResult(Job1.class) instanceof Long
    }

    // 通过 dependNode 方法声明依赖
    // Job1 → Job4
    def "declare dependency via dependNode method"() {
        given:
        def request = new Test1Context()
        request.setName("hello")

        when:
        def runner = new JobBuilder<Test1Context>()
                .addNode(Job4.class)
                .run(request)

        then:
        runner.getResult(Job4.class) == "job4 result"
        runner.getResult(Job1.class) instanceof Long
    }

    // Function 类型 Lambda 节点
    // Job1 → node4(func)
    def "function lambda node"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
        builder.funcNode("node4", { c -> "hello_func" } as Function).depend(Job1.class)

        when:
        def runner = builder.run(request)

        then:
        runner.getResult("node4") == "hello_func"
    }

    // Consumer 类型 Lambda 节点
    // Job1 → consumer(consumer)
    def "consumer lambda node"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
        builder.funcNode("consumer", { c -> } as Consumer).depend(Job1.class)

        when:
        builder.run(request)

        then:
        noExceptionThrown()
    }

    // Builder 可以多次 run 复用
    // Job1 → func(func)
    def "builder can be reused for multiple runs"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
        builder.funcNode("func", { c -> "result" } as Function).depend(Job1.class)

        when:
        def run1 = builder.run(request)
        def run2 = builder.run(request)

        then:
        run1.getResult(Job1.class) != null
        run2.getResult(Job1.class) != null
        run1.getResult("func") == "result"
        run2.getResult("func") == "result"
    }
}
