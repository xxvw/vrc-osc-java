package io.github.xxvw.vrcosc.transport;

import io.github.xxvw.vrcosc.OscException;
import io.github.xxvw.vrcosc.OscTransportException;
import io.github.xxvw.vrcosc.osc.OscCodec;
import io.github.xxvw.vrcosc.osc.OscPacket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/** Receives OSC packets over UDP and dispatches them on a daemon thread. */
public final class UdpOscReceiver implements AutoCloseable {
    private final InetSocketAddress bindAddress;
    private final OscListener listener;
    private final Consumer<OscException> errorHandler;
    private DatagramSocket socket;
    private Thread receiveThread;
    private boolean closed;

    /** Creates a receiver. The socket is opened by {@link #start()}. */
    public UdpOscReceiver(
            InetSocketAddress bindAddress,
            OscListener listener,
            Consumer<OscException> errorHandler) {
        this.bindAddress = validateBindAddress(bindAddress);
        this.listener = Objects.requireNonNull(listener, "listener");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /** Binds the socket and starts the receive thread. Calling this repeatedly has no effect. */
    public synchronized void start() {
        if (closed) {
            throw new OscTransportException("OSC receiver is closed");
        }
        if (receiveThread != null && receiveThread.isAlive()) {
            return;
        }

        DatagramSocket newSocket = null;
        try {
            newSocket = new DatagramSocket(null);
            newSocket.setReceiveBufferSize(1 << 20);
            newSocket.bind(bindAddress);
        } catch (SocketException exception) {
            if (newSocket != null) {
                newSocket.close();
            }
            throw new OscTransportException(
                    "Could not bind OSC receiver to " + bindAddress, exception);
        }

        socket = newSocket;
        receiveThread =
                new Thread(this::receiveLoop, "vrc-osc-receiver-" + newSocket.getLocalPort());
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /** Returns whether the receive thread is running. */
    public synchronized boolean isRunning() {
        return receiveThread != null && receiveThread.isAlive() && !closed;
    }

    /** Returns the bound local port, or the configured port before {@link #start()}. */
    public synchronized int localPort() {
        return socket == null ? bindAddress.getPort() : socket.getLocalPort();
    }

    /** Closes the socket and stops the receive thread. Calling this repeatedly has no effect. */
    @Override
    public void close() {
        DatagramSocket socketToClose;
        Thread threadToJoin;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            socketToClose = socket;
            threadToJoin = receiveThread;
        }

        if (socketToClose != null) {
            socketToClose.close();
        }
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(1_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[OscCodec.MAX_PACKET_SIZE];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        while (!isClosed()) {
            datagram.setLength(buffer.length);
            try {
                socket.receive(datagram);
            } catch (SocketException exception) {
                if (!isClosed()) {
                    report(new OscTransportException("OSC receive socket failed", exception));
                }
                return;
            } catch (IOException exception) {
                if (!isClosed()) {
                    report(new OscTransportException("Could not receive OSC packet", exception));
                }
                continue;
            }

            byte[] received = Arrays.copyOfRange(datagram.getData(), 0, datagram.getLength());
            try {
                OscPacket packet = OscCodec.decode(received);
                listener.onPacket(packet);
            } catch (OscException exception) {
                report(exception);
            } catch (RuntimeException exception) {
                report(new OscTransportException("OSC listener failed", exception));
            }
        }
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    private void report(OscException exception) {
        try {
            errorHandler.accept(exception);
        } catch (RuntimeException ignored) {
            // An error handler must not stop packet reception.
        }
    }

    private static InetSocketAddress validateBindAddress(InetSocketAddress bindAddress) {
        Objects.requireNonNull(bindAddress, "bindAddress");
        if (bindAddress.getPort() < 0 || bindAddress.getPort() > 65_535) {
            throw new IllegalArgumentException("receive port must be between 0 and 65535");
        }
        if (bindAddress.isUnresolved()) {
            throw new IllegalArgumentException("receive host could not be resolved");
        }
        return bindAddress;
    }
}
