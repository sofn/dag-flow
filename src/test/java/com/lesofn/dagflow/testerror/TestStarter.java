package com.lesofn.dagflow.testerror;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.test1.Test1Context;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 15:46
 */
class TestStarter {

    @Test
    void testError() {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobBuilder<Test1Context> builder = new JobBuilder<>();
        //依赖关系组装
        builder.addNode(Job1.class).depend(JobError1.class);
        //请求业务逻辑执行
        assertThrows(ExecutionException.class, () -> builder.run(request));
    }

}