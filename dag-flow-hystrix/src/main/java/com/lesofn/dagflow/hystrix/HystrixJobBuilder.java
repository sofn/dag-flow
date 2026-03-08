package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;
import com.lesofn.dagflow.exception.ConstructorException;
import com.lesofn.dagflow.model.DagNodeFactory;
import com.netflix.hystrix.HystrixCommand;

/**
 * Hystrix扩展的JobBuilder，提供addHystrixNode方法
 *
 * @author sofn
 */
public class HystrixJobBuilder<C extends DagFlowContext> extends JobBuilder<C> {

    /**
     * 通过HystrixCommand类添加节点
     *
     * @param commandClass HystrixCommand的类
     */
    public HystrixJobBuilder<C> addHystrixNode(Class<? extends HystrixCommand<?>> commandClass) {
        return this.addHystrixNode(DagNodeFactory.getClassNodeName(commandClass), commandClass);
    }

    /**
     * 通过HystrixCommand类和自定义名称添加节点
     *
     * @param nodeName     节点名称
     * @param commandClass HystrixCommand的类
     */
    public HystrixJobBuilder<C> addHystrixNode(String nodeName, Class<? extends HystrixCommand<?>> commandClass) {
        try {
            HystrixCommand<?> instance = commandClass.getConstructor().newInstance();
            HystrixCommandWrapper<C, ?> wrapper = new HystrixCommandWrapper<>(instance);
            this.node(nodeName, wrapper);
            //解析类中定义的依赖
            if (instance instanceof DagFlowDepend) {
                currentNode.addDepends(nodeFactory.parseDepends((DagFlowDepend<C>) instance));
            }
        } catch (ConstructorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConstructorException("please support no argument constructor", e);
        }
        return this;
    }

    /**
     * 通过HystrixCommand实例添加节点
     *
     * @param command HystrixCommand实例
     */
    public HystrixJobBuilder<C> addHystrixNode(HystrixCommand<?> command) {
        return addHystrixNode(DagNodeFactory.getClassNodeName(command.getClass()), command);
    }

    /**
     * 通过HystrixCommand实例和自定义名称添加节点
     *
     * @param nodeName 节点名称
     * @param command  HystrixCommand实例
     */
    public HystrixJobBuilder<C> addHystrixNode(String nodeName, HystrixCommand<?> command) {
        HystrixCommandWrapper<C, ?> wrapper = new HystrixCommandWrapper<>(command);
        this.node(nodeName, wrapper);
        //解析类中定义的依赖
        if (command instanceof DagFlowDepend) {
            currentNode.addDepends(nodeFactory.parseDepends((DagFlowDepend<C>) command));
        }
        return this;
    }

    /**
     * 获取Hystrix节点结果的辅助方法
     *
     * @param runner JobRunner实例
     * @param clazz  HystrixCommand的类
     * @return 节点结果
     */
    public static <T> T getHystrixResult(com.lesofn.dagflow.JobRunner<?> runner, Class<? extends HystrixCommand<T>> clazz) {
        return runner.getResult(DagNodeFactory.getClassNodeName(clazz));
    }
}
