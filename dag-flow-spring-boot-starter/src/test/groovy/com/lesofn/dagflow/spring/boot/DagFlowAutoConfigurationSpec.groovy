package com.lesofn.dagflow.spring.boot

import com.lesofn.dagflow.spring.SpringContextHolder
import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * DagFlowAutoConfiguration 单元测试
 */
class DagFlowAutoConfigurationSpec extends Specification {

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DagFlowAutoConfiguration))

    def "auto-configuration registers SpringContextHolder bean"() {
        expect:
        runner.run { context ->
            assert context.containsBean("springContextHolder")
            assert context.getBean(SpringContextHolder) != null
        }
    }

    def "auto-configuration can be disabled via property"() {
        expect:
        runner.withPropertyValues("dagflow.enabled=false").run { context ->
            assert !context.containsBean("springContextHolder")
        }
    }

    def "auto-configuration is enabled by default"() {
        expect:
        runner.run { context ->
            assert context.containsBean("springContextHolder")
        }
    }

    def "custom SpringContextHolder bean takes precedence"() {
        expect:
        runner.withBean(SpringContextHolder).run { context ->
            assert context.getBeansOfType(SpringContextHolder).size() == 1
        }
    }
}
