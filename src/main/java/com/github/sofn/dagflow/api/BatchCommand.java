package com.github.sofn.dagflow.api;

import com.github.sofn.dagflow.api.context.DagFlowContext;
import com.github.sofn.dagflow.exception.DagFlowRunException;

import java.util.Map;
import java.util.Set;

/**
 * 异步计算节点，如果没有设置使用默认线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface BatchCommand<C extends DagFlowContext, P, R> extends AsyncCommand<C, Map<P, R>> {

    /**
     * 批量参数
     *
     * @param context 上下文
     * @return 批量参数
     */
    Set<P> batchParam(C context);

    /**
     * 单个参数的执行逻辑
     *
     * @param context 上下文
     * @param param   单个参数
     * @return 单个结果
     */
    R run(C context, P param) throws Exception;

    @Override
    default Map<P, R> run(C context) throws Exception {
        throw new DagFlowRunException("批量任务禁止执行此方法");
    }
}
