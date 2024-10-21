package com.lesofn.dagflow.hystrix;

import com.netflix.hystrix.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 原生Hystrix
 *
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class OriginHystrixJob extends HystrixCommand<String> {

    public OriginHystrixJob() {
        super(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("test"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("TestThreadPool"))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(2))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(10))
        );
    }

    @Override
    protected String run() throws Exception {
        log.info("job1 start");
        return "OriginHystrixJobResult";
    }

}
