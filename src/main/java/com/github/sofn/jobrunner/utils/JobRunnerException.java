package com.github.sofn.jobrunner.utils;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-22 11:36
 */
public class JobRunnerException extends RuntimeException {

    public JobRunnerException(String message) {
        super(message);
    }

    public JobRunnerException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobRunnerException(Throwable cause) {
        super(cause);
    }
}
