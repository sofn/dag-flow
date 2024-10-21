package com.lesofn.dagflow.test1;

import com.lesofn.dagflow.api.AsyncCommand;
import com.lesofn.dagflow.api.DagFlowCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * Node默认单例
 *
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 16:04
 */
@Slf4j
public class Job4 implements AsyncCommand<Test1Context, String> {

    @Override
    public Class<? extends DagFlowCommand<Test1Context, ?>> dependNode() {
        return Job1.class;
    }

    @Override
    public String run(Test1Context context) {
        log.info("job4 start");
        log.info("depend job1 res:" + context.getResult(Job1.class));
        return "job4 result";
    }
}
