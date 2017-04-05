package com.github.sofn.jobrunner.utils;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-04-05 11:36
 */
public class CycleDependException extends JobRunnerException {

    public CycleDependException(String message) {
        super(message);
    }

    public CycleDependException(String message, Throwable cause) {
        super(message, cause);
    }

    public CycleDependException(Throwable cause) {
        super(cause);
    }
}
