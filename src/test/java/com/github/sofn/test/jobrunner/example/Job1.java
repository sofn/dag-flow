package com.github.sofn.test.jobrunner.example;

import com.github.sofn.jobrunner.JobCommand;
import org.apache.commons.lang.time.DateFormatUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-21 23:16
 */
public class Job1 extends JobCommand<String> {

    public Job1() {
        super("job1", 2000, 2);
    }

    @Override
    protected String call() throws Exception {
        System.out.println("job1 start " + DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(new Date()));
        TimeUnit.SECONDS.sleep(1);
        System.out.println("job1 return " + DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(new Date()));
        return "hello job1";
    }
}
