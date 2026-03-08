package com.lesofn.dagflow.test1

import com.lesofn.dagflow.api.AsyncCommand
import groovy.util.logging.Slf4j

@Slf4j
class Job2 implements AsyncCommand<Test1Context, String> {
    @Override
    String run(Test1Context context) {
        log.info("job2 start")
        log.info("depend job1 res:" + context.getResult(Job1.class))
        return "job2"
    }
}
