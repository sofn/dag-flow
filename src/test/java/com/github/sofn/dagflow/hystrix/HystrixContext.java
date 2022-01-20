package com.github.sofn.dagflow.hystrix;

import com.github.sofn.dagflow.api.context.DagFlowContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-11-06 20:33
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class HystrixContext extends DagFlowContext {
    private String name;
}
