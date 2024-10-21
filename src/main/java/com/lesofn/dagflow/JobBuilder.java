package com.lesofn.dagflow;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;
import com.lesofn.dagflow.api.function.ConsumerCommand;
import com.lesofn.dagflow.api.function.FunctionCommand;
import com.lesofn.dagflow.exception.DagFlowBuildException;
import com.lesofn.dagflow.model.DagNode;
import com.lesofn.dagflow.model.DagNodeFactory;
import com.netflix.hystrix.HystrixCommand;

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

    final DagNodeFactory<C> nodeFactory = new DagNodeFactory<>();

    private DagNode<C, ?> currentNode;

    /**
     * @param commandClass 要执行的job
     */
    public final JobBuilder<C> addNode(Class<? extends DagFlowCommand<C, ?>> commandClass) {
        currentNode = nodeFactory.createByClass(commandClass);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    /**
     * @param commandClass 要执行的job
     */
    public final JobBuilder<C> addNode(String nodeName, Class<? extends DagFlowCommand<C, ?>> commandClass) {
        currentNode = nodeFactory.createByClass(nodeName, commandClass);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    public final JobBuilder<C> addNode(String nodeName, DagFlowCommand<C, ?> command) {
        currentNode = nodeFactory.createByInstance(nodeName, command);
        //解析类中定义的依赖
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    /**
     * @param commandClass 要执行的job
     */
    public final JobBuilder<C> addHystrixNode(Class<? extends HystrixCommand<?>> commandClass) {
        return this.addHystrixNode(DagNodeFactory.getClassNodeName(commandClass), commandClass);
    }

    /**
     * @param commandClass 要执行的job
     */
    public final JobBuilder<C> addHystrixNode(String nodeName, Class<? extends HystrixCommand<?>> commandClass) {
        currentNode = nodeFactory.createByHystrix(nodeName, commandClass);
        //解析类中定义的依赖
        if (DagFlowDepend.class.isAssignableFrom(commandClass)) {
            currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        }
        return this;
    }

    /**
     * @param command 要执行的job
     */
    public final JobBuilder<C> addHystrixNode(HystrixCommand<?> command) {
        return addHystrixNode(DagNodeFactory.getClassNodeName(command.getClass()), command);
    }


    public final JobBuilder<C> addHystrixNode(String nodeName, HystrixCommand<?> command) {
        currentNode = nodeFactory.createByHystrix(nodeName, command);
        //解析类中定义的依赖
        if (command instanceof DagFlowDepend) {
            currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        }
        return this;
    }


    public final JobBuilder<C> funcNode(String nodeName, Function<C, ?> function) {
        this.funcNode(nodeName, function, null);
        return this;
    }

    public final JobBuilder<C> funcNode(String nodeName, Function<C, ?> function, Executor executor) {
        FunctionCommand<C, ?> command = new FunctionCommand<>(function, executor);
        currentNode = nodeFactory.createByNameFunction(nodeName, command);
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    public final JobBuilder<C> funcNode(String nodeName, Consumer<C> consumer) {
        this.funcNode(nodeName, consumer, null);
        return this;
    }

    public final JobBuilder<C> funcNode(String nodeName, Consumer<C> consumer, Executor executor) {
        ConsumerCommand<C, ?> command = new ConsumerCommand<>(consumer, executor);
        currentNode = nodeFactory.createByNameConsumer(nodeName, command);
        currentNode.addDepends(nodeFactory.parseDepends(currentNode.getInstance()));
        return this;
    }

    @SafeVarargs
    public final JobBuilder<C> depend(Class<? extends DagFlowCommand<C, ?>>... depends) {
        if (this.currentNode == null) {
            throw new DagFlowBuildException("please add node");
        }
        //解析Builder中定义的依赖
        this.currentNode.addDepends(Arrays.stream(depends).map(nodeFactory::createByClass).collect(Collectors.toList()));
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

    public JobRunner<C> run(C context) throws ExecutionException, InterruptedException {
        JobRunner<C> runner = new JobRunner<>();
        //每次运行，都执行一次初始化，重置状态
        nodeFactory.getNodes().forEach(DagNode::init);
        return runner.run(context, this.nodeFactory);
    }


}
