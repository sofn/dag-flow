package com.lesofn.dagflow.test2

import com.lesofn.dagflow.api.AsyncCommand
import groovy.util.logging.Slf4j

@Slf4j
class CommonJob1 implements AsyncCommand<Test2Context, Long> {
    @Override
    Long run(Test2Context context) {
        log.info("job1 start")
        return System.currentTimeMillis()
    }
}
