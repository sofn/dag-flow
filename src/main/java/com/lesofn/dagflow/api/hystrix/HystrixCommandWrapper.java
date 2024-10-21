package com.lesofn.dagflow.api.hystrix;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.context.DagFlowContextInjection;
import com.netflix.hystrix.HystrixCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步计算节点，如果没有设置使用默认线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
@Slf4j
public class HystrixCommandWrapper<C extends DagFlowContext, R> implements AsyncCommand<C, R> {

    /**
     * Hystrix命令
     */
    HystrixCommand<R> hystrixCommand;

    public HystrixCommandWrapper(HystrixCommand<R> hystrixCommand) {
        this.hystrixCommand = hystrixCommand;
    }

    @Override
    @SuppressWarnings("unchecked")
    public R run(C context) throws Exception {
        if (hystrixCommand instanceof DagFlowContextInjection) {
            ((DagFlowContextInjection<C>) hystrixCommand).setContext(context);
        }
        return hystrixCommand.execute();
    }
}
