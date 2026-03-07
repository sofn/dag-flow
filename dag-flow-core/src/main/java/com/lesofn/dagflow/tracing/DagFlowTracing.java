package com.lesofn.dagflow.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * dag-flow OpenTelemetry 链路追踪工具类
 * <p>
 * 当 OpenTelemetry SDK 未配置时，所有操作均为 no-op，无性能开销。
 * <p>
 * 可通过 {@link #setOpenTelemetry(OpenTelemetry)} 设置自定义实例，
 * 未设置时回退到 {@link GlobalOpenTelemetry}。
 *
 * @author sofn
 */
public final class DagFlowTracing {

    public static final String INSTRUMENTATION_NAME = "dag-flow";

    // ======================== Attribute Keys ========================

    public static final AttributeKey<Long> ATTR_NODE_COUNT = AttributeKey.longKey("dagflow.node.count");
    public static final AttributeKey<String> ATTR_NODE_NAME = AttributeKey.stringKey("dagflow.node.name");
    public static final AttributeKey<String> ATTR_NODE_TYPE = AttributeKey.stringKey("dagflow.node.type");
    public static final AttributeKey<Long> ATTR_BATCH_SIZE = AttributeKey.longKey("dagflow.batch.size");
    public static final AttributeKey<String> ATTR_BATCH_PARAM = AttributeKey.stringKey("dagflow.batch.param");

    private static volatile OpenTelemetry openTelemetry;

    private DagFlowTracing() {
    }

    /**
     * 设置自定义 OpenTelemetry 实例（优先于 GlobalOpenTelemetry）。
     * 传入 null 则恢复使用 GlobalOpenTelemetry。
     */
    public static void setOpenTelemetry(OpenTelemetry otel) {
        openTelemetry = otel;
    }

    public static Tracer tracer() {
        OpenTelemetry otel = openTelemetry;
        if (otel != null) {
            return otel.getTracer(INSTRUMENTATION_NAME);
        }
        return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    /**
     * 创建 DAG 执行根 Span
     */
    public static Span startDagSpan(int nodeCount) {
        return tracer().spanBuilder("dagflow.run")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_NODE_COUNT, (long) nodeCount)
                .startSpan();
    }

    /**
     * 创建节点执行 Span（作为 parentContext 的子 Span）
     */
    public static Span startNodeSpan(Context parentContext, String nodeName, String nodeType) {
        return tracer().spanBuilder("dagflow.node." + nodeName)
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_NODE_NAME, nodeName)
                .setAttribute(ATTR_NODE_TYPE, nodeType)
                .startSpan();
    }

    /**
     * 创建批量子项 Span
     */
    public static Span startBatchItemSpan(Context parentContext, String nodeName, Object param) {
        return tracer().spanBuilder("dagflow.batch." + nodeName)
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_NODE_NAME, nodeName)
                .setAttribute(ATTR_BATCH_PARAM, String.valueOf(param))
                .startSpan();
    }

    /**
     * 成功结束 Span
     */
    public static void endSpanOk(Span span) {
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.end();
        }
    }

    /**
     * 异常结束 Span
     */
    public static void endSpanError(Span span, Throwable error) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.recordException(error);
            span.end();
        }
    }
}
