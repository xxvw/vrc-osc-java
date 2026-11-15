package io.github.xxvw.vrcosc.transport;

import io.github.xxvw.vrcosc.OscTransportException;
import io.github.xxvw.vrcosc.osc.OscCodec;
import io.github.xxvw.vrcosc.osc.OscPacket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Objects;

/** Sends OSC packets over UDP. */
public final class UdpOscSender implements AutoCloseable {
    private final DatagramSocket socket;
    private final InetSocketAddress destination;
    private boolean closed;

    /** Creates a sender for the destination. */
    public UdpOscSender(InetSocketAddress destination) {
        this.destination = validateDestination(destination);
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException exception) {
            throw new OscTransportException("Could not open an OSC sender socket", exception);
        }
    }

    /** Sends one packet. */
    public synchronized void send(OscPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (closed) {
            throw new OscTransportException("OSC sender is closed");
        }
        byte[] bytes = OscCodec.encode(packet);
        DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, destination);
        try {
            socket.send(datagram);
        } catch (IOException exception) {
            throw new OscTransportException("Could not send OSC packet", exception);
        }
    }

    /** Returns whether this sender has been closed. */
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Closes the sender. Calling this method repeatedly has no effect. */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            socket.close();
        }
    }

    private static InetSocketAddress validateDestination(InetSocketAddress destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.getPort() < 1 || destination.getPort() > 65_535) {
            throw new IllegalArgumentException("destination port must be between 1 and 65535");
        }
        if (destination.isUnresolved()) {
            throw new IllegalArgumentException("destination host could not be resolved");
        }
        return destination;
    }
}
