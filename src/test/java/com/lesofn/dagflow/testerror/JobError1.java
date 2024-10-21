package com.lesofn.dagflow.testerror;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.test1.Test1Context;
import lombok.extern.slf4j.Slf4j;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class JobError1 implements AsyncCommand<Test1Context, String> {

    @Override
    public String run(Test1Context context) {
        log.info("JobError1 start");
        throw new RuntimeException("出错啦");
    }
}
