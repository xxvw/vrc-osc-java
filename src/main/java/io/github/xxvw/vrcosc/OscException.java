package io.github.xxvw.vrcosc;

/** Base runtime exception for this library. */
public class OscException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message. */
    public OscException(String message) {
        super(message);
    }

    /** Creates an exception with a message and cause. */
    public OscException(String message, Throwable cause) {
        super(message, cause);
    }
}
