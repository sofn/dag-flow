package com.github.sofn.dagflow.api.context;

import com.github.sofn.dagflow.JobRunner;
import com.github.sofn.dagflow.api.DagFlowCommand;
import lombok.Setter;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-11-06 20:20
 */
@Setter
public abstract class DagFlowContext {

    private JobRunner<? extends DagFlowContext> runner;

    public <C extends DagFlowContext, T extends DagFlowCommand<C, R>, R> R getResult(Class<T> clazz) {
        return runner.getResultNow(clazz);
    }

    public <C extends DagFlowContext, T extends DagFlowCommand<C, R>, R> R getResult(String nodeName) {
        return null;
    }


}
