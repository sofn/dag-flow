package com.lesofn.dagflow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Legacy default executor holder.
 * <p>
 * The mutable {@code public static} fields are replaced by {@code final}
 * references to a single shared {@link DagFlowExecutor}. New code should
 * create a per-DAG {@link DagFlowExecutor} via {@link DagFlowExecutor#defaultExecutor()}.
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:59
 */
public class DagFlowDefaultExecutor {

    /**
     * 默认异步执行线程池（不可重新赋值，保留以兼容历史代码）
     */
    public static final ExecutorService ASYNC_DEFAULT_EXECUTOR;

    /**
     * 默认计算执行线程池（不可重新赋值，保留以兼容历史代码）
     */
    public static final ExecutorService CALC_DEFAULT_EXECUTOR;

    static {
        DagFlowExecutor defaultExecutor = DagFlowExecutor.defaultExecutor();
        ASYNC_DEFAULT_EXECUTOR = defaultExecutor.asyncExecutor();
        CALC_DEFAULT_EXECUTOR = defaultExecutor.calcExecutor();
    }

    /**
     * 创建虚拟线程执行器 (Java 21+)
     */
    public static ExecutorService newVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
