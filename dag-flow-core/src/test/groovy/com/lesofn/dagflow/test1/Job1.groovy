package com.lesofn.dagflow.test1

import com.lesofn.dagflow.api.AsyncCommand
import groovy.util.logging.Slf4j

@Slf4j
class Job1 implements AsyncCommand<Test1Context, Long> {
    @Override
    Long run(Test1Context context) {
        log.info("job1 start")
        return System.currentTimeMillis()
    }
}
