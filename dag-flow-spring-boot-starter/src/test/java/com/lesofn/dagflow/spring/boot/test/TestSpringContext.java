package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.api.context.DagFlowContext;
import lombok.Getter;
import lombok.Setter;

/**
 * @author sofn
 */
@Getter
@Setter
public class TestSpringContext extends DagFlowContext {
    private String name;
}
