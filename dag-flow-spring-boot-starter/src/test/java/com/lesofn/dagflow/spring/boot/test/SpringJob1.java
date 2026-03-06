package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.api.AsyncCommand;
import org.springframework.stereotype.Component;

/**
 * @author sofn
 */
@Component
public class SpringJob1 implements AsyncCommand<TestSpringContext, String> {

    @Override
    public String run(TestSpringContext context) {
        return "springJob1_" + context.getName();
    }
}
