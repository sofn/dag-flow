package com.lesofn.dagflow.testerror

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import groovy.util.logging.Slf4j

@Slf4j
class JobError1 implements AsyncCommand<Test1Context, String> {
    @Override
    String run(Test1Context context) {
        log.info("JobError1 start")
        throw new RuntimeException("Something went wrong")
    }
}
