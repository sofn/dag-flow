package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.api.BatchCommand;
import com.lesofn.dagflow.api.BatchStrategy;
import com.lesofn.dagflow.api.CalcCommand;
import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.SyncCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.executor.DagFlowDefaultExecutor;
import com.lesofn.dagflow.tracing.DagFlowTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.lambda.fi.util.function.CheckedSupplier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author sofn
 * @version 1.0 Created at: 2021-11-02 15:47
 */
@Data
@Slf4j
@EqualsAndHashCode(of = {"name"})
public class DagNode<C extends DagFlowContext, T extends DagFlowCommand<C, ?>> {

    /**
     * 名称，一次job中唯一
     */
    private String name;

    /**
     * 类
     */
    private Class<T> clazz;

    /**
     * 实例
     */
    private T instance;

    /**
     * 依赖，保存顺序
     */
    private Set<DagNode<C, ?>> depends = new LinkedHashSet<>();

    /**
     * 异步结果
     */
    private CompletableFuture<Object> future;

    /**
     * 执行器覆盖（如虚拟线程），优先级高于命令默认执行器
     */
    private Executor executorOverride;

    /**
     * OTel 父上下文，由 JobRunner 在 run 时设置
     */
    private Context parentTraceContext;

    private volatile boolean started;

    public DagNode(String name) {
        this.name = name;
    }

    public DagNode(String name, Class<T> clazz) {
        this.name = name;
        this.clazz = clazz;
    }

    public DagNode(String name, T instance) {
        this.name = name;
        this.instance = instance;
    }

    public boolean addDepend(DagNode<C, ?> depend) {
        return this.depends.add(depend);
    }

    public boolean addDepends(Collection<DagNode<C, ?>> depends) {
        return this.depends.addAll(depends);
    }

    public boolean addDepends(DagNode<C, ?>[] depends) {
        return this.depends.addAll(Arrays.asList(depends));
    }

    /**
     * 初始化，以便Builder可以复用
     */
    public void init() {
        this.started = false;
        this.future = null;
    }

    /**
     * 获取getCompletableFuture 有可能没执行
     *
     * @return CompletableFuture
     */
    public CompletableFuture<Object> getFuture() {
        if (this.future == null) {
            this.future = new CompletableFuture<>();
        }
        return this.future;
    }

    public CompletableFuture<Object> startNode(C context) {
        if (started && !this.future.isCancelled()) {
            return getFuture();
        }
        this.started = true;

        Context parentCtx = parentTraceContext != null ? parentTraceContext : Context.current();
        Span nodeSpan = DagFlowTracing.startNodeSpan(parentCtx, name, getCommandType());
        Context nodeContext = parentCtx.with(nodeSpan);

        Executor executor = getExecutor();

        if (this.instance instanceof BatchCommand) {
            try (Scope ignored = nodeSpan.makeCurrent()) {
                this.batchExecute(context, nodeContext, nodeSpan);
            }
        } else if (executor != null) {
            executor.execute(nodeContext.wrap(() -> {
                try (Scope ignored = nodeSpan.makeCurrent()) {
                    this.execute(getFuture(), CheckedSupplier.unchecked(() -> this.instance.run(context)), nodeSpan);
                }
            }));
        } else {
            try (Scope ignored = nodeSpan.makeCurrent()) {
                this.execute(getFuture(), CheckedSupplier.unchecked(() -> this.instance.run(context)), nodeSpan);
            }
        }
        return getFuture();
    }

    /**
     * 获取命令类型名称，用于 tracing attribute
     */
    String getCommandType() {
        if (instance instanceof SyncCommand) return "SyncCommand";
        if (instance instanceof BatchCommand) return "BatchCommand";
        if (instance instanceof AsyncCommand) return "AsyncCommand";
        if (instance instanceof CalcCommand) return "CalcCommand";
        return "Unknown";
    }

    private Executor getExecutor() {
        if (this.instance instanceof SyncCommand) {
            return null;
        }
        //全局执行器覆盖（如虚拟线程）
        if (executorOverride != null) {
            return executorOverride;
        }
        Executor executor = null;
        if (this.instance instanceof AsyncCommand) {
            executor = ((AsyncCommand<?, ?>) this.instance).executor();
            if (executor == null) {
                executor = DagFlowDefaultExecutor.ASYNC_DEFAULT_EXECUTOR;
            }
        } else if (this.instance instanceof CalcCommand) {
            executor = ((CalcCommand<?, ?>) this.instance).executor();
            if (executor == null) {
                executor = DagFlowDefaultExecutor.CALC_DEFAULT_EXECUTOR;
            }
        }
        return executor;
    }

    private <R> void execute(CompletableFuture<R> future, Supplier<R> supplier, Span span) {
        try {
            future.complete(supplier.get());
            DagFlowTracing.endSpanOk(span);
        } catch (Exception e) {
            log.error("DagFlow run error", e);
            future.completeExceptionally(e);
            DagFlowTracing.endSpanError(span, e);
        }
    }

    @SuppressWarnings("unchecked")
    <B extends BatchCommand<C, P, R>, P, R> void batchExecute(C context, Context nodeContext, Span nodeSpan) {
        B batchNode = (B) this.instance;
        Executor executor = getExecutor();
        BatchStrategy strategy = batchNode.batchStrategy();

        Set<P> batchParam = batchNode.batchParam(context);
        nodeSpan.setAttribute(DagFlowTracing.ATTR_BATCH_SIZE, (long) batchParam.size());

        // 校验策略：requiredCount 不能超过总参数数
        if (!strategy.isAll() && strategy.getRequiredCount() > batchParam.size()) {
            IllegalArgumentException ex = new IllegalArgumentException(
                    "BatchStrategy requiredCount(" + strategy.getRequiredCount() + ") > batchParam size(" + batchParam.size() + ")");
            getFuture().completeExceptionally(ex);
            DagFlowTracing.endSpanError(nodeSpan, ex);
            return;
        }

        List<Pair<P, CompletableFuture<R>>> childFutures = new ArrayList<>();
        // 用于 ANY / AT_LEAST_N 策略计数
        AtomicInteger completedCount = new AtomicInteger(0);

        for (P p : batchParam) {
            CompletableFuture<R> itemFuture = new CompletableFuture<>();
            childFutures.add(Pair.of(p, itemFuture));

            Span itemSpan = DagFlowTracing.startBatchItemSpan(nodeContext, name, p);
            Context itemContext = nodeContext.with(itemSpan);

            if (executor != null) {
                executor.execute(itemContext.wrap(() -> {
                    try (Scope ignored = itemSpan.makeCurrent()) {
                        this.execute(itemFuture, CheckedSupplier.unchecked(() -> batchNode.run(context, p)), itemSpan);
                    }
                }));
            } else {
                try (Scope ignored = itemSpan.makeCurrent()) {
                    this.execute(itemFuture, CheckedSupplier.unchecked(() -> batchNode.run(context, p)), itemSpan);
                }
            }

            // 注册完成回调：用于 ANY / AT_LEAST_N 提前完成
            if (!strategy.isAll()) {
                itemFuture.whenComplete((result, error) -> {
                    if (error == null) {
                        int done = completedCount.incrementAndGet();
                        if (done >= strategy.getRequiredCount()) {
                            finishBatch(childFutures, nodeSpan);
                        }
                    }
                });
            }
        }

        if (strategy.isAll()) {
            // ALL 策略：等全部完成
            CompletableFuture.allOf(childFutures.stream().map(Pair::getRight).toArray(CompletableFuture[]::new)).exceptionally(e -> {
                getFuture().completeExceptionally(e);
                DagFlowTracing.endSpanError(nodeSpan, e);
                return null;
            }).thenRun(() -> {
                if (childFutures.stream().map(Pair::getRight).noneMatch(CompletableFuture::isCompletedExceptionally)) {
                    finishBatch(childFutures, nodeSpan);
                }
            });
        } else {
            // ANY / AT_LEAST_N 策略：任一子任务异常也需要处理
            for (Pair<P, CompletableFuture<R>> pair : childFutures) {
                pair.getRight().whenComplete((result, error) -> {
                    if (error != null && !getFuture().isDone()) {
                        // 全部子任务完成/失败后仍未达标，则传播异常
                        boolean allDone = childFutures.stream().map(Pair::getRight)
                                .allMatch(f -> f.isDone() || f.isCancelled());
                        if (allDone && completedCount.get() < strategy.getRequiredCount()) {
                            getFuture().completeExceptionally(error);
                            cancelRemaining(childFutures);
                            DagFlowTracing.endSpanError(nodeSpan, error);
                        }
                    }
                });
            }
        }
    }

    /**
     * 收集已完成的子任务结果，取消未完成的，完成节点 Future
     */
    @SuppressWarnings("unchecked")
    private <P, R> void finishBatch(List<Pair<P, CompletableFuture<R>>> childFutures, Span nodeSpan) {
        if (getFuture().isDone()) {
            return;
        }
        Map<P, R> map = new HashMap<>();
        for (Pair<P, CompletableFuture<R>> pair : childFutures) {
            CompletableFuture<R> f = pair.getRight();
            if (f.isDone() && !f.isCompletedExceptionally() && !f.isCancelled()) {
                try {
                    map.put(pair.getLeft(), f.get());
                } catch (Exception e) {
                    log.error("get future value error", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (getFuture().complete(map)) {
            cancelRemaining(childFutures);
            DagFlowTracing.endSpanOk(nodeSpan);
        }
    }

    /**
     * 取消所有未完成的子任务
     */
    private <P, R> void cancelRemaining(List<Pair<P, CompletableFuture<R>>> childFutures) {
        for (Pair<P, CompletableFuture<R>> pair : childFutures) {
            CompletableFuture<R> f = pair.getRight();
            if (!f.isDone()) {
                f.cancel(true);
            }
        }
    }

    public void cancel() {
        if (future != null && !future.isCancelled() && !future.isDone() && !future.isCompletedExceptionally()) {
            log.info("DagFlow canceled: " + this.name);
            future.cancel(true);
        }
    }
}
