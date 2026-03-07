package com.lesofn.dagflow;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.function.ConsumerCommand;
import com.lesofn.dagflow.api.function.FunctionCommand;
import com.lesofn.dagflow.exception.DagFlowBuildException;
import com.lesofn.dagflow.exception.DagFlowCycleException;
import com.lesofn.dagflow.executor.DagFlowDefaultExecutor;
import com.lesofn.dagflow.model.DagNode;
import com.lesofn.dagflow.model.DagNodeCheck;
import com.lesofn.dagflow.model.DagNodeFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 动态 DAG 构建器。
 * <p>
 * 与 {@link JobBuilder} 的区别：
 * <ul>
 *   <li>每次 addNode 必须同时传入依赖，不需要在一个地方集中维护</li>
 *   <li>每次添加节点后立即做增量环检测，发现环立即报错</li>
 *   <li>天然支持实例对象和 lambda，无需为每个节点创建 Class</li>
 * </ul>
 * <p>
 * 用法示例：
 * <pre>{@code
 * DynamicDag<MyContext> dag = new DynamicDag<>();
 * dag.addNode("a", ctx -> "resultA");
 * dag.addNode("b", ctx -> "resultB", "a");
 * dag.addNode("c", myCommand, "a", "b");
 * dag.run(context);
 * }</pre>
 *
 * @author sofn
 */
public class DynamicDag<C extends DagFlowContext> {

    private final DagNodeFactory<C> nodeFactory = new DagNodeFactory<>();

    private Executor executorOverride;

    /**
     * 添加命令实例节点，同时指定依赖
     *
     * @param name    节点名称（唯一）
     * @param command 命令实例
     * @param depends 依赖的节点名称（必须已注册）
     * @return this
     * @throws DagFlowBuildException 节点名重复或依赖不存在
     * @throws DagFlowCycleException 添加后检测到环
     */
    public DynamicDag<C> addNode(String name, DagFlowCommand<C, ?> command, String... depends) {
        checkNodeNotExists(name);
        DagNode<C, ?> node = nodeFactory.createByInstance(name, command);
        addDependsAndCheckCycle(node, depends);
        return this;
    }

    /**
     * 添加 Class 节点（通过无参构造反射创建实例），同时指定依赖
     *
     * @param commandClass 命令类
     * @param depends      依赖的节点名称（必须已注册）
     * @return this
     */
    public DynamicDag<C> addNode(Class<? extends DagFlowCommand<C, ?>> commandClass, String... depends) {
        String name = DagNodeFactory.getClassNodeName(commandClass);
        checkNodeNotExists(name);
        DagNode<C, ?> node = nodeFactory.createByClass(commandClass);
        addDependsAndCheckCycle(node, depends);
        return this;
    }

    /**
     * 添加 Function lambda 节点，同时指定依赖
     *
     * @param name     节点名称
     * @param function 函数
     * @param depends  依赖的节点名称
     * @return this
     */
    public DynamicDag<C> funcNode(String name, Function<C, ?> function, String... depends) {
        return funcNode(name, function, null, depends);
    }

    /**
     * 添加 Function lambda 节点（自定义执行器），同时指定依赖
     */
    public DynamicDag<C> funcNode(String name, Function<C, ?> function, Executor executor, String... depends) {
        checkNodeNotExists(name);
        FunctionCommand<C, ?> command = new FunctionCommand<>(function, executor);
        DagNode<C, ?> node = nodeFactory.createByNameFunction(name, command);
        addDependsAndCheckCycle(node, depends);
        return this;
    }

    /**
     * 添加 Consumer lambda 节点，同时指定依赖
     *
     * @param name     节点名称
     * @param consumer 消费者
     * @param depends  依赖的节点名称
     * @return this
     */
    public DynamicDag<C> consumerNode(String name, Consumer<C> consumer, String... depends) {
        return consumerNode(name, consumer, null, depends);
    }

    /**
     * 添加 Consumer lambda 节点（自定义执行器），同时指定依赖
     */
    public DynamicDag<C> consumerNode(String name, Consumer<C> consumer, Executor executor, String... depends) {
        checkNodeNotExists(name);
        ConsumerCommand<C, ?> command = new ConsumerCommand<>(consumer, executor);
        DagNode<C, ?> node = nodeFactory.createByNameConsumer(name, command);
        addDependsAndCheckCycle(node, depends);
        return this;
    }

    /**
     * 启用虚拟线程执行器 (Java 21+)
     */
    public DynamicDag<C> useVirtualThreads() {
        this.executorOverride = DagFlowDefaultExecutor.newVirtualThreadExecutor();
        return this;
    }

    /**
     * 获取已注册节点数
     */
    public int size() {
        return nodeFactory.getNodes().size();
    }

    /**
     * 执行 DAG
     */
    public JobRunner<C> run(C context) throws ExecutionException, InterruptedException {
        JobRunner<C> runner = new JobRunner<>();
        nodeFactory.getNodes().forEach(node -> {
            node.init();
            node.setExecutorOverride(executorOverride);
        });
        return runner.run(context, this.nodeFactory);
    }

    /**
     * 检查节点名不存在
     */
    private void checkNodeNotExists(String name) {
        try {
            nodeFactory.getByNodeName(name);
            throw new DagFlowBuildException("node already exists: " + name);
        } catch (DagFlowBuildException e) {
            if (e.getMessage().startsWith("node already exists")) {
                throw e;
            }
            // getByNodeName throws "cannot ref unbind node" when node doesn't exist — this is expected
        }
    }

    /**
     * 添加依赖并做增量环检测。如果检测到环，回滚并抛出异常。
     */
    @SuppressWarnings("unchecked")
    private void addDependsAndCheckCycle(DagNode<C, ?> node, String[] depends) {
        if (depends != null && depends.length > 0) {
            List<DagNode<C, ?>> depNodes = Arrays.stream(depends)
                    .map(depName -> {
                        try {
                            return (DagNode<C, ?>) nodeFactory.getByNodeName(depName);
                        } catch (DagFlowBuildException e) {
                            // 回滚：移除刚添加的节点
                            removeNode(node.getName());
                            throw new DagFlowBuildException("dependency not found: " + depName + " (required by node: " + node.getName() + ")");
                        }
                    })
                    .collect(Collectors.toList());
            node.addDepends(depNodes);
        }

        // 增量环检测
        List<String> cyclePath = DagNodeCheck.checkCycleOnAdd(node, nodeFactory.getNodes());
        if (!cyclePath.isEmpty()) {
            // 回滚：移除刚添加的节点及其依赖边
            removeNode(node.getName());
            throw new DagFlowCycleException("adding node '" + node.getName() + "' creates cycle: " + String.join(" -> ", cyclePath));
        }
    }

    /**
     * 从 nodeFactory 中移除节点（回滚用）
     */
    private void removeNode(String name) {
        nodeFactory.removeNode(name);
    }
}
