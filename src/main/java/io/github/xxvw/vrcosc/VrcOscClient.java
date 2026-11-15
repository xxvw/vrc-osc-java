package io.github.xxvw.vrcosc;

import io.github.xxvw.vrcosc.osc.OscArgument;
import io.github.xxvw.vrcosc.osc.OscMessage;
import io.github.xxvw.vrcosc.osc.OscPacket;
import io.github.xxvw.vrcosc.transport.OscListener;
import io.github.xxvw.vrcosc.transport.UdpOscReceiver;
import io.github.xxvw.vrcosc.transport.UdpOscSender;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A synchronous OSC sender with callback-based receive support and VRChat helpers. */
public final class VrcOscClient implements AutoCloseable {
    /** VRChat's default OSC receive port. */
    public static final int DEFAULT_SEND_PORT = 9_000;

    /** VRChat's default OSC sender port. */
    public static final int DEFAULT_RECEIVE_PORT = 9_001;

    /** Default VRChat OSC host. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default VRChat avatar parameter address prefix. */
    public static final String AVATAR_PARAMETERS_PREFIX = "/avatar/parameters/";

    /** VRChat avatar change address. */
    public static final String AVATAR_CHANGE_ADDRESS = "/avatar/change";

    /** VRChat chatbox input address. */
    public static final String CHATBOX_INPUT_ADDRESS = "/chatbox/input";

    /** VRChat chatbox typing address. */
    public static final String CHATBOX_TYPING_ADDRESS = "/chatbox/typing";

    private final UdpOscSender sender;
    private final InetSocketAddress receiveAddress;
    private final CopyOnWriteArrayList<OscListener> listeners = new CopyOnWriteArrayList<>();
    private final Object lifecycleLock = new Object();
    private volatile Consumer<OscException> errorHandler;
    private boolean closed;
    private UdpOscReceiver receiver;

    private VrcOscClient(Builder builder) {
        InetSocketAddress destination = resolve(builder.host, builder.sendPort);
        this.receiveAddress = resolve(builder.bindAddress, builder.receivePort);
        this.listeners.addAll(builder.listeners);
        this.errorHandler = builder.errorHandler;
        this.sender = new UdpOscSender(destination);
    }

    /** Creates a client builder using VRChat's default ports and loopback address. */
    public static Builder builder() {
        return new Builder();
    }

    /** Starts receiving OSC packets. Sending does not require this call. */
    public void start() {
        synchronized (lifecycleLock) {
            if (closed) {
                throw new OscTransportException("VRChat OSC client is closed");
            }
            if (receiver != null && receiver.isRunning()) {
                return;
            }
            UdpOscReceiver newReceiver =
                    new UdpOscReceiver(receiveAddress, this::dispatch, this::reportError);
            newReceiver.start();
            receiver = newReceiver;
        }
    }

    /** Sends an OSC message to the configured destination. */
    public void send(String address, OscArgument... arguments) {
        send(OscMessage.of(address, arguments));
    }

    /** Sends an OSC packet to the configured destination. */
    public void send(OscPacket packet) {
        sender.send(packet);
    }

    /** Sends a VRChat avatar parameter as a float. */
    public void sendAvatarParameter(String name, float value) {
        send(AVATAR_PARAMETERS_PREFIX + requireParameterName(name), OscArgument.float32(value));
    }

    /** Sends a VRChat avatar parameter as an integer. */
    public void sendAvatarParameter(String name, int value) {
        send(AVATAR_PARAMETERS_PREFIX + requireParameterName(name), OscArgument.int32(value));
    }

    /** Sends a VRChat avatar parameter as a boolean. */
    public void sendAvatarParameter(String name, boolean value) {
        send(AVATAR_PARAMETERS_PREFIX + requireParameterName(name), OscArgument.bool(value));
    }

    /** Sends Chatbox text with a notification sound and immediate submission. */
    public void sendChatbox(String text, boolean immediate) {
        sendChatbox(text, immediate, true);
    }

    /** Sends Chatbox text, optionally submitting it immediately and playing the notification sound. */
    public void sendChatbox(String text, boolean immediate, boolean notification) {
        requireChatboxText(text);
        send(
                CHATBOX_INPUT_ADDRESS,
                OscArgument.string(text),
                OscArgument.bool(immediate),
                OscArgument.bool(notification));
    }

    /** Sends the VRChat avatar change command. */
    public void sendAvatarChange(String avatarId) {
        send(AVATAR_CHANGE_ADDRESS, OscArgument.string(requireSingleLineValue(avatarId, "avatarId")));
    }

    /** Toggles VRChat's Chatbox typing indicator. */
    public void setTyping(boolean typing) {
        send(CHATBOX_TYPING_ADDRESS, OscArgument.bool(typing));
    }

    /** Adds a listener for received packets. */
    public void addListener(OscListener listener) {
        ensureOpen();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes a listener. */
    public void removeListener(OscListener listener) {
        listeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    /** Replaces the handler for malformed packets, transport errors, and listener failures. */
    public void setErrorHandler(Consumer<OscException> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /** Returns the configured or currently bound receive port. */
    public int receivePort() {
        synchronized (lifecycleLock) {
            return receiver == null ? receiveAddress.getPort() : receiver.localPort();
        }
    }

    /** Returns whether the receive thread is running. */
    public boolean isReceiving() {
        synchronized (lifecycleLock) {
            return receiver != null && receiver.isRunning();
        }
    }

    /** Closes the sender and receiver. Calling this repeatedly has no effect. */
    @Override
    public void close() {
        UdpOscReceiver receiverToClose;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            receiverToClose = receiver;
            receiver = null;
        }
        if (receiverToClose != null) {
            receiverToClose.close();
        }
        sender.close();
    }

    private void dispatch(OscPacket packet) {
        for (OscListener listener : listeners) {
            try {
                listener.onPacket(packet);
            } catch (RuntimeException exception) {
                reportError(new OscTransportException("OSC listener failed", exception));
            }
        }
    }

    private void reportError(OscException exception) {
        Consumer<OscException> handler = errorHandler;
        try {
            handler.accept(exception);
        } catch (RuntimeException ignored) {
            // Error handlers must not stop the receiver.
        }
    }

    private void ensureOpen() {
        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("VRChat OSC client is closed");
            }
        }
    }

    private static InetSocketAddress resolve(String host, int port) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("Could not resolve host: " + host);
        }
        return address;
    }

    private static String requireParameterName(String name) {
        String value = requireSingleLineValue(name, "name");
        if (value.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Avatar parameter name cannot contain '/'");
        }
        return value;
    }

    private static String requireSingleLineValue(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(name + " cannot contain control characters");
            }
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private static void requireChatboxText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.codePointCount(0, text.length()) > 144) {
            throw new IllegalArgumentException("Chatbox text cannot exceed 144 Unicode code points");
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        if (lines > 9) {
            throw new IllegalArgumentException("Chatbox text cannot exceed 9 explicit lines");
        }
    }

    /** Configures a {@link VrcOscClient}. */
    public static final class Builder {
        private String host = DEFAULT_HOST;
        private int sendPort = DEFAULT_SEND_PORT;
        private String bindAddress = DEFAULT_HOST;
        private int receivePort = DEFAULT_RECEIVE_PORT;
        private final List<OscListener> listeners = new CopyOnWriteArrayList<>();
        private Consumer<OscException> errorHandler = ignored -> {};

        private Builder() {}

        /** Sets the destination host. */
        public Builder host(String host) {
            this.host = requireHost(host);
            return this;
        }

        /** Sets VRChat's OSC receive port. */
        public Builder sendPort(int sendPort) {
            if (sendPort < 1 || sendPort > 65_535) {
                throw new IllegalArgumentException("sendPort must be between 1 and 65535");
            }
            this.sendPort = sendPort;
            return this;
        }

        /** Sets the host used to bind the receiver. */
        public Builder bindAddress(String bindAddress) {
            this.bindAddress = requireHost(bindAddress);
            return this;
        }

        /** Sets the receiver port; zero selects an ephemeral port. */
        public Builder receivePort(int receivePort) {
            if (receivePort < 0 || receivePort > 65_535) {
                throw new IllegalArgumentException("receivePort must be between 0 and 65535");
            }
            this.receivePort = receivePort;
            return this;
        }

        /** Adds an initial receive listener. */
        public Builder listener(OscListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /** Sets the handler for malformed packets, transport errors, and listener failures. */
        public Builder errorHandler(Consumer<OscException> errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        /** Creates the client. */
        public VrcOscClient build() {
            return new VrcOscClient(this);
        }

        private static String requireHost(String host) {
            Objects.requireNonNull(host, "host");
            if (host.isBlank()) {
                throw new IllegalArgumentException("host must not be blank");
            }
            return host;
        }
    }
}
