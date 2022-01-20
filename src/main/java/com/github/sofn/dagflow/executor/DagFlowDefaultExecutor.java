package com.github.sofn.dagflow.executor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:59
 */
@Slf4j
public class DagFlowDefaultExecutor {

    /**
     * 默认异步执行线程池
     */
    public static ExecutorService ASYNC_DEFAULT_EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 8,
            5, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(Runtime.getRuntime().availableProcessors() * 16),
            new ThreadFactoryBuilder().setNameFormat("dagflow_async_default-%d").build(),
            new CallerRunsPolicyWithMonitor());

    /**
     * 默认异步执行线程池
     */
    public static ExecutorService CALC_DEFAULT_EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() + 1,
            Runtime.getRuntime().availableProcessors() + 1,
            5, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(Runtime.getRuntime().availableProcessors() * 4),
            new ThreadFactoryBuilder().setNameFormat("dagflow_calc_default-%d").build(),
            new CallerRunsPolicyWithMonitor());

    private static class CallerRunsPolicyWithMonitor extends ThreadPoolExecutor.CallerRunsPolicy {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            log.warn("CallerRunsPolicyWithMonitor.rejectedExecution");
            super.rejectedExecution(r, e);
        }
    }
}
