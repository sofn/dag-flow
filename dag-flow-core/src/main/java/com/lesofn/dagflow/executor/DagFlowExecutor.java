package com.lesofn.dagflow.executor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Per-DAG executor configuration.
 * <p>
 * Provides isolated thread pools for async, CPU-bound and synchronous nodes,
 * avoiding the global mutable static pools used by the legacy
 * {@link DagFlowDefaultExecutor}.
 */
public class DagFlowExecutor implements AutoCloseable {

    private final ExecutorService asyncExecutor;
    private final ExecutorService calcExecutor;
    private final ExecutorService syncExecutor;
    private final List<ExecutorService> ownedExecutors;

    private DagFlowExecutor(ExecutorService asyncExecutor, ExecutorService calcExecutor,
                            ExecutorService syncExecutor, List<ExecutorService> ownedExecutors) {
        this.asyncExecutor = asyncExecutor;
        this.calcExecutor = calcExecutor;
        this.syncExecutor = syncExecutor;
        this.ownedExecutors = ownedExecutors;
    }

    /**
     * Create a fresh default executor with platform thread pools.
     * <p>
     * Each call returns a new instance, so different DAGs do not share pools.
     */
    public static DagFlowExecutor defaultExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();

        ThreadPoolExecutor async = new ThreadPoolExecutor(
                processors * 2, processors * 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(processors * 16),
                new ThreadFactoryBuilder().setNameFormat("dagflow_async-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        async.allowCoreThreadTimeOut(true);

        ThreadPoolExecutor calc = new ThreadPoolExecutor(
                processors + 1, processors + 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(processors * 4),
                new ThreadFactoryBuilder().setNameFormat("dagflow_calc-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        calc.allowCoreThreadTimeOut(true);

        ThreadPoolExecutor sync = new ThreadPoolExecutor(
                0, Math.max(2, processors), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("dagflow_sync-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        List<ExecutorService> owned = List.of(async, calc, sync);
        return new DagFlowExecutor(async, calc, sync, owned);
    }

    /**
     * Create an executor backed by virtual threads for async/calc work.
     * Sync commands still run on a small platform thread pool to preserve
     * the "non-virtual" sync semantics.
     */
    public static DagFlowExecutor virtualThreads() {
        ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService calc = Executors.newVirtualThreadPerTaskExecutor();

        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor sync = new ThreadPoolExecutor(
                0, Math.max(2, processors), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("dagflow_sync-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        List<ExecutorService> owned = List.of(async, calc, sync);
        return new DagFlowExecutor(async, calc, sync, owned);
    }

    /**
     * Create an executor from a user-provided configuration.
     * Any missing executor is replaced by a freshly created default pool.
     * Pools supplied by the caller are never shut down by this executor.
     */
    public static DagFlowExecutor custom(DagFlowExecutorConfig config) {
        List<ExecutorService> owned = new ArrayList<>();

        ExecutorService async = config.getAsyncExecutor();
        if (async == null) {
            async = createDefaultAsyncExecutor();
            owned.add(async);
        }

        ExecutorService calc = config.getCalcExecutor();
        if (calc == null) {
            calc = createDefaultCalcExecutor();
            owned.add(calc);
        }

        ExecutorService sync = config.getSyncExecutor();
        if (sync == null) {
            sync = createDefaultSyncExecutor();
            owned.add(sync);
        }

        return new DagFlowExecutor(async, calc, sync, owned);
    }

    private static ExecutorService createDefaultAsyncExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                processors * 2, processors * 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(processors * 16),
                new ThreadFactoryBuilder().setNameFormat("dagflow_async-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static ExecutorService createDefaultCalcExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                processors + 1, processors + 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(processors * 4),
                new ThreadFactoryBuilder().setNameFormat("dagflow_calc-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static ExecutorService createDefaultSyncExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                0, Math.max(2, processors), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("dagflow_sync-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public ExecutorService asyncExecutor() {
        return asyncExecutor;
    }

    public ExecutorService calcExecutor() {
        return calcExecutor;
    }

    public ExecutorService syncExecutor() {
        return syncExecutor;
    }

    @Override
    public void close() {
        for (ExecutorService executor : ownedExecutors) {
            if (executor == null || executor.isShutdown()) {
                continue;
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
