package com.lesofn.dagflow.resilience4j

import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.tracing.DagFlowTracing
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

import java.util.function.Function

/**
 * Resilience4j 模块 OpenTelemetry 链路追踪测试
 */
class Resilience4jTracingSpec extends Specification {

    static class SimpleContext extends DagFlowContext {
        String name
    }

    InMemorySpanExporter spanExporter

    def setup() {
        spanExporter = InMemorySpanExporter.create()
        def tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build()
        def otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
        DagFlowTracing.setOpenTelemetry(otel)
    }

    def cleanup() {
        DagFlowTracing.setOpenTelemetry(null)
    }

    // Resilience4j 节点 Span 包含装饰器属性
    def "Resilience4j node span contains decorator attributes"() {
        given:
        def cb = CircuitBreaker.of("testCB", CircuitBreakerConfig.ofDefaults())
        def retry = Retry.of("testRetry", RetryConfig.custom().maxAttempts(2).build())
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "ok" } as Function)
                .withCircuitBreaker(cb)
                .withRetry(retry)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("protected", command)
                .run(new SimpleContext(name: "test"))

        then:
        runner.getResult("protected") == "ok"

        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan != null

        def nodeSpan = spans.find { it.name == "dagflow.node.protected" }
        nodeSpan != null
        nodeSpan.parentSpanId == dagSpan.spanContext.spanId
        nodeSpan.status.statusCode == StatusCode.OK

        def decorators = nodeSpan.attributes.get(Resilience4jCommand.ATTR_DECORATORS)
        decorators.contains("CircuitBreaker")
        decorators.contains("Retry")
    }

    // 无装饰器时属性为空字符串
    def "Resilience4j node with no decorators has empty decorators attribute"() {
        given:
        def command = new Resilience4jCommand<SimpleContext, String>({ c -> "plain" } as Function)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("plain", command)
                .run(new SimpleContext(name: "test"))

        then:
        runner.getResult("plain") == "plain"

        def spans = spanExporter.finishedSpanItems
        def nodeSpan = spans.find { it.name == "dagflow.node.plain" }
        nodeSpan.attributes.get(Resilience4jCommand.ATTR_DECORATORS) == ""
    }

    // 多节点 DAG 中 Resilience4j 节点正确追踪
    def "multiple Resilience4j nodes in DAG all have spans"() {
        given:
        def cb = CircuitBreaker.of("cb", CircuitBreakerConfig.ofDefaults())
        def cmd1 = new Resilience4jCommand<SimpleContext, String>({ c -> "r1" } as Function).withCircuitBreaker(cb)
        def cmd2 = new Resilience4jCommand<SimpleContext, String>({ c -> "r2" } as Function).withCircuitBreaker(cb)

        when:
        def runner = new Resilience4jJobBuilder<SimpleContext>()
                .addResilience4jNode("node1", cmd1)
                .addResilience4jNode("node2", cmd2).depend("node1")
                .run(new SimpleContext(name: "test"))

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan.attributes.get(DagFlowTracing.ATTR_NODE_COUNT) == 2L

        def nodeSpans = spans.findAll { it.name.startsWith("dagflow.node.") }
        nodeSpans.size() == 2
        nodeSpans.every { it.parentSpanId == dagSpan.spanContext.spanId }
        nodeSpans.every { it.status.statusCode == StatusCode.OK }
    }
}
