package io.github.xxvw.vrcosc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xxvw.vrcosc.osc.OscArgument;
import io.github.xxvw.vrcosc.osc.OscCodec;
import io.github.xxvw.vrcosc.osc.OscMessage;
import io.github.xxvw.vrcosc.osc.OscPacket;
import io.github.xxvw.vrcosc.transport.OscListener;
import io.github.xxvw.vrcosc.transport.UdpOscSender;
import io.github.xxvw.vrcosc.transport.UdpOscReceiver;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class VrcOscClientTest {
    @Test
    void usesVrChatDefaultsWithoutReceiving() {
        assertEquals("127.0.0.1", VrcOscClient.DEFAULT_HOST);
        assertEquals(9_000, VrcOscClient.DEFAULT_SEND_PORT);
        assertEquals(9_001, VrcOscClient.DEFAULT_RECEIVE_PORT);

        try (VrcOscClient client = VrcOscClient.builder().build()) {
            assertEquals(9_001, client.receivePort());
            assertFalse(client.isReceiving());
        }
    }

    @Test
    void sendsVrChatHelpersWithExpectedAddressesAndArgumentTypes() throws Exception {
        try (DatagramSocket inbox = inbox();
                VrcOscClient client =
                        VrcOscClient.builder()
                                .sendPort(inbox.getLocalPort())
                                .receivePort(0)
                                .build()) {
            client.sendAvatarParameter("FloatParam", 0.5f);
            assertEquals(
                    OscMessage.of(
                            "/avatar/parameters/FloatParam", OscArgument.float32(0.5f)),
                    receive(inbox));

            client.sendAvatarParameter("IntParam", 7);
            assertEquals(
                    OscMessage.of("/avatar/parameters/IntParam", OscArgument.int32(7)),
                    receive(inbox));

            client.sendAvatarParameter("BoolParam", true);
            assertEquals(
                    OscMessage.of("/avatar/parameters/BoolParam", OscArgument.bool(true)),
                    receive(inbox));

            client.sendChatbox("hello", true, false);
            assertEquals(
                    OscMessage.of(
                            "/chatbox/input",
                            OscArgument.string("hello"),
                            OscArgument.bool(true),
                            OscArgument.bool(false)),
                    receive(inbox));

            client.sendAvatarChange("avtr_1234");
            assertEquals(
                    OscMessage.of("/avatar/change", OscArgument.string("avtr_1234")),
                    receive(inbox));

            client.setTyping(false);
            assertEquals(
                    OscMessage.of("/chatbox/typing", OscArgument.bool(false)),
                    receive(inbox));
        }
    }

    @Test
    void chatboxDefaultsToNotificationEnabled() throws Exception {
        try (DatagramSocket inbox = inbox();
                VrcOscClient client =
                        VrcOscClient.builder()
                                .sendPort(inbox.getLocalPort())
                                .receivePort(0)
                                .build()) {
            client.sendChatbox("default", true);

            assertEquals(
                    OscMessage.of(
                            "/chatbox/input",
                            OscArgument.string("default"),
                            OscArgument.bool(true),
                            OscArgument.bool(true)),
                    receive(inbox));
        }
    }

    @Test
    void validatesChatboxLengthAndExplicitLines() throws Exception {
        try (DatagramSocket inbox = inbox();
                VrcOscClient client =
                        VrcOscClient.builder()
                                .sendPort(inbox.getLocalPort())
                                .receivePort(0)
                                .build()) {
            client.sendChatbox("a".repeat(144), false);
            assertTrue(receive(inbox) instanceof OscMessage);
            client.sendChatbox("😀".repeat(144), false);
            assertTrue(receive(inbox) instanceof OscMessage);
            client.sendChatbox("line\n".repeat(8) + "line", false);
            assertTrue(receive(inbox) instanceof OscMessage);

            assertThrows(IllegalArgumentException.class, () -> client.sendChatbox("a".repeat(145), false));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendChatbox("😀".repeat(145), false));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendChatbox("line\n".repeat(9) + "line", false));
            assertThrows(NullPointerException.class, () -> client.sendChatbox(null, false));
        }
    }

    @Test
    void rejectsInvalidVrChatNamesAndBuilderValues() throws Exception {
        try (DatagramSocket inbox = inbox();
                VrcOscClient client =
                        VrcOscClient.builder()
                                .sendPort(inbox.getLocalPort())
                                .receivePort(0)
                                .build()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendAvatarParameter("", 1.0f));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendAvatarParameter(" ", 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendAvatarParameter("group/name", true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendAvatarChange("avtr\n1234"));

            assertThrows(IllegalArgumentException.class, () -> VrcOscClient.builder().sendPort(0));
            assertThrows(
                    IllegalArgumentException.class, () -> VrcOscClient.builder().sendPort(65_536));
            assertThrows(
                    IllegalArgumentException.class, () -> VrcOscClient.builder().receivePort(-1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VrcOscClient.builder().receivePort(65_536));
            assertThrows(IllegalArgumentException.class, () -> VrcOscClient.builder().host(" "));
        }
    }

    @Test
    void receivesPacketsInOrderOnAnEphemeralPort() throws Exception {
        BlockingQueue<OscPacket> received = new LinkedBlockingQueue<>();
        try (VrcOscClient client =
                VrcOscClient.builder()
                        .receivePort(0)
                        .listener(received::add)
                        .build()) {
            client.start();
            assertTrue(client.isReceiving());
            assertTrue(client.receivePort() > 0);

            try (UdpOscSender sender =
                    new UdpOscSender(loopback(client.receivePort()))) {
                for (int i = 0; i < 5; i++) {
                    sender.send(OscMessage.of("/order", OscArgument.int32(i)));
                }
            }

            for (int i = 0; i < 5; i++) {
                OscPacket packet = received.poll(3, TimeUnit.SECONDS);
                assertEquals(
                        OscMessage.of("/order", OscArgument.int32(i)), packet);
            }
        }
    }

    @Test
    void callsEveryListenerAndKeepsReceivingAfterFailures() throws Exception {
        List<OscPacket> first = new CopyOnWriteArrayList<>();
        List<OscPacket> second = new CopyOnWriteArrayList<>();
        List<OscException> errors = new CopyOnWriteArrayList<>();
        CountDownLatch secondReceived = new CountDownLatch(1);
        OscListener failing =
                packet -> {
                    first.add(packet);
                    throw new IllegalStateException("listener failure");
                };
        OscListener recording =
                packet -> {
                    second.add(packet);
                    secondReceived.countDown();
                };

        try (VrcOscClient client =
                VrcOscClient.builder()
                        .receivePort(0)
                        .listener(failing)
                        .listener(recording)
                        .errorHandler(errors::add)
                        .build()) {
            client.start();

            try (UdpOscSender sender = new UdpOscSender(loopback(client.receivePort()))) {
                sender.send(OscMessage.of("/ok", OscArgument.int32(1)));
                assertTrue(secondReceived.await(3, TimeUnit.SECONDS));

                sendMalformed(client.receivePort(), new byte[] {1, 2, 3});
                sender.send(OscMessage.of("/after", OscArgument.string("still alive")));
            }

            awaitTrue(() -> second.size() >= 2, Duration.ofSeconds(3));
            assertEquals(2, first.size());
            assertEquals(
                    OscMessage.of("/after", OscArgument.string("still alive")), second.get(1));
            awaitTrue(() -> errors.size() >= 2, Duration.ofSeconds(3));
            assertTrue(errors.stream().anyMatch(OscTransportException.class::isInstance));
            assertTrue(errors.stream().anyMatch(OscProtocolException.class::isInstance));
            assertTrue(client.isReceiving());
        }
    }

    @Test
    void supportsConcurrentSenders() throws Exception {
        BlockingQueue<OscPacket> received = new LinkedBlockingQueue<>();
        int threads = 4;
        int perThread = 25;
        int total = threads * perThread;
        Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();

        try (UdpOscReceiver receiver = startedReceiver(received);
                VrcOscClient client =
                        VrcOscClient.builder().sendPort(receiver.localPort()).build()) {
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                for (int thread = 0; thread < threads; thread++) {
                    int id = thread;
                    executor.submit(
                            () -> {
                                start.await();
                                for (int i = 0; i < perThread; i++) {
                                    client.send("/concurrent", OscArgument.int32(id));
                                }
                                return null;
                            });
                }
                start.countDown();

                for (int i = 0; i < total; i++) {
                    OscPacket packet = received.poll(10, TimeUnit.SECONDS);
                    assertTrue(packet instanceof OscMessage);
                    int id = ((OscArgument.Int32) ((OscMessage) packet).arguments().get(0)).value();
                    counts.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                }
            } finally {
                executor.shutdownNow();
            }

            assertEquals(threads, counts.size());
            for (AtomicInteger count : counts.values()) {
                assertEquals(perThread, count.get());
            }
        }
    }

    @Test
    void closeIsIdempotentAndReleasesTheReceivePort() throws Exception {
        int port;
        try (DatagramSocket reserved = inbox()) {
            port = reserved.getLocalPort();
        }

        VrcOscClient client = VrcOscClient.builder().receivePort(port).build();
        client.start();
        assertEquals(port, client.receivePort());
        assertTrue(client.isReceiving());

        client.close();
        client.close();

        assertFalse(client.isReceiving());
        assertThrows(OscTransportException.class, () -> client.start());
        assertThrows(IllegalStateException.class, () -> client.addListener(packet -> {}));
        assertThrows(
                OscTransportException.class,
                () -> client.send("/closed", OscArgument.int32(1)));

        try (DatagramSocket rebound = new DatagramSocket(new InetSocketAddress("127.0.0.1", port))) {
            assertTrue(rebound.isBound());
        }
    }

    @Test
    void removesListeners() throws Exception {
        BlockingQueue<OscPacket> received = new LinkedBlockingQueue<>();
        AtomicInteger removedCalls = new AtomicInteger();
        OscListener removed = packet -> removedCalls.incrementAndGet();
        try (VrcOscClient client =
                VrcOscClient.builder()
                        .receivePort(0)
                        .listener(removed)
                        .listener(received::add)
                        .build()) {
            client.removeListener(removed);
            client.start();
            try (UdpOscSender sender = new UdpOscSender(loopback(client.receivePort()))) {
                sender.send(OscMessage.of("/removed", OscArgument.int32(1)));
            }
            assertTrue(received.poll(3, TimeUnit.SECONDS) instanceof OscMessage);
            assertEquals(0, removedCalls.get());
        }
    }

    private static DatagramSocket inbox() throws IOException {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(3_000);
        return socket;
    }

    private static UdpOscReceiver startedReceiver(BlockingQueue<OscPacket> received) {
        UdpOscReceiver receiver = new UdpOscReceiver(loopback(0), received::add, error -> {});
        receiver.start();
        return receiver;
    }

    private static OscPacket receive(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[OscCodec.MAX_PACKET_SIZE];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        socket.receive(datagram);
        return OscCodec.decode(Arrays.copyOf(buffer, datagram.getLength()));
    }

    private static InetSocketAddress loopback(int port) {
        return new InetSocketAddress("127.0.0.1", port);
    }

    private static void sendMalformed(int port, byte[] bytes) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(bytes, bytes.length, loopback(port)));
        }
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition was not met within " + timeout);
    }
}
