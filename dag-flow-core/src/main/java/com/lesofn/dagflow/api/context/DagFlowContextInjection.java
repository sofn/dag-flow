package com.lesofn.dagflow.api.context;

/**
 * DagFlow上下文注入接口
 * 支持外部命令（如Hystrix/Resilience4j等）获取DagFlow的上下文和依赖结果
 *
 * @author sofn
 * @version 1.0 Created at: 2022-01-19 12:26
 */
public interface DagFlowContextInjection<C extends DagFlowContext> {

    void setContext(C context);

}
