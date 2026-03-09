package com.lesofn.dagflow.exception;

/**
 * Exception thrown when a DAG node or the entire DAG execution exceeds the configured timeout.
 *
 * @author sofn
 */
public class DagFlowTimeoutException extends DagFlowRunException {

    public DagFlowTimeoutException(String message) {
        super(message);
    }

    public DagFlowTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
