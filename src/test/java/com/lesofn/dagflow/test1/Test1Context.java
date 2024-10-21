package com.lesofn.dagflow.test1;

import com.lesofn.dagflow.api.context.DagFlowContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author sofn
 * @version 1.0 Created at: 2020-11-06 20:33
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Test1Context extends DagFlowContext {
    private String name;
    
}
