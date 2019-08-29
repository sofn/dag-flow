package com.github.sofn.test.dagrunner;

import com.github.sofn.dagrunner.DagRunner;
import com.github.sofn.dagrunner.utils.CycleDependException;
import com.github.sofn.test.dagrunner.example.Job1;
import com.github.sofn.test.dagrunner.example.Job2;
import org.junit.Test;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class CycleCheckTest {

    @Test(expected = CycleDependException.class)
    public void testCycleCheck01() {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        job1.addDepend(job1);
        runner.putJob(job1);
        runner.startJobs();
        System.out.println(runner.get(job1));
    }

    @Test(expected = CycleDependException.class)
    public void testCycleCheck02() {
        DagRunner runner = new DagRunner();
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        job1.addDepend(job2);
        job2.addDepend(job1);
        runner.putJob(job1);
        runner.putJob(job2);
        runner.startJobs();
    }

}
