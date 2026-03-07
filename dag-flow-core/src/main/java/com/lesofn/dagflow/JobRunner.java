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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:17
 */
@Slf4j
public class JobRunner<C extends DagFlowContext> {

    private final Map<String, CompletableFuture<?>> futureMap = new HashMap<>();

    JobRunner<C> run(C context, DagNodeFactory<C> nodeFactory) throws ExecutionException, InterruptedException {
        boolean hasCycle = DagNodeCheck.hasCycle(nodeFactory.getNodes());
        if (hasCycle) {
            throw new DagFlowCycleException("不能有循环节点");
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
            CompletableFuture.allOf(this.futureMap.values().toArray(new CompletableFuture[]{})).get();
            DagFlowTracing.endSpanOk(dagSpan);
        } catch (Exception e) {
            DagFlowTracing.endSpanError(dagSpan, e);
            throw e;
        }
        return this;
    }

    public <T> T getResult(String nodeName) {
        CompletableFuture<?> future = futureMap.get(nodeName);
        return getFutureValue(nodeName, future);
    }

    public <T> T getResult(Class<? extends DagFlowCommand<?, T>> clazz) {
        String nodeName = DagNodeFactory.getClassNodeName(clazz);
        CompletableFuture<?> future = futureMap.get(nodeName);
        if (future == null) {
            throw new DagFlowRunException("节点未注册: " + nodeName);
        }
        return getFutureValue(nodeName, future);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFutureValue(String nodeName, CompletableFuture<?> future) {
        try {
            return (T) future.get();
        } catch (InterruptedException e) {
            log.error("node run Interrupted", e);
        } catch (ExecutionException e) {
            log.error("node run error", e.getCause());
        }
        log.error("node: {} run error", nodeName, new DagFlowRunException("return default value"));
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getResultNow(Class<? extends DagFlowCommand<?, T>> clazz) {
        String nodeName = DagNodeFactory.getClassNodeName(clazz);
        CompletableFuture<?> future = futureMap.get(nodeName);
        if (future == null) {
            throw new DagFlowRunException("node not register: " + nodeName);
        }
        if (future.isDone()) {
            try {
                return (T) future.getNow(null);
            } catch (Exception e) {
                log.error(nodeName + " run error", e);
                throw new DagFlowRunException("run error", e);
            }
        }
        log.error(nodeName + " run error", new DagFlowRunException("return default value"));
        throw new DagFlowRunException("getResultNow error, please add depend");
    }
}
