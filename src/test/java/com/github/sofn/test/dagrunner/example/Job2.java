package com.github.sofn.test.dagrunner.example;

import com.github.sofn.dagrunner.JobCommand;
import com.github.sofn.dagrunner.annnotation.JobDepend;
import org.apache.commons.lang.time.DateFormatUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-21 23:16
 */
public class Job2 extends JobCommand<String> {

    @JobDepend(Job1.class)
    private String job1Result = depend(Job1.class);

    public Job2() {
        super("job2", 2000, 10);
    }

    @Override
    protected String call() throws Exception {
        System.out.println("job2 start " + DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(new Date()));
        System.out.println("job2 got job1 value: " + job1Result);
        TimeUnit.SECONDS.sleep(1);
        System.out.println("job2 return " + DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(new Date()));
        return "hello job2";
    }

}
