package com.github.sofn.dagflow.api.function;

import com.github.sofn.dagflow.api.CalcCommand;
import com.github.sofn.dagflow.api.context.DagFlowContext;
import lombok.AllArgsConstructor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 异步计算节点，如果没有设置使用默认线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
@AllArgsConstructor
public class ConsumerCommand<C extends DagFlowContext, R> implements CalcCommand<C, R> {

    private Consumer<C> consumer;
    private Executor executor;

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public R run(C context) {
        consumer.accept(context);
        return null;
    }
}
