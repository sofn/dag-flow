package com.lesofn.dagflow.hystrix

import com.lesofn.dagflow.tracing.DagFlowTracing
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

/**
 * Hystrix 模块 OpenTelemetry 链路追踪测试
 */
class HystrixTracingSpec extends Specification {

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

    // HystrixNode 执行时 Span 包含 Hystrix 特有属性
    def "HystrixNode span contains hystrix-specific attributes"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .run(request)

        then:
        runner.getResult("originHystrixJob") == "OriginHystrixJobResult"

        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan != null

        def nodeSpan = spans.find { it.name.startsWith("dagflow.node.") }
        nodeSpan != null
        nodeSpan.parentSpanId == dagSpan.spanContext.spanId
        nodeSpan.attributes.get(HystrixCommandWrapper.ATTR_COMMAND_KEY) != null
        nodeSpan.attributes.get(HystrixCommandWrapper.ATTR_GROUP_KEY) != null
        nodeSpan.status.statusCode == StatusCode.OK
    }

    // 多个 HystrixNode 有依赖关系时，每个节点都有 Span
    def "multiple HystrixNodes with dependency each have spans"() {
        given:
        def request = new HystrixContext()
        request.setName("hello")

        when:
        def runner = new HystrixJobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .addHystrixNode(HystrixWrapperJob.class)
                .depend(com.lesofn.dagflow.model.DagNodeFactory.getClassNodeName(OriginHystrixJob.class))
                .run(request)

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
