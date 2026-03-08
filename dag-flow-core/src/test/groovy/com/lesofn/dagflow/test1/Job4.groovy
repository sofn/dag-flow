package com.lesofn.dagflow.test1

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.DagFlowCommand
import groovy.util.logging.Slf4j

@Slf4j
class Job4 implements AsyncCommand<Test1Context, String> {
    @Override
    Class<? extends DagFlowCommand<Test1Context, ?>> dependNode() {
        return Job1.class
    }

    @Override
    String run(Test1Context context) {
        log.info("job4 start")
        log.info("depend job1 res:" + context.getResult(Job1.class))
        return "job4 result"
    }
}
