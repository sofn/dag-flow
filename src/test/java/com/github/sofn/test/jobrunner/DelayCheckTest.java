package com.github.sofn.test.jobrunner;

import com.github.sofn.jobrunner.JobRunner;
import com.github.sofn.test.jobrunner.example.Job1;
import com.github.sofn.test.jobrunner.example.Job2;
import org.junit.Test;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class DelayCheckTest {

    @Test
    public void testDelayCheck01() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.putJob(job2.addDepend(job1));
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

    @Test
    public void testDelayCheck02() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.putJob(job2.addDepend(Job1.class));
        runner.putJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

    @Test
    public void testDelayCheck03() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        job1.addDepend("myjob1", new Job1().addDepend("myjob2", new Job1()));
        Job2 job2 = new Job2();
        runner.putJob(job2.addDepend(Job1.class));
        runner.putJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

    @Test
    public void testDelayCheck04() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.putJob(job2.addDepend(job1));
        runner.putJob(job1.addDepend(job2));
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

}
