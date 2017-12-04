package com.github.sofn.dagrunner.utils;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 11:36
 */
public class DagRunnerException extends RuntimeException {

    public DagRunnerException(String message) {
        super(message);
    }

    public DagRunnerException(String message, Throwable cause) {
        super(message, cause);
    }

    public DagRunnerException(Throwable cause) {
        super(cause);
    }
}
