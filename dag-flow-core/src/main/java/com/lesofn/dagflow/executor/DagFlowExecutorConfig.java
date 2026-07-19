package com.lesofn.dagflow.executor;

import java.util.concurrent.ExecutorService;

/**
 * Configuration for {@link DagFlowExecutor}.
 */
public class DagFlowExecutorConfig {

    private ExecutorService asyncExecutor;
    private ExecutorService calcExecutor;
    private ExecutorService syncExecutor;

    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    public ExecutorService getCalcExecutor() {
        return calcExecutor;
    }

    public ExecutorService getSyncExecutor() {
        return syncExecutor;
    }

    public DagFlowExecutorConfig asyncExecutor(ExecutorService asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        return this;
    }

    public DagFlowExecutorConfig calcExecutor(ExecutorService calcExecutor) {
        this.calcExecutor = calcExecutor;
        return this;
    }

    public DagFlowExecutorConfig syncExecutor(ExecutorService syncExecutor) {
        this.syncExecutor = syncExecutor;
        return this;
    }
}
