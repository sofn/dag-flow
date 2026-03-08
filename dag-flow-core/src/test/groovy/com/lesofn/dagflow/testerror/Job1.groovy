package com.lesofn.dagflow.testerror

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import groovy.util.logging.Slf4j

@Slf4j
class Job1 implements AsyncCommand<Test1Context, String> {
    @Override
    String run(Test1Context context) {
        log.info("job1 start")
        return "job1"
    }
}
