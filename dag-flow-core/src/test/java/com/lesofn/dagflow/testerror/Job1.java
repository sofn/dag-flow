package com.lesofn.dagflow.testerror;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.test1.Test1Context;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class Job1 implements AsyncCommand<Test1Context, String> {

    @Override
    public String run(Test1Context context) {
        log.info("job1 start");
        return "job1";
    }
}
