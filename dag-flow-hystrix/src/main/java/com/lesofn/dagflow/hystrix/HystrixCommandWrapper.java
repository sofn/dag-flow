package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.api.SyncCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import com.lesofn.dagflow.api.context.DagFlowContextInjection;
import com.netflix.hystrix.HystrixCommand;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
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

    public static final AttributeKey<String> ATTR_COMMAND_KEY = AttributeKey.stringKey("dagflow.hystrix.command_key");
    public static final AttributeKey<String> ATTR_GROUP_KEY = AttributeKey.stringKey("dagflow.hystrix.group_key");

    @Override
    @SuppressWarnings("unchecked")
    public R run(C context) throws Exception {
        Span span = Span.current();
        span.setAttribute(ATTR_COMMAND_KEY, hystrixCommand.getCommandKey().name());
        span.setAttribute(ATTR_GROUP_KEY, hystrixCommand.getCommandGroup().name());

        if (hystrixCommand instanceof DagFlowContextInjection) {
            ((DagFlowContextInjection<C>) hystrixCommand).setContext(context);
        }
        return hystrixCommand.execute();
    }
}
