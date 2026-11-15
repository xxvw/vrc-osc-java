package io.github.xxvw.vrcosc;

/** Indicates invalid OSC data or an unsupported OSC value. */
public final class OscProtocolException extends OscException {
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message. */
    public OscProtocolException(String message) {
        super(message);
    }

    /** Creates an exception with a message and cause. */
    public OscProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
