package com.lesofn.dagflow.resilience4j;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.api.context.DagFlowContext;

/**
 * Resilience4j扩展的JobBuilder，提供addResilience4jNode方法
 *
 * @author sofn
 */
public class Resilience4jJobBuilder<C extends DagFlowContext> extends JobBuilder<C> {

    /**
     * 添加Resilience4j保护的节点
     *
     * @param nodeName 节点名称
     * @param command  Resilience4j命令
     */
    public Resilience4jJobBuilder<C> addResilience4jNode(String nodeName, Resilience4jCommand<C, ?> command) {
        this.node(nodeName, command);
        return this;
    }
}
