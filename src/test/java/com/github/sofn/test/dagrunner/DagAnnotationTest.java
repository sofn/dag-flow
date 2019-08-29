package com.github.sofn.test.dagrunner;

import com.github.sofn.dagrunner.DagRunner;
import com.github.sofn.test.dagrunner.example.Job1;
import com.github.sofn.test.dagrunner.example.Job2;
import org.junit.Test;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 15:26
 */
public class DagAnnotationTest {

    @Test
    public void test01() {
        DagRunner runner = new DagRunner();
        runner.registerJob(new Job1());
        runner.registerJob(new Job2());
        runner.startJobs();
        System.out.println(runner.get(Job2.class));
    }

}
