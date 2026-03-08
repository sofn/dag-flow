package com.lesofn.dagflow.spring.boot.autoconfigure;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.spring.SpringContextHolder;
import com.lesofn.dagflow.spring.boot.replay.DagFlowReplayController;
import com.lesofn.dagflow.spring.boot.replay.DagFlowReplayStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * dag-flow Spring Boot 自动配置
 * <p>
 * 安装后自动注册 {@link SpringContextHolder}，使 {@link JobBuilder#dependSpringBean(String...)} 开箱即用。
 *
 * @author sofn
 */
@AutoConfiguration
@ConditionalOnClass(JobBuilder.class)
@EnableConfigurationProperties(DagFlowProperties.class)
@ConditionalOnProperty(prefix = "dagflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DagFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dagflow", name = "replay-enabled", havingValue = "true", matchIfMissing = false)
    public DagFlowReplayStore dagFlowReplayStore(DagFlowProperties properties) {
        return new DagFlowReplayStore(properties.getReplayCacheSize());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dagflow", name = "replay-enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnWebApplication
    public DagFlowReplayController dagFlowReplayController(DagFlowReplayStore store) {
        return new DagFlowReplayController(store);
    }
}
