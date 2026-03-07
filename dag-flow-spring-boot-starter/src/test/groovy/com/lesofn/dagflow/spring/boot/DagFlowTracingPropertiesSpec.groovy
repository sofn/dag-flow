package com.lesofn.dagflow.spring.boot

import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowAutoConfiguration
import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowProperties
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * DagFlowProperties tracing 配置测试
 */
class DagFlowTracingPropertiesSpec extends Specification {

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DagFlowAutoConfiguration))

    // tracingEnabled 默认为 true
    def "tracingEnabled defaults to true"() {
        expect:
        runner.run { context ->
            def props = context.getBean(DagFlowProperties)
            assert props.isTracingEnabled()
        }
    }

    // tracingEnabled 可配置为 false
    def "tracingEnabled can be set to false"() {
        expect:
        runner.withPropertyValues("dagflow.tracing-enabled=false").run { context ->
            def props = context.getBean(DagFlowProperties)
            assert !props.isTracingEnabled()
        }
    }
}
