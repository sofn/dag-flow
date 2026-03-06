package com.lesofn.dagflow.test1;

import com.lesofn.dagflow.api.AsyncCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class Job1 implements AsyncCommand<Test1Context, Long> {

    @Override
    public Long run(Test1Context context) {
        log.info("job1 start");
        return System.currentTimeMillis();
    }
}
