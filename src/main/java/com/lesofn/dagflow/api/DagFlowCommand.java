package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;

/**
 * 任务基类
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface DagFlowCommand<C extends DagFlowContext, R> extends DagFlowDepend<C> {

    /**
     * 计算方法
     *
     * @param context 上下文对象
     * @return 返回值
     */
    R run(C context) throws Exception;

}
