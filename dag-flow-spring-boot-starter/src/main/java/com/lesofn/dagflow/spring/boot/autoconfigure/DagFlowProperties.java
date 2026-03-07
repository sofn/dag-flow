package com.lesofn.dagflow.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dag-flow 配置属性
 *
 * @author sofn
 */
@ConfigurationProperties(prefix = "dagflow")
public class DagFlowProperties {

    /**
     * 是否启用 dag-flow 自动配置
     */
    private boolean enabled = true;

    /**
     * 是否启用 OpenTelemetry 链路追踪（需要 classpath 包含 opentelemetry-api）
     */
    private boolean tracingEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }
}
