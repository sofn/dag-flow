package com.github.sofn.dagflow.api;

import com.github.sofn.dagflow.api.context.DagFlowContext;

import java.util.concurrent.Executor;

/**
 * 异步计算节点，如果没有设置使用默认线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface AsyncCommand<C extends DagFlowContext, R> extends DagFlowCommand<C, R> {

    /**
     * 线程池
     */
    default Executor executor() {
        return null;
    }

}
