package com.github.sofn.test.dagrunner;

import com.github.sofn.dagrunner.DagRunner;
import com.github.sofn.test.dagrunner.example.Job1;
import com.github.sofn.test.dagrunner.example.Job2;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class DagRunnerTest {

    @Test
    public void test01() throws ExecutionException, InterruptedException {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        String res = runner.doJob(job1);
        System.out.println(res);
    }

    @Test
    public void test02() throws ExecutionException, InterruptedException {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        runner.registerJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job1));
    }

    @Test
    public void test03() throws ExecutionException, InterruptedException {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.registerJob(job1);
        runner.registerJob(job2).addDepend(job1);
        runner.startJobs();
        TimeUnit.SECONDS.sleep(3);
        System.out.println(runner.get(job1));
    }

    @Test
    public void test04() throws ExecutionException, InterruptedException {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        job1.setDelay(true);
        Job2 job2 = new Job2();
        runner.registerJob(job1);
        runner.registerJob(job2).addDepend(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

}
