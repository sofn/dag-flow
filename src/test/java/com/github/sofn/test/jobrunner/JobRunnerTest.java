package com.github.sofn.test.jobrunner;

import com.github.sofn.dagrunner.JobRunner;
import com.github.sofn.test.jobrunner.example.Job1;
import com.github.sofn.test.jobrunner.example.Job2;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class JobRunnerTest {

    @Test
    public void test01() throws ExecutionException, InterruptedException {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        String res = runner.doJob(job1);
        System.out.println(res);
    }

    @Test
    public void test02() throws ExecutionException, InterruptedException {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        runner.putJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job1));
    }

    @Test
    public void test03() throws ExecutionException, InterruptedException {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.putJob(job1);
        runner.putJob(job2).addDepend(job1);
        runner.startJobs();
        TimeUnit.SECONDS.sleep(3);
        System.out.println(runner.get(job1));
    }

    @Test
    public void test04() throws ExecutionException, InterruptedException {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        job1.setDelay(true);
        Job2 job2 = new Job2();
        runner.putJob(job1);
        runner.putJob(job2).addDepend(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

}
