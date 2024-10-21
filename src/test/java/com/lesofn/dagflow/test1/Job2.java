package com.lesofn.dagflow.test1;

import com.lesofn.dagflow.api.AsyncCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class Job2 implements AsyncCommand<Test1Context, String> {

    @Override
    public String run(Test1Context context) {
        log.info("job2 start");
        log.info("depend job1 res:" + context.getResult(Job1.class));
        return "job2";
    }
}
