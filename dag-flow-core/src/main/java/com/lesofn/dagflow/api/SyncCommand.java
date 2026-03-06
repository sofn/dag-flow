package com.lesofn.dagflow.api;

import com.lesofn.dagflow.api.context.DagFlowContext;

/**
 * 同步执行节点
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
public interface SyncCommand<C extends DagFlowContext, R> extends DagFlowCommand<C, R> {

}
