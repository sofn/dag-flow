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

    /**
     * 是否启用 replay 执行记录和 Web 端点
     */
    private boolean replayEnabled = false;

    /**
     * replay 记录缓存条数上限
     */
    private int replayCacheSize = 100;

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

    public boolean isReplayEnabled() {
        return replayEnabled;
    }

    public void setReplayEnabled(boolean replayEnabled) {
        this.replayEnabled = replayEnabled;
    }

    public int getReplayCacheSize() {
        return replayCacheSize;
    }

    public void setReplayCacheSize(int replayCacheSize) {
        this.replayCacheSize = replayCacheSize;
    }
}
