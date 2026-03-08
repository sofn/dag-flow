package com.lesofn.dagflow;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.function.ConsumerCommand;
import com.lesofn.dagflow.api.function.FunctionCommand;
import com.lesofn.dagflow.exception.DagFlowBuildException;
import com.lesofn.dagflow.executor.DagFlowDefaultExecutor;
import com.lesofn.dagflow.model.DagNode;
import com.lesofn.dagflow.model.DagNodeFactory;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:42
 */
public class JobBuilder<C extends DagFlowContext> {

    protected final DagNodeFactory<C> nodeFactory = new DagNodeFactory<>();

    protected DagNode<C, ?> currentNode;

    /**
     * 全局执行器覆盖，用于虚拟线程等场景
     */
    protected Executor executorOverride;

    /**
     * Whether replay recording is enabled
     */
    protected boolean replayEnabled;

    // ======================== Class-based node registration ========================

    /**
     * Register a node by command class with auto-generated name (className#0, #1, …).
     */
    public JobBuilder<C> node(Class<? extends DagFlowCommand<C, ?>> commandClass) {
        currentNode = nodeFactory.createByClassAutoName(commandClass);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    /**
     * Register a node by command class with explicit name.
     */
    public JobBuilder<C> node(String nodeName, Class<? extends DagFlowCommand<C, ?>> commandClass) {
        currentNode = nodeFactory.createByClass(nodeName, commandClass);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    // ======================== Instance-based node registration ========================

    /**
     * Register a node by command instance with explicit name.
     */
    public JobBuilder<C> node(String nodeName, DagFlowCommand<C, ?> command) {
        currentNode = nodeFactory.createByInstance(nodeName, command);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    // ======================== Function-based node registration ========================

    /**
     * Register a Function node with auto-generated name (node#0, node#1, …).
     */
    public JobBuilder<C> node(Function<C, ?> function) {
        return this.node(function, (Executor) null);
    }

    /**
     * Register a Function node with auto-generated name and custom executor.
     */
    public JobBuilder<C> node(Function<C, ?> function, Executor executor) {
        String nodeName = nodeFactory.generateFuncNodeName();
        return this.node(nodeName, function, executor);
    }

    /**
     * Register a Function node with explicit name.
     */
    public JobBuilder<C> node(String nodeName, Function<C, ?> function) {
        return this.node(nodeName, function, null);
    }

    /**
     * Register a Function node with explicit name and custom executor.
     */
    public JobBuilder<C> node(String nodeName, Function<C, ?> function, Executor executor) {
        FunctionCommand<C, ?> command = new FunctionCommand<>(function, executor);
        currentNode = nodeFactory.createByNameFunction(nodeName, command);
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    // ======================== Consumer-based node registration ========================

    /**
     * Register a Consumer node with auto-generated name (node#0, node#1, …).
     */
    public JobBuilder<C> node(Consumer<C> consumer) {
        return this.node(consumer, (Executor) null);
    }

    /**
     * Register a Consumer node with auto-generated name and custom executor.
     */
    public JobBuilder<C> node(Consumer<C> consumer, Executor executor) {
        String nodeName = nodeFactory.generateFuncNodeName();
        return this.node(nodeName, consumer, executor);
    }

    /**
     * Register a Consumer node with explicit name.
     */
    public JobBuilder<C> node(String nodeName, Consumer<C> consumer) {
        return this.node(nodeName, consumer, null);
    }

    /**
     * Register a Consumer node with explicit name and custom executor.
     */
    public JobBuilder<C> node(String nodeName, Consumer<C> consumer, Executor executor) {
        ConsumerCommand<C, ?> command = new ConsumerCommand<>(consumer, executor);
        currentNode = nodeFactory.createByNameConsumer(nodeName, command);
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    // ======================== Dependency declaration ========================

    @SafeVarargs
    public final JobBuilder<C> depend(Class<? extends DagFlowCommand<C, ?>>... depends) {
        if (this.currentNode == null) {
            throw new DagFlowBuildException("please add node");
        }
        //解析Builder中定义的依赖
        this.currentNode.addDepends(Arrays.stream(depends).map(nodeFactory::findOrCreateByClass).collect(Collectors.toList()));
        return this;
    }

    public JobBuilder<C> depend(String... depends) {
        if (this.currentNode == null) {
            throw new DagFlowBuildException("please add node");
        }
        //解析Builder中定义的依赖
        this.currentNode.addDepends(Arrays.stream(depends).map(nodeFactory::getByNodeName).collect(Collectors.toList()));
        return this;
    }

    public JobBuilder<C> dependSpringBean(String... depends) {
        if (this.currentNode == null) {
            throw new DagFlowBuildException("please add node");
        }
        //解析Builder中定义的依赖
        this.currentNode.addDepends(Arrays.stream(depends).map(nodeFactory::createBySpringBean).collect(Collectors.toList()));
        return this;
    }

    // ======================== Configuration ========================

    /**
     * 启用虚拟线程执行器 (Java 21+)，所有非 SyncCommand 节点将在虚拟线程上执行
     */
    public JobBuilder<C> useVirtualThreads() {
        this.executorOverride = DagFlowDefaultExecutor.newVirtualThreadExecutor();
        return this;
    }

    /**
     * Enable replay recording. After execution, call {@code runner.getReplayRecord()}
     * to access timing data and render Gantt charts / timelines.
     */
    public JobBuilder<C> enableReplay() {
        this.replayEnabled = true;
        return this;
    }

    public JobRunner<C> run(C context) throws ExecutionException, InterruptedException {
        JobRunner<C> runner = new JobRunner<>();
        //每次运行，都执行一次初始化，重置状态
        nodeFactory.getNodes().forEach(node -> {
            node.init();
            node.setExecutorOverride(executorOverride);
        });
        return runner.run(context, this.nodeFactory, this.replayEnabled);
    }

}
