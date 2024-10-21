package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.api.context.DagFlowContextInjection;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import lombok.extern.slf4j.Slf4j;

/**
 * 添加依赖，并注入JobFlow的Context
 *
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class HystrixWrapperJob extends HystrixCommand<String> implements DagFlowContextInjection<HystrixContext> {

    HystrixContext context;

    public HystrixWrapperJob() {
        super(HystrixCommandGroupKey.Factory.asKey("test"));
    }


    @Override
    public void setContext(HystrixContext context) {
        this.context = context;
    }

    @Override
    protected String run() throws Exception {
        log.info("HystrixWrapperJob start");
        log.info(context.getResult("originHystrixJob"));
        return "HystrixWrapperJobResult";
    }

}
