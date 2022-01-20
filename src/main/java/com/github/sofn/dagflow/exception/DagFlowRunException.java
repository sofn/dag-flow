package com.github.sofn.dagflow.exception;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 11:36
 */
public class DagFlowRunException extends RuntimeException {

    public DagFlowRunException(String message) {
        super(message);
    }

    public DagFlowRunException(String message, Throwable cause) {
        super(message, cause);
    }

    public DagFlowRunException(Throwable cause) {
        super(cause);
    }
}
