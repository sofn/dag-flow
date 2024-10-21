package com.lesofn.dagflow.test2;

import com.lesofn.dagflow.api.BatchCommand;
import com.google.common.collect.Sets;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author sofn
 * @version 1.0 Created at: 2021-11-04 17:22
 */
public class BatchJob1 implements BatchCommand<Test2Context, Long, String> {

    @Override
    public Set<Long> batchParam(Test2Context context) {
        return Sets.newHashSet(context.getParams());
    }

    @Override
    public String run(Test2Context context, Long param) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(10, 100));
        System.out.println(Thread.currentThread() + "  " + System.currentTimeMillis());
        return param + " ";
    }
}
