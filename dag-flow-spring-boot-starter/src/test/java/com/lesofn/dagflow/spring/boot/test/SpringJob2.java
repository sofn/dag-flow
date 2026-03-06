package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.api.SyncCommand;
import org.springframework.stereotype.Component;

/**
 * @author sofn
 */
@Component
public class SpringJob2 implements SyncCommand<TestSpringContext, String> {

    @Override
    public String run(TestSpringContext context) {
        return "springJob2_" + context.getName();
    }
}
