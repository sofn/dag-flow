package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.api.BatchCommand;
import com.lesofn.dagflow.api.CalcCommand;
import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.hystrix.HystrixCommandWrapper;
import com.lesofn.dagflow.executor.DagFlowDefaultExecutor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.lambda.fi.util.function.CheckedSupplier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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

        Executor executor = getExecutor();

        if (this.instance instanceof BatchCommand) {
            this.executeRoute(context, getFuture());
        } else if (executor != null) {
            executor.execute(() -> this.executeRoute(context, getFuture()));
        } else {
            this.executeRoute(context, getFuture());
        }
        return getFuture();
    }

    private Executor getExecutor() {
        Executor executor = null;
        if (this.instance instanceof HystrixCommandWrapper) {
            return null;
        } else if (this.instance instanceof AsyncCommand) {
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

    @SuppressWarnings("unchecked")
    private void executeRoute(C context, CompletableFuture<Object> future) {
        if (this.instance instanceof BatchCommand) {
            this.batchExecute(context);
            return;
        }
        if (this.instance instanceof HystrixCommandWrapper) {
            HystrixCommandWrapper<C, Object> hystrixNode = (HystrixCommandWrapper<C, Object>) this.instance;
            hystrixExecute(context, hystrixNode, future);
            return;
        }
        this.execute(future, CheckedSupplier.unchecked(() -> this.instance.run(context)));
    }

    private <R> void execute(CompletableFuture<R> future, Supplier<R> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Exception e) {
            log.error("DagFlow run error", e);
            future.completeExceptionally(e);
        }
    }

    /**
     * 执行Hystrix节点
     */
    public <R> void hystrixExecute(C context, HystrixCommandWrapper<C, R> hystrixCommandWrapper, CompletableFuture<R> future) {
        try {
            future.complete(hystrixCommandWrapper.run(context));
        } catch (Exception e) {
            log.error("DagFlow run error", e);
            future.completeExceptionally(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <B extends BatchCommand<C, P, R>, P, R> void batchExecute(C context) {
        B batchNode = (B) this.instance;
        Executor executor = getExecutor();

        Set<P> batchParam = batchNode.batchParam(context);
        List<Pair<P, CompletableFuture<R>>> childFutures = new ArrayList<>();
        for (P p : batchParam) {
            CompletableFuture<R> itemFuture = new CompletableFuture<>();
            childFutures.add(Pair.of(p, itemFuture));
            if (executor != null) {
                executor.execute(() -> this.execute(itemFuture, CheckedSupplier.unchecked(() -> batchNode.run(context, p))));
            } else {
                this.execute(itemFuture, CheckedSupplier.unchecked(() -> batchNode.run(context, p)));
            }
        }

        CompletableFuture.allOf(childFutures.stream().map(Pair::getRight).toArray(CompletableFuture[]::new)).exceptionally(e -> {
            getFuture().completeExceptionally(e);
            return null;
        }).thenRun(() -> {
            if (childFutures.stream().map(Pair::getRight).noneMatch(CompletableFuture::isCompletedExceptionally)) {
                Map<P, R> map = new HashMap<>();
                for (Pair<P, CompletableFuture<R>> pair : childFutures) {
                    try {
                        map.put(pair.getLeft(), pair.getRight().get());
                    } catch (Exception e) {
                        log.error("get future value error", e);
                        Thread.currentThread().interrupt();
                    }
                }
                getFuture().complete(map);
            }
        });
    }

    public void cancel() {
        if (future != null && !future.isCancelled() && !future.isDone() && !future.isCompletedExceptionally()) {
            log.info("DagFlow canceled: " + this.name);
            future.cancel(true);
        }
    }
}
