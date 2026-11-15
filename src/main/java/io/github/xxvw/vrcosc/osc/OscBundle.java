package io.github.xxvw.vrcosc.osc;

import java.util.List;
import java.util.Objects;

/** An immutable OSC bundle with an NTP delivery timestamp. */
public record OscBundle(OscTimeTag timeTag, List<OscPacket> packets) implements OscPacket {
    /** Creates a bundle and validates its contents. */
    public OscBundle {
        Objects.requireNonNull(timeTag, "timeTag");
        Objects.requireNonNull(packets, "packets");
        for (OscPacket packet : packets) {
            Objects.requireNonNull(packet, "packet");
        }
        packets = List.copyOf(packets);
    }

    /** Creates an immediate bundle. */
    public static OscBundle immediate(OscPacket... packets) {
        Objects.requireNonNull(packets, "packets");
        return new OscBundle(OscTimeTag.immediate(), List.of(packets));
    }
}
