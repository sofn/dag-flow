package com.github.sofn.dagflow;

import com.github.sofn.dagflow.api.DagFlowCommand;
import com.github.sofn.dagflow.api.context.DagFlowContext;
import com.github.sofn.dagflow.exception.DagFlowCycleException;
import com.github.sofn.dagflow.exception.DagFlowRunException;
import com.github.sofn.dagflow.model.Node;
import com.github.sofn.dagflow.model.NodeCheck;
import com.github.sofn.dagflow.model.NodeFactory;
import com.netflix.hystrix.HystrixCommand;
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

    JobRunner<C> run(C context, NodeFactory<C> nodeFactory) throws ExecutionException, InterruptedException {
        boolean hasCycle = NodeCheck.hasCycle(nodeFactory.getNodes());
        if (hasCycle) {
            throw new DagFlowCycleException("不能有循环节点");
        }

        context.setRunner(this);

        //配置所有节点依赖关系
        for (Node<C, ?> node : nodeFactory.getNodes()) {
            //注册到context
            futureMap.put(node.getName(), node.getFuture());

            if (CollectionUtils.isEmpty(node.getDepends())) {
                log.info("start node direct: " + node.getName());
                node.startNode(context);
            } else {
                CompletableFuture<?>[] depFutures = node.getDepends().stream()
                        .map(it -> it.startNode(context))
                        .toArray(CompletableFuture[]::new);

                CompletableFuture.allOf(depFutures).exceptionally(e -> {
                    node.cancel();
                    return null;
                }).thenRun(() -> node.startNode(context));
            }
        }
        CompletableFuture.allOf(this.futureMap.values().toArray(new CompletableFuture[]{})).get();
        return this;
    }

    public <T> T getResult(String nodeName) {
        CompletableFuture<?> future = futureMap.get(nodeName);
        return getFutureValue(nodeName, future);
    }

    public <T> T getHystrixResult(Class<? extends HystrixCommand<T>> clazz) {
        String nodeName = NodeFactory.getClassNodeName(clazz);
        CompletableFuture<?> future = futureMap.get(nodeName);
        if (future == null) {
            throw new DagFlowRunException("节点未注册: " + nodeName);
        }
        return getFutureValue(nodeName, future);
    }

    public <T> T getResult(Class<? extends DagFlowCommand<?, T>> clazz) {
        String nodeName = NodeFactory.getClassNodeName(clazz);
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
        String nodeName = NodeFactory.getClassNodeName(clazz);
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
