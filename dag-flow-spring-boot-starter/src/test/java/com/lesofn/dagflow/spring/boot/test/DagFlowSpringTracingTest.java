package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import com.lesofn.dagflow.tracing.DagFlowTracing;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 环境下 OpenTelemetry 追踪集成测试
 *
 * @author sofn
 */
@SpringBootTest(classes = TestApplication.class)
class DagFlowSpringTracingTest {

    @Autowired
    ApplicationContext applicationContext;

    InMemorySpanExporter spanExporter;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        DagFlowTracing.setOpenTelemetry(otel);
    }

    @AfterEach
    void tearDown() {
        DagFlowTracing.setOpenTelemetry(null);
    }

    @Test
    void springBeanDagProducesTracingSpans() throws Exception {
        TestSpringContext context = new TestSpringContext();
        context.setName("tracing");

        JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                .addNode(SpringJob1.class)
                .dependSpringBean("springJob2")
                .run(context);

        assertEquals("springJob1_tracing", runner.getResult(SpringJob1.class));

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData dagSpan = spans.stream()
                .filter(s -> s.getName().equals("dagflow.run"))
                .findFirst().orElse(null);
        assertNotNull(dagSpan, "dagflow.run span should exist");
        assertEquals(StatusCode.OK, dagSpan.getStatus().getStatusCode());
        assertEquals(2L, dagSpan.getAttributes().get(DagFlowTracing.ATTR_NODE_COUNT));

        long nodeSpanCount = spans.stream()
                .filter(s -> s.getName().startsWith("dagflow.node."))
                .count();
        assertEquals(2, nodeSpanCount, "should have 2 node spans");

        // All node spans are children of dag span
        spans.stream()
                .filter(s -> s.getName().startsWith("dagflow.node."))
                .forEach(s -> assertEquals(dagSpan.getSpanContext().getSpanId(), s.getParentSpanId()));
    }

    @Test
    void springDagWithFuncNodeProducesSpans() throws Exception {
        TestSpringContext context = new TestSpringContext();
        context.setName("func");

        new JobBuilder<TestSpringContext>()
                .funcNode("greet", (Function<TestSpringContext, String>) c -> "hello_" + c.getName())
                .run(context);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData nodeSpan = spans.stream()
                .filter(s -> s.getName().equals("dagflow.node.greet"))
                .findFirst().orElse(null);
        assertNotNull(nodeSpan);
        assertEquals("greet", nodeSpan.getAttributes().get(DagFlowTracing.ATTR_NODE_NAME));
    }
}
