package com.lesofn.dagflow

import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.test2.BatchJob1
import com.lesofn.dagflow.test2.Test2Context
import com.lesofn.dagflow.tracing.DagFlowTracing
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.LongStream

/**
 * OpenTelemetry 链路追踪测试
 */
class TracingSpec extends Specification {

    static class TraceContext extends DagFlowContext {}

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

    // DAG 执行产生根 Span 和节点 Span
    def "DAG run creates root span and node spans"() {
        when:
        new JobBuilder<TraceContext>()
                .node("nodeA", { c -> "a" } as Function)
                .node("nodeB", { c -> "b" } as Function)
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan != null
        dagSpan.attributes.get(DagFlowTracing.ATTR_NODE_COUNT) == 2L

        def nodeSpans = spans.findAll { it.name.startsWith("dagflow.node.") }
        nodeSpans.size() == 2
        nodeSpans.every { it.parentSpanId == dagSpan.spanContext.spanId }
    }

    // 节点 Span 包含正确的属性
    def "node spans have correct attributes"() {
        when:
        new JobBuilder<TraceContext>()
                .node("myFunc", { c -> "result" } as Function)
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def nodeSpan = spans.find { it.name == "dagflow.node.myFunc" }
        nodeSpan != null
        nodeSpan.attributes.get(DagFlowTracing.ATTR_NODE_NAME) == "myFunc"
        nodeSpan.attributes.get(DagFlowTracing.ATTR_NODE_TYPE) == "CalcCommand"
    }

    // 成功执行的 Span 状态为 OK
    def "successful execution sets span status OK"() {
        when:
        new JobBuilder<TraceContext>()
                .node("ok", { c -> "ok" } as Function)
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan.status.statusCode == StatusCode.OK
        def nodeSpan = spans.find { it.name == "dagflow.node.ok" }
        nodeSpan.status.statusCode == StatusCode.OK
    }

    // 异常节点的 Span 状态为 ERROR 并记录异常
    def "failed node sets span status ERROR and records exception"() {
        when:
        new JobBuilder<TraceContext>()
                .node("fail", { c -> throw new RuntimeException("test error") } as Function)
                .run(new TraceContext())

        then:
        thrown(Exception)

        and:
        def spans = spanExporter.finishedSpanItems
        def nodeSpan = spans.find { it.name == "dagflow.node.fail" }
        nodeSpan != null
        nodeSpan.status.statusCode == StatusCode.ERROR
        nodeSpan.events.any { it.name == "exception" }
    }

    // 并行节点各自有独立 Span
    def "parallel nodes create separate spans"() {
        when:
        new JobBuilder<TraceContext>()
                .node("a", { c -> "a" } as Function)
                .node("b", { c -> "b" } as Function)
                .node("c", { c -> "c" } as Function)
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan.attributes.get(DagFlowTracing.ATTR_NODE_COUNT) == 3L
        def nodeSpans = spans.findAll { it.name.startsWith("dagflow.node.") }
        nodeSpans.size() == 3
        nodeSpans.collect { it.attributes.get(DagFlowTracing.ATTR_NODE_NAME) }.sort() == ["a", "b", "c"]
    }

    // 依赖链中的 Span 都有正确的父子关系
    def "dependency chain spans are children of dag span"() {
        when:
        new JobBuilder<TraceContext>()
                .node("step1", { c -> "s1" } as Function)
                .node("step2", { c -> "s2" } as Function).depend("step1")
                .node("step3", { c -> "s3" } as Function).depend("step2")
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan != null
        dagSpan.attributes.get(DagFlowTracing.ATTR_NODE_COUNT) == 3L
        def nodeSpans = spans.findAll { it.name.startsWith("dagflow.node.") }
        nodeSpans.size() == 3
        nodeSpans.every { it.parentSpanId == dagSpan.spanContext.spanId }
    }

    // 批量命令生成节点 Span 和子项 Span
    def "batch command creates node and item spans"() {
        given:
        def request = new Test2Context()
        request.setName("hello")
        request.setParams(LongStream.range(0, 3).boxed().collect(Collectors.toList()))

        when:
        new JobBuilder<Test2Context>()
                .node(BatchJob1.class)
                .run(request)

        then:
        def spans = spanExporter.finishedSpanItems
        def nodeSpan = spans.find { it.name.startsWith("dagflow.node.") && it.attributes.get(DagFlowTracing.ATTR_NODE_TYPE) == "BatchCommand" }
        nodeSpan != null
        nodeSpan.attributes.get(DagFlowTracing.ATTR_BATCH_SIZE) == 3L

        def batchItemSpans = spans.findAll { it.name.startsWith("dagflow.batch.") }
        batchItemSpans.size() == 3
        batchItemSpans.every { it.parentSpanId == nodeSpan.spanContext.spanId }
    }

    // 虚拟线程模式下 Span 依然正确
    def "virtual threads preserve tracing context"() {
        when:
        new JobBuilder<TraceContext>()
                .useVirtualThreads()
                .node("vt1", { c -> Thread.sleep(10); "vt1" } as Function)
                .node("vt2", { c -> Thread.sleep(10); "vt2" } as Function)
                .run(new TraceContext())

        then:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        def nodeSpans = spans.findAll { it.name.startsWith("dagflow.node.") }
        nodeSpans.size() == 2
        nodeSpans.every { it.parentSpanId == dagSpan.spanContext.spanId }
    }

    // DAG 根 Span 在异常时状态为 ERROR
    def "dag span status is ERROR when execution fails"() {
        when:
        new JobBuilder<TraceContext>()
                .node("boom", { c -> throw new RuntimeException("boom") } as Function)
                .run(new TraceContext())

        then:
        thrown(Exception)

        and:
        def spans = spanExporter.finishedSpanItems
        def dagSpan = spans.find { it.name == "dagflow.run" }
        dagSpan != null
        dagSpan.status.statusCode == StatusCode.ERROR
    }

    // 无自定义 OTel 实例时，不会报错（回退到 GlobalOpenTelemetry no-op 模式）
    def "tracing is no-op without custom OpenTelemetry configured"() {
        given:
        DagFlowTracing.setOpenTelemetry(null)

        when:
        def runner = new JobBuilder<TraceContext>()
                .node("noop", { c -> "noop" } as Function)
                .run(new TraceContext())

        then:
        runner.getResult("noop") == "noop"
    }
}
