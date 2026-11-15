package io.github.xxvw.vrcosc.osc;

import java.util.List;
import java.util.Objects;

/** An immutable OSC message. */
public record OscMessage(String address, List<OscArgument> arguments) implements OscPacket {
    /** Creates an OSC message and validates its address and arguments. */
    public OscMessage {
        Objects.requireNonNull(address, "address");
        if (!address.startsWith("/") || address.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("OSC address must start with '/' and contain no NUL");
        }
        Objects.requireNonNull(arguments, "arguments");
        for (OscArgument argument : arguments) {
            Objects.requireNonNull(argument, "argument");
        }
        arguments = List.copyOf(arguments);
    }

    /** Creates a message from an address and arguments. */
    public static OscMessage of(String address, OscArgument... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        return new OscMessage(address, List.of(arguments));
    }
}
