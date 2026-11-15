package io.github.xxvw.vrcosc.transport;

import io.github.xxvw.vrcosc.osc.OscPacket;

/** Receives decoded OSC packets from the receiver thread. */
@FunctionalInterface
public interface OscListener {
    /** Handles one received packet. */
    void onPacket(OscPacket packet);
}
