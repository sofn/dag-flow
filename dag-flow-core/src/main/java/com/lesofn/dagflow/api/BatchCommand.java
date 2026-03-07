package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.exception.DagFlowRunException;

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

    /**
     * 批量执行策略，子类可覆盖。
     * <ul>
     *   <li>{@link BatchStrategy#ALL} — 全部执行完成（默认）</li>
     *   <li>{@link BatchStrategy#ANY} — 至少 1 个执行完成后自动取消剩余任务</li>
     *   <li>{@link BatchStrategy#atLeast(int)} — 至少 N 个执行完成后自动取消剩余任务</li>
     * </ul>
     *
     * @return 执行策略
     */
    default BatchStrategy batchStrategy() {
        return BatchStrategy.ALL;
    }

    @Override
    default Map<P, R> run(C context) throws Exception {
        throw new DagFlowRunException("批量任务禁止执行此方法");
    }
}
