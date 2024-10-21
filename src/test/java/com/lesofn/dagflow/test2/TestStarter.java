package com.lesofn.dagflow.test2;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 15:46
 */
class TestStarter {

    @Test
    void testBatch01() throws ExecutionException, InterruptedException {
        Test2Context request = new Test2Context();
        request.setName("hello");
        request.setParams(LongStream.range(0, 100).boxed().collect(Collectors.toList()));

        JobRunner<Test2Context> runner = new JobBuilder<Test2Context>()
                .addNode(BatchJob1.class)
                .addNode(CommonJob1.class).depend(BatchJob1.class)
                .run(request);

        //执行结果
        System.out.println("done");
        System.out.println(runner.getResult(BatchJob1.class));
        System.out.println(runner.getResult(CommonJob1.class));
    }

    @Test
    void testBatchError02() {
        Test2Context request = new Test2Context();
        request.setName("hello");
        request.setParams(LongStream.range(0, 2).boxed().collect(Collectors.toList()));

        try {
            JobRunner<Test2Context> runner = new JobBuilder<Test2Context>()
                    .addNode(BatchExceptionJob2.class)
                    .addNode(CommonJob1.class).depend(BatchExceptionJob2.class)
                    .run(request);

            //执行结果
            System.out.println(runner.getResult(BatchExceptionJob2.class));
            System.out.println(runner.getResult(CommonJob1.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("done");
    }

}