package com.github.sofn.dagflow.exception;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 11:36
 */
public class DagFlowBuildException extends RuntimeException {

    public DagFlowBuildException(String message) {
        super(message);
    }

    public DagFlowBuildException(String message, Throwable cause) {
        super(message, cause);
    }

    public DagFlowBuildException(Throwable cause) {
        super(cause);
    }
}
