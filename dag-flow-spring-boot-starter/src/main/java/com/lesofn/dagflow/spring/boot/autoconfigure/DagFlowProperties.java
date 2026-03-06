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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
