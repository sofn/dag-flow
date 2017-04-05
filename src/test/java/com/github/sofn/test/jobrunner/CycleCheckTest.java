package com.github.sofn.test.jobrunner;

import com.github.sofn.jobrunner.JobRunner;
import com.github.sofn.jobrunner.utils.CycleDependException;
import com.github.sofn.test.jobrunner.example.Job1;
import com.github.sofn.test.jobrunner.example.Job2;
import org.junit.Test;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class CycleCheckTest {

    @Test(expected = CycleDependException.class)
    public void testCycleCheck01() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        job1.addDepend(job1);
        runner.putJob(job1);
        runner.startJobs();
        System.out.println(runner.getr(job1));
    }

    @Test(expected = CycleDependException.class)
    public void testCycleCheck02() {
        JobRunner runner = new JobRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        job1.addDepend(job2);
        job2.addDepend(job1);
        runner.putJob(job1);
        runner.putJob(job2);
        runner.startJobs();
    }

}
