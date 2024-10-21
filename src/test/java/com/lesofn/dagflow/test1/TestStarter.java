package com.lesofn.dagflow.test1;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import com.lesofn.dagflow.executor.DagFlowDefaultExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-10-29 15:46
 */
class TestStarter {

    @Test
    void test01() throws ExecutionException, InterruptedException {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobRunner<Test1Context> runner = new JobBuilder<Test1Context>()
                .addNode(Job2.class).depend(Job1.class)
                .addNode(Job3.class).depend(Job2.class)
                .run(request);

        //执行结果
        System.out.println("done");
        System.out.println(runner.getResult(Job3.class));
    }

    @Test
    void test02() throws Exception {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobRunner<Test1Context> runner = new JobBuilder<Test1Context>()
                .addNode(Job4.class)
                .run(request);

        System.out.println(runner.getResult(Job4.class));
    }

    @Test
    void test03Function() throws Exception {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobBuilder<Test1Context> builder = new JobBuilder<>();

        //依赖关系组装
        builder.funcNode("node4", c -> {
            System.out.println(c.getResult(Job1.class));
            return "hello";
        }).depend(Job1.class);

        //请求业务逻辑执行
        JobRunner<Test1Context> context;
        context = builder.run(request);
        //执行结果
        System.out.println("done");
        System.out.println((String) context.getResult("node4"));
    }

    @Test
    void test04Consumer() throws Exception {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobBuilder<Test1Context> builder = new JobBuilder<>();

        //依赖关系组装
        builder.funcNode("consumer", c -> {
            System.out.println("consumer: " + c.getName());
        }).depend(Job1.class);

        //请求业务逻辑执行
        builder.run(request);
    }

    @Test
    void test05ReRun() throws Exception {
        Test1Context request = new Test1Context();
        request.setName("hello");

        JobBuilder<Test1Context> builder = new JobBuilder<>();

        //依赖关系组装
        builder.funcNode("consumer", c -> {
            System.out.println("consumer: " + c.getName());
        }).depend(Job1.class);

        //请求业务逻辑执行
        JobRunner<Test1Context> run1 = builder.run(request);
        JobRunner<Test1Context> run2 = builder.run(request);
        System.out.println(run1.getResult(Job1.class));
        System.out.println(run2.getResult(Job1.class));
    }

    @Test
    void test06() throws Exception {
        Test1Context request = new Test1Context();
        request.setName("hello");

        Future<String> submit = DagFlowDefaultExecutor.CALC_DEFAULT_EXECUTOR.submit(() -> "hello");

        JobBuilder<Test1Context> builder = new JobBuilder<>();

        //依赖关系组装
        builder.funcNode("consumer", c -> {
            System.out.println("consumer: " + c.getName());
        }).depend(Job1.class);

        //请求业务逻辑执行
        JobRunner<Test1Context> run1 = builder.run(request);
        JobRunner<Test1Context> run2 = builder.run(request);
        System.out.println(run1.getResult(Job1.class));
        System.out.println(run2.getResult(Job1.class));
    }

}