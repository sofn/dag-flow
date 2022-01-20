package com.github.sofn.dagflow.exception;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-22 11:36
 */
public class ConstructorException extends RuntimeException {

    public ConstructorException(String message) {
        super(message);
    }

    public ConstructorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConstructorException(Throwable cause) {
        super(cause);
    }
}
