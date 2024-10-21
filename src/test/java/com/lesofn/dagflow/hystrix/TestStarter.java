package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import com.lesofn.dagflow.model.DagNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-10-29 15:46
 */
class TestStarter {

    @Test
    void testOriginHystrix() throws Exception {
        OriginHystrixJob job = new OriginHystrixJob();
        System.out.println(job.run());
        assertEquals("OriginHystrixJobResult", job.run());
    }

    @Test
    void testHystrix01() throws ExecutionException, InterruptedException {
        System.out.println("start");
        HystrixContext request = new HystrixContext();
        request.setName("hello");

        JobRunner<HystrixContext> runner = new JobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .run(request);

        //执行结果
        System.out.println("done");
        assertEquals("OriginHystrixJobResult", runner.getResult("originHystrixJob"));
        assertEquals("OriginHystrixJobResult", runner.getHystrixResult(OriginHystrixJob.class));
    }

    @Test
    void testHystrix02() throws ExecutionException, InterruptedException {
        System.out.println("start");
        HystrixContext request = new HystrixContext();
        request.setName("hello");

        JobRunner<HystrixContext> runner = new JobBuilder<HystrixContext>()
                .addHystrixNode(OriginHystrixJob.class)
                .addHystrixNode(HystrixWrapperJob.class)
                .depend(DagNodeFactory.getClassNodeName(OriginHystrixJob.class))
                .run(request);

        //执行结果
        System.out.println("done");
        assertEquals("OriginHystrixJobResult", runner.getResult("originHystrixJob"));
        assertEquals("OriginHystrixJobResult", runner.getHystrixResult(OriginHystrixJob.class));
        assertEquals("HystrixWrapperJobResult", runner.getResult("hystrixWrapperJob"));
        assertEquals("HystrixWrapperJobResult", runner.getHystrixResult(HystrixWrapperJob.class));
    }

}