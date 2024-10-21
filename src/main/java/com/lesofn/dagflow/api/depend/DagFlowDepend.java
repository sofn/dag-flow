package com.lesofn.dagflow.api.depend;

import com.lesofn.dagflow.api.DagFlowCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;

import java.util.Collections;
import java.util.List;

/**
 * 任务依赖
 *
 * @author lishaofeng
 * @version 1.0 Created at: 2022-01-19 12:20
 */
public interface DagFlowDepend<C extends DagFlowContext> {

    /**
     * 添加单个依赖
     *
     * @return 依赖的Class
     */
    default Class<? extends DagFlowCommand<C, ?>> dependNode() {
        return null;
    }

    /**
     * 根据名称添加单个依赖
     *
     * @return 依赖的name
     */
    default String dependNodeName() {
        return null;
    }

    /**
     * 添加多个依赖
     *
     * @return 依赖Class集合
     */
    default List<Class<? extends DagFlowCommand<C, ?>>> dependNodeList() {
        return Collections.emptyList();
    }

    /**
     * 添加多个依赖,根据名称
     *
     * @return 依赖的name集合
     */
    default List<String> dependNodeNameList() {
        return Collections.emptyList();
    }

}
