package com.lesofn.dagflow.test2;

import com.lesofn.dagflow.api.AsyncCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class CommonJob1 implements AsyncCommand<Test2Context, Long> {

    @Override
    public Long run(Test2Context context) {
        log.info("job1 start");
        return System.currentTimeMillis();
    }
}
