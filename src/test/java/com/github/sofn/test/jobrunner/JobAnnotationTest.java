package com.github.sofn.test.jobrunner;

import com.github.sofn.jobrunner.JobRunner;
import com.github.sofn.test.jobrunner.example.Job1;
import com.github.sofn.test.jobrunner.example.Job2;
import org.junit.Test;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class JobAnnotationTest {

    @Test
    public void test01() {
        JobRunner runner = new JobRunner();
        runner.putJob(new Job1());
        runner.putJob(new Job2());
        runner.startJobs();
        System.out.println(runner.get(Job2.class));
    }

}
