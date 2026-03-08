package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.depend.DagFlowDepend;
import com.lesofn.dagflow.api.function.ConsumerCommand;
import com.lesofn.dagflow.api.function.FunctionCommand;
import com.lesofn.dagflow.exception.ConstructorException;
import com.lesofn.dagflow.exception.DagFlowBuildException;
import com.lesofn.dagflow.spring.SpringContextHolder;
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

    private static final String FUNC_NODE_PREFIX = "node";

    /**
     * 注册的节点
     */
    private final Map<String, DagNode<C, ?>> nodeNameMap = new LinkedHashMap<>();

    /**
     * class → first registered node (for class-based lookup by depend / getResult)
     */
    private final Map<Class<?>, DagNode<C, ?>> firstClassNodeMap = new LinkedHashMap<>();

    /**
     * Auto-name counters per prefix (e.g. "fetchOrder" → 0, 1, 2…)
     */
    private final Map<String, Integer> autoNameCounters = new HashMap<>();

    /**
     * Tracks classes that have been explicitly registered via node(Class).
     * Used to adopt auto-created nodes on first explicit registration.
     */
    private final Set<Class<?>> explicitlyRegistered = new HashSet<>();

    /**
     * class转node name，首字符小写，跟Spring bean逻辑一致
     *
     * @param clazz 类
     * @return node name
     */
    public static String getClassNodeName(Class<?> clazz) {
        return StringUtils.uncapitalize(clazz.getSimpleName());
    }

    /**
     * Generate auto-incremented name: prefix#0, prefix#1, …
     */
    public String generateAutoName(String prefix) {
        int count = autoNameCounters.getOrDefault(prefix, 0);
        autoNameCounters.put(prefix, count + 1);
        return prefix + "#" + count;
    }

    /**
     * Generate auto-name for functional (lambda) nodes: node#0, node#1, …
     */
    public String generateFuncNodeName() {
        return generateAutoName(FUNC_NODE_PREFIX);
    }

    /**
     * Registration with auto-generated name. Used by node(Class).
     * <p>
     * On the first explicit call for a class, if that class was already auto-created
     * by depend/parseDepends, the existing node is adopted (returned as-is).
     * Subsequent calls always create new nodes with incrementing suffixes.
     */
    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> createByClassAutoName(Class<T> clazz) {
        // Adopt existing auto-created node on first explicit registration
        if (!explicitlyRegistered.contains(clazz) && firstClassNodeMap.containsKey(clazz)) {
            explicitlyRegistered.add(clazz);
            return (DagNode<C, T>) firstClassNodeMap.get(clazz);
        }
        explicitlyRegistered.add(clazz);

        String prefix = getClassNodeName(clazz);
        String nodeName = generateAutoName(prefix);
        try {
            DagNode<C, T> node = new DagNode<>(nodeName, clazz);
            //初始化类
            Constructor<?> constructor = CLASS_CONSTRUCTOR_MAP.computeIfAbsent(clazz, Unchecked.function(Class::getConstructor));
            T instance = (T) constructor.newInstance();
            node.setInstance(instance);
            nodeNameMap.put(nodeName, node);
            firstClassNodeMap.putIfAbsent(clazz, node);
            return node;
        } catch (Exception e) {
            throw new ConstructorException("please support no argument constructor", e);
        }
    }

    /**
     * Reference lookup by class. Used by depend(Class) and parseDepends.
     * Finds existing node for the class, or auto-creates one if not found.
     * Does NOT mark the class as explicitly registered (so node(Class) can adopt later).
     */
    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> findOrCreateByClass(Class<T> clazz) {
        if (firstClassNodeMap.containsKey(clazz)) {
            return (DagNode<C, T>) firstClassNodeMap.get(clazz);
        }
        return createByClassInternal(clazz);
    }

    /**
     * Internal creation without marking as explicitly registered.
     * Used by findOrCreateByClass for auto-created dependency nodes.
     */
    @SuppressWarnings("unchecked")
    private <T extends DagFlowCommand<C, ?>> DagNode<C, T> createByClassInternal(Class<T> clazz) {
        String prefix = getClassNodeName(clazz);
        String nodeName = generateAutoName(prefix);
        try {
            DagNode<C, T> node = new DagNode<>(nodeName, clazz);
            Constructor<?> constructor = CLASS_CONSTRUCTOR_MAP.computeIfAbsent(clazz, Unchecked.function(Class::getConstructor));
            T instance = (T) constructor.newInstance();
            node.setInstance(instance);
            nodeNameMap.put(nodeName, node);
            firstClassNodeMap.putIfAbsent(clazz, node);
            return node;
        } catch (Exception e) {
            throw new ConstructorException("please support no argument constructor", e);
        }
    }

    /**
     * Find node by class (lookup only, no creation). Returns null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T extends DagFlowCommand<C, ?>> DagNode<C, T> findByClass(Class<T> clazz) {
        return (DagNode<C, T>) firstClassNodeMap.get(clazz);
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
            firstClassNodeMap.putIfAbsent(clazz, node);
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
        firstClassNodeMap.putIfAbsent(command.getClass(), node);
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
            result.add(this.findOrCreateByClass(dependNode));
        }
        CollectionUtils.emptyIfNull(instance.dependNodeList())
                .forEach(it -> result.add(this.findOrCreateByClass(it)));

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
