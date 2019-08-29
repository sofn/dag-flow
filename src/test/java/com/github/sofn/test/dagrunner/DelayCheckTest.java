package com.github.sofn.test.dagrunner;

import com.github.sofn.dagrunner.DagRunner;
import com.github.sofn.test.dagrunner.example.Job1;
import com.github.sofn.test.dagrunner.example.Job2;
import org.junit.Test;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class DelayCheckTest {

    @Test
    public void testDelayCheck01() {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.registerJob(job2.addDepend(job1));
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

    @Test
    public void testDelayCheck02() {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        runner.registerJob(job2.addDepend(Job1.class));
        runner.registerJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }

    @Test
    public void testDelayCheck03() {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        job1.addDepend("myjob1", new Job1().addDepend("myjob2", new Job1()));
        Job2 job2 = new Job2();
        runner.registerJob(job2.addDepend(Job1.class));
        runner.registerJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job2));
    }
}
