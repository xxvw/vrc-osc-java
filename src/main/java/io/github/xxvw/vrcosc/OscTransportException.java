package io.github.xxvw.vrcosc;

/** Indicates an UDP socket or receiver failure. */
public final class OscTransportException extends OscException {
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message. */
    public OscTransportException(String message) {
        super(message);
    }

    /** Creates an exception with a message and cause. */
    public OscTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
