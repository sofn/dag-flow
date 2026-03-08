package com.lesofn.dagflow.test2

import com.lesofn.dagflow.api.BatchCommand

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class BatchExceptionJob2 implements BatchCommand<Test2Context, Long, String> {
    @Override
    Set<Long> batchParam(Test2Context context) {
        return new HashSet<>(context.params)
    }

    @Override
    String run(Test2Context context, Long param) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(10, 100))
        System.out.println("${Thread.currentThread()}  ${System.currentTimeMillis()}")
        if (param == 1) {
            throw new RuntimeException("error----")
        }
        return param + " "
    }
}
