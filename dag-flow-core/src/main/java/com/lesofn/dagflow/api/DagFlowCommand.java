package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;

import java.time.Duration;

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

    /**
     * Node-level timeout. Returns null by default (no timeout).
     * Subclasses can override to declare a per-command timeout.
     *
     * @return timeout duration, or null for no timeout
     */
    default Duration timeout() {
        return null;
    }

    /**
     * Fallback method invoked when the command execution fails.
     * Returns null by default (no fallback). Subclasses can override
     * to provide a degraded/default result.
     *
     * @param context the execution context
     * @param cause   the exception that caused the failure
     * @return fallback result, or null if no fallback
     */
    default R fallback(C context, Throwable cause) {
        return null;
    }

    /**
     * Whether this command has a fallback defined.
     * Used internally to distinguish "fallback returned null" from "no fallback defined".
     *
     * @return true if fallback is overridden
     */
    default boolean hasFallback() {
        return false;
    }

}
