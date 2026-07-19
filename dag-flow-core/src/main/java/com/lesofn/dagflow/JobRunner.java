package com.lesofn.dagflow;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.exception.DagFlowCycleException;
import com.lesofn.dagflow.exception.DagFlowRunException;
import com.lesofn.dagflow.model.DagNode;
import com.lesofn.dagflow.model.DagNodeCheck;
import com.lesofn.dagflow.model.DagNodeFactory;
import com.lesofn.dagflow.tracing.DagFlowTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:17
 */
@Slf4j
public class JobRunner<C extends DagFlowContext> {

    private final Map<String, CompletableFuture<?>> futureMap = new HashMap<>();
    private final Map<Class<?>, String> classNodeNameMap = new HashMap<>();

    JobRunner<C> run(C context, DagNodeFactory<C> nodeFactory) throws ExecutionException, InterruptedException {
        return run(context, nodeFactory, 0);
    }

    JobRunner<C> run(C context, DagNodeFactory<C> nodeFactory, long timeoutMillis) throws ExecutionException, InterruptedException {
        boolean hasCycle = DagNodeCheck.hasCycle(nodeFactory.getNodes());
        if (hasCycle) {
            throw new DagFlowCycleException("Cycle detected in DAG nodes");
        }

        context.setRunner(this);

        Span dagSpan = DagFlowTracing.startDagSpan(nodeFactory.getNodes().size());
        Context dagContext = Context.current().with(dagSpan);
        try (Scope ignored = dagSpan.makeCurrent()) {
            //设置 OTel 父上下文到每个节点
            for (DagNode<C, ?> node : nodeFactory.getNodes()) {
                node.setParentTraceContext(dagContext);
            }

            //Phase 1: 注册所有节点 future，并为有依赖的节点绑定触发链
            for (DagNode<C, ?> node : nodeFactory.getNodes()) {
                futureMap.put(node.getName(), node.getFuture());
                if (node.getClazz() != null) {
                    classNodeNameMap.putIfAbsent(node.getClazz(), node.getName());
                }

                if (!CollectionUtils.isEmpty(node.getDepends())) {
                    CompletableFuture<?>[] depFutures = node.getDepends().stream()
                            .map(DagNode::getFuture)
                            .toArray(CompletableFuture[]::new);

                    CompletableFuture.allOf(depFutures).exceptionally(e -> {
                        node.cancel();
                        return null;
                    }).thenRun(() -> node.startNode(context));
                }
            }

            //Phase 2: 启动所有无依赖的根节点
            for (DagNode<C, ?> node : nodeFactory.getNodes()) {
                if (CollectionUtils.isEmpty(node.getDepends())) {
                    log.info("start node direct: " + node.getName());
                    node.startNode(context);
                }
            }
            CompletableFuture<?>[] nodeFutures = this.futureMap.values().toArray(new CompletableFuture[]{});
            if (timeoutMillis > 0) {
                try {
                    CompletableFuture.allOf(nodeFutures).get(timeoutMillis, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    throw new ExecutionException("DAG execution timed out after " + timeoutMillis + "ms", e);
                }
            } else {
                CompletableFuture.allOf(nodeFutures).get();
            }
            DagFlowTracing.endSpanOk(dagSpan);
        } catch (Exception e) {
            DagFlowTracing.endSpanError(dagSpan, e);
            throw e;
        }
        return this;
    }

    public <T> T getResult(String nodeName) {
        CompletableFuture<?> future = futureMap.get(nodeName);
        if (future == null) {
            throw new DagFlowRunException("Node not registered: " + nodeName);
        }
        return getFutureValue(nodeName, future);
    }

    public <T> T getResult(Class<? extends DagFlowCommand<?, T>> clazz) {
        String nodeName = classNodeNameMap.get(clazz);
        if (nodeName == null) {
            throw new DagFlowRunException("Node not registered: " + DagNodeFactory.getClassNodeName(clazz));
        }
        CompletableFuture<?> future = futureMap.get(nodeName);
        return getFutureValue(nodeName, future);
    }

    public Optional<Object> tryGetResult(String nodeName) {
        try {
            return Optional.ofNullable(getResult(nodeName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> Optional<T> tryGetResult(Class<? extends DagFlowCommand<?, T>> clazz) {
        try {
            return Optional.ofNullable(getResult(clazz));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getResultOrDefault(String nodeName, T defaultValue) {
        try {
            Object result = getResult(nodeName);
            return result != null ? (T) result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public <T> T getResultOrDefault(Class<? extends DagFlowCommand<?, T>> clazz, T defaultValue) {
        return tryGetResult(clazz).orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFutureValue(String nodeName, CompletableFuture<?> future) {
        try {
            return (T) future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DagFlowRunException("node interrupted: " + nodeName, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new DagFlowRunException("node run error: " + nodeName, cause);
        }
    }

    public <T> T getResultNow(String nodeName) {
        CompletableFuture<?> future = futureMap.get(nodeName);
        if (future == null) {
            throw new DagFlowRunException("Node not registered: " + nodeName);
        }
        if (!future.isDone()) {
            throw new DagFlowRunException("getResultNow error, please add depend: " + nodeName);
        }
        return getFutureValue(nodeName, future);
    }

    @SuppressWarnings("unchecked")
    public <T> T getResultNow(Class<? extends DagFlowCommand<?, T>> clazz) {
        String nodeName = classNodeNameMap.get(clazz);
        if (nodeName == null) {
            throw new DagFlowRunException("Node not registered: " + DagNodeFactory.getClassNodeName(clazz));
        }
        return getResultNow(nodeName);
    }
}
