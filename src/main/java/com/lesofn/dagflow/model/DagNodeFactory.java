package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;
import com.lesofn.dagflow.api.function.ConsumerCommand;
import com.lesofn.dagflow.api.function.FunctionCommand;
import com.lesofn.dagflow.api.hystrix.HystrixCommandWrapper;
import com.lesofn.dagflow.exception.ConstructorException;
import com.lesofn.dagflow.exception.DagFlowBuildException;
import com.lesofn.dagflow.spring.SpringContextHolder;
import com.netflix.hystrix.HystrixCommand;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.Unchecked;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author sofn
 * @version 1.0 Created at: 2021-11-02 16:06
 */
public class DagNodeFactory<C extends DagFlowContext> {
    /**
     * 构造方法缓存
     */
    private static final Map<Class<?>, Constructor<?>> CLASS_CONSTRUCTOR_MAP = new HashMap<>();

    /**
     * 注册的节点
     */
    private final Map<String, DagNode<C, ?>> nodeNameMap = new HashMap<>();

    /**
     * class转node name，首字符小写，跟Spring bean逻辑一致
     *
     * @param clazz 类
     * @return node name
     */
    public static String getClassNodeName(Class<?> clazz) {
        return StringUtils.uncapitalize(clazz.getSimpleName());
    }

    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> createByClass(Class<T> clazz) {
        String nodeName = DagNodeFactory.getClassNodeName(clazz);
        return this.createByClass(nodeName, clazz);
    }

    /**
     * 自定义名称注册
     */
    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> createByClass(String nodeName, Class<T> clazz) {
        if (nodeNameMap.containsKey(nodeName)) {
            DagNode<C, T> node = (DagNode<C, T>) nodeNameMap.get(nodeName);
            if (!Objects.equals(node.getClazz(), clazz)) {
                throw new DagFlowBuildException("cannot change node:" + nodeName + ",old:" + node.getClazz().getName() + ",new:" + clazz.getName());
            }
            return node;
        }

        try {
            DagNode<C, T> node = new DagNode<>(nodeName, clazz);
            //初始化类
            Constructor<?> constructor = CLASS_CONSTRUCTOR_MAP.computeIfAbsent(clazz, Unchecked.function(Class::getConstructor));
            T instance = (T) constructor.newInstance();
            node.setInstance(instance);
            nodeNameMap.put(nodeName, node);
            return node;
        } catch (Exception e) {
            throw new ConstructorException("please support no argument constructor", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> createBySpringBean(String beanName) {
        if (nodeNameMap.containsKey(beanName)) {
            return (DagNode<C, T>) nodeNameMap.get(beanName);
        }
        T bean = SpringContextHolder.getBean(beanName);
        DagNode<C, T> node = new DagNode<>(beanName, bean);
        nodeNameMap.put(beanName, node);
        return node;
    }

    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> createByInstance(String nodeName, T command) {
        if (nodeNameMap.containsKey(nodeName)) {
            DagNode<C, T> node = (DagNode<C, T>) nodeNameMap.get(nodeName);
            if (!Objects.equals(node.getInstance(), command)) {
                throw new DagFlowBuildException("cannot change node:" + nodeName + ",old:" + node.getInstance() + ",new:" + command);
            }
            return node;
        }
        DagNode<C, T> node = new DagNode<>(nodeName, command);
        nodeNameMap.put(nodeName, node);
        return node;
    }

    @SuppressWarnings("unchecked")
    public DagNode<C, HystrixCommandWrapper<C, ?>> createByHystrix(String nodeName, Class<? extends HystrixCommand<?>> commandClass) {
        if (nodeNameMap.containsKey(nodeName)) {
            DagNode<C, HystrixCommandWrapper<C, ?>> node = (DagNode<C, HystrixCommandWrapper<C, ?>>) nodeNameMap.get(nodeName);
            if (!Objects.equals(node.getClazz(), commandClass)) {
                throw new DagFlowBuildException("cannot change node:" + nodeName + ",old:" + node.getClazz().getName()
                        + ",new:" + commandClass.getName());
            }
            return node;
        }

        try {
            //初始化类
            Constructor<?> constructor = CLASS_CONSTRUCTOR_MAP.computeIfAbsent(commandClass, Unchecked.function(Class::getConstructor));
            HystrixCommand<?> instance = (HystrixCommand<?>) constructor.newInstance();
            //添加node
            HystrixCommandWrapper<C, ?> wrapper = new HystrixCommandWrapper<>(instance);
            DagNode<C, HystrixCommandWrapper<C, ?>> node = new DagNode<>(nodeName, wrapper);
            nodeNameMap.put(nodeName, node);
            return node;
        } catch (Exception e) {
            throw new ConstructorException("please support no argument constructor", e);
        }
    }


    @SuppressWarnings("unchecked")
    public DagNode<C, HystrixCommandWrapper<C, ?>> createByHystrix(String nodeName, HystrixCommand<?> hystrixCommand) {
        if (nodeNameMap.containsKey(nodeName)) {
            DagNode<C, ?> cNode = nodeNameMap.get(nodeName);
            if (!(cNode.getInstance() instanceof HystrixCommandWrapper)) {
                throw new DagFlowBuildException("cannot change node:" + nodeName + ",old:" + cNode.getInstance().getClass().getName()
                        + ",new:" + hystrixCommand.getClass().getName());
            }
            return (DagNode<C, HystrixCommandWrapper<C, ?>>) cNode;
        }
        DagNode<C, HystrixCommandWrapper<C, ?>> node = new DagNode<>(nodeName, new HystrixCommandWrapper<>(hystrixCommand));
        nodeNameMap.put(nodeName, node);
        return node;
    }


    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, R>, R> DagNode<C, T> getByNodeName(String nodeName) {
        if (nodeNameMap.containsKey(nodeName)) {
            return (DagNode<C, T>) nodeNameMap.get(nodeName);
        }
        throw new DagFlowBuildException("cannot ref unbind node:" + nodeName);
    }


    @SuppressWarnings("unchecked")
    public <R> DagNode<C, FunctionCommand<C, R>> createByNameFunction(String beanName, FunctionCommand<C, R> command) {
        if (nodeNameMap.containsKey(beanName)) {
            return (DagNode<C, FunctionCommand<C, R>>) nodeNameMap.get(beanName);
        }
        DagNode<C, FunctionCommand<C, R>> node = new DagNode<>(beanName, command);
        nodeNameMap.put(beanName, node);
        return node;
    }

    @SuppressWarnings("unchecked")
    public <R> DagNode<C, ConsumerCommand<C, R>> createByNameConsumer(String beanName, ConsumerCommand<C, R> command) {
        if (nodeNameMap.containsKey(beanName)) {
            return (DagNode<C, ConsumerCommand<C, R>>) nodeNameMap.get(beanName);
        }
        DagNode<C, ConsumerCommand<C, R>> node = new DagNode<>(beanName, command);
        nodeNameMap.put(beanName, node);
        return node;
    }

    public <T extends DagFlowDepend<C>> List<DagNode<C, ?>> parseDepends(T instance) {
        List<DagNode<C, ?>> result = new ArrayList<>();

        //根据class获取
        Class<? extends DagFlowCommand<C, ?>> dependNode = instance.dependNode();
        if (dependNode != null) {
            result.add(this.createByClass(dependNode));
        }
        CollectionUtils.emptyIfNull(instance.dependNodeList())
                .forEach(it -> result.add(this.createByClass(it)));

        //根据name获取
        String nodeName = instance.dependNodeName();
        if (StringUtils.isNoneBlank(nodeName)) {
            result.add(this.createBySpringBean(nodeName));
        }

        CollectionUtils.emptyIfNull(instance.dependNodeNameList())
                .forEach(it -> result.add(this.createBySpringBean(it)));

        return result;
    }

    public Collection<DagNode<C, ?>> getNodes() {
        return nodeNameMap.values();
    }
}
