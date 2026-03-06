package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.api.SyncCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.context.DagFlowContextInjection;
import com.netflix.hystrix.HystrixCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * Hystrix命令包装器，将HystrixCommand包装为DagFlow的SyncCommand
 * 使用SyncCommand是因为HystrixCommand内部管理自己的线程池
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:53
 */
@Slf4j
public class HystrixCommandWrapper<C extends DagFlowContext, R> implements SyncCommand<C, R> {

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
