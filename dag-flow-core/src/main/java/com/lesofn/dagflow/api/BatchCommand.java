package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.exception.DagFlowRunException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 批量执行节点，与 AsyncCommand / CalcCommand 是并列的执行模式。
 * <p>
 * 它不再继承 AsyncCommand，因为批量任务由框架按 {@link #batchStrategy()} 拆分为子任务执行，
 * 单参数 {@link #run(C, Object)} 才是业务入口，{@link #run(C)} 仅作为保护性默认实现直接抛异常。
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface BatchCommand<C extends DagFlowContext, P, R> extends DagFlowCommand<C, Map<P, R>> {

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

    /**
     * 批量子任务使用的执行器，未配置时由框架使用当前 DAG 的 async 执行器。
     */
    default Executor executor() {
        return null;
    }

    @Override
    default Map<P, R> run(C context) throws Exception {
        throw new DagFlowRunException("BatchCommand must not call run(context) directly");
    }
}
