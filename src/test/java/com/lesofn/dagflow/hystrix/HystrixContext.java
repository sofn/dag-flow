package com.lesofn.dagflow.hystrix;

import com.lesofn.dagflow.api.context.DagFlowContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2020-11-06 20:33
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class HystrixContext extends DagFlowContext {
    private String name;
}
