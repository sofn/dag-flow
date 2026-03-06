package com.lesofn.dagflow.spring.boot

import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowAutoConfiguration
import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowProperties
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * DagFlowProperties 配置测试
 */
class DagFlowPropertiesSpec extends Specification {

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DagFlowAutoConfiguration))

    def "DagFlowProperties default values"() {
        expect:
        runner.run { context ->
            def props = context.getBean(DagFlowProperties)
            assert props.isEnabled()
        }
    }

    def "DagFlowProperties can be customized"() {
        expect:
        runner.withPropertyValues("dagflow.enabled=false").run { context ->
            assert !context.containsBean("springContextHolder")
        }
    }
}
