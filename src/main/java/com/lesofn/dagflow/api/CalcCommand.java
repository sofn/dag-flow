package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;

import java.util.concurrent.Executor;

/**
 * CPU计算节点，如果没有设置线程池则使用默认线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface CalcCommand<C extends DagFlowContext, R> extends DagFlowCommand<C, R> {

    /**
     * 线程池
     */
    default Executor executor() {
        return null;
    }

}
