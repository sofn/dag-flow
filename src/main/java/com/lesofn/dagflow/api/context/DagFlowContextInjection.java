package com.lesofn.dagflow.api.context;

/**
 * JobFlow整合Hystrix
 * 支持添加JobFlow的依赖
 *
 * @author sofn
 * @version 1.0 Created at: 2022-01-19 12:26
 */
public interface DagFlowContextInjection<C extends DagFlowContext> {

    void setContext(C context);

}
