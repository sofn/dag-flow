package com.lesofn.dagflow.exception;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-04-05 11:36
 */
public class DagFlowCycleException extends DagFlowRunException {

    public DagFlowCycleException(String message) {
        super(message);
    }

    public DagFlowCycleException(String message, Throwable cause) {
        super(message, cause);
    }

    public DagFlowCycleException(Throwable cause) {
        super(cause);
    }
}
