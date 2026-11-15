package io.github.xxvw.vrcosc.osc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xxvw.vrcosc.OscProtocolException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class OscCodecTest {
    @Test
    void encodesFixedIntMessage() {
        OscMessage message = OscMessage.of("/test", OscArgument.int32(0x01020304));

        byte[] encoded = OscCodec.encode(message);

        assertArrayEquals(
                new byte[] {
                    '/', 't', 'e', 's', 't', 0, 0, 0,
                    ',', 'i', 0, 0,
                    1, 2, 3, 4
                },
                encoded);
        assertEquals(message, OscCodec.decode(encoded));
    }

    @Test
    void encodesImmediateBundleWithElementSizes() {
        OscMessage message = OscMessage.of("/test", OscArgument.int32(0x01020304));
        OscBundle bundle = OscBundle.immediate(message);

        byte[] encoded = OscCodec.encode(bundle);

        byte[] expectedMessage = OscCodec.encode(message);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(new byte[] {'#', 'b', 'u', 'n', 'd', 'l', 'e', 0});
        expected.writeBytes(new byte[] {0, 0, 0, 0, 0, 0, 0, 1});
        writeInt(expected, expectedMessage.length);
        expected.writeBytes(expectedMessage);

        assertArrayEquals(expected.toByteArray(), encoded);
        assertEquals(bundle, OscCodec.decode(encoded));
    }

    @Test
    void roundTripsEveryStandardArgumentType() {
        OscTimeTag timeTag = OscTimeTag.ofNtpBits(0xE000000080000000L);
        OscMessage message =
                OscMessage.of(
                        "/all",
                        OscArgument.int32(-42),
                        OscArgument.float32(1.25f),
                        OscArgument.string("こんにちは OSC"),
                        OscArgument.blob(new byte[] {0, 1, 2, 3, 4}),
                        OscArgument.int64(-9_000_000_000L),
                        OscArgument.double64(Math.PI),
                        OscArgument.timeTag(timeTag),
                        OscArgument.charValue('A'),
                        OscArgument.symbol("symbol"),
                        OscArgument.midi(1, 0x90, 60, 127),
                        OscArgument.rgba(1, 2, 3, 4),
                        OscArgument.bool(true),
                        OscArgument.bool(false),
                        OscArgument.nil(),
                        OscArgument.impulse(),
                        OscArgument.array(
                                OscArgument.int32(7),
                                OscArgument.string("nested"),
                                OscArgument.bool(false)));

        OscPacket decoded = OscCodec.decode(OscCodec.encode(message));

        assertEquals(message, decoded);
    }

    @Test
    void encodesFixedWireBytesForEveryType() {
        OscMessage message =
                OscMessage.of(
                        "/all",
                        OscArgument.int32(42),
                        OscArgument.float32(1.5f),
                        OscArgument.string("hi"),
                        OscArgument.blob(new byte[] {1, 2, 3}),
                        OscArgument.int64(-2L),
                        OscArgument.double64(1.5),
                        OscArgument.timeTag(OscTimeTag.immediate()),
                        OscArgument.charValue('A'),
                        OscArgument.symbol("sym"),
                        OscArgument.midi(1, 0x90, 60, 127),
                        OscArgument.rgba(1, 2, 3, 4),
                        OscArgument.bool(true),
                        OscArgument.bool(false),
                        OscArgument.nil(),
                        OscArgument.impulse(),
                        OscArgument.array(
                                OscArgument.int32(7),
                                OscArgument.float32(0.25f),
                                OscArgument.string("x")));

        byte[] expected =
                HexFormat.of()
                        .parseHex(
                                "2f616c6c00000000"
                                        + "2c6966736268647463536d7254464e495b6966735d000000"
                                        + "0000002a"
                                        + "3fc00000"
                                        + "68690000"
                                        + "0000000301020300"
                                        + "fffffffffffffffe"
                                        + "3ff8000000000000"
                                        + "0000000000000001"
                                        + "00000041"
                                        + "73796d00"
                                        + "01903c7f"
                                        + "01020304"
                                        + "00000007"
                                        + "3e800000"
                                        + "78000000");

        byte[] encoded = OscCodec.encode(message);

        assertArrayEquals(expected, encoded);
        assertEquals(message, OscCodec.decode(expected));
    }

    @Test
    void roundTripsNestedBundles() {
        OscMessage first = OscMessage.of("/one", OscArgument.string("hello"));
        OscMessage second = OscMessage.of("/two");
        OscBundle nested =
                new OscBundle(
                        OscTimeTag.at(Instant.parse("2026-11-16T00:00:00Z")),
                        List.of(second));
        OscBundle bundle = new OscBundle(OscTimeTag.immediate(), List.of(first, nested));

        assertEquals(bundle, OscCodec.decode(OscCodec.encode(bundle)));
    }

    @Test
    void utf8UsesByteLengthAndFourBytePadding() {
        OscMessage message = OscMessage.of("/s", OscArgument.string("あ"));

        byte[] encoded = OscCodec.encode(message);

        assertEquals(0, encoded.length % 4);
        assertEquals(message, OscCodec.decode(encoded));
        assertArrayEquals(
                HexFormat.of().parseHex("2f7300002c730000e3818200"), encoded);
    }

    @Test
    void blobIsDefensivelyCopied() {
        byte[] source = {1, 2, 3};
        OscArgument.Blob blob = OscArgument.blob(source);
        source[0] = 9;
        byte[] firstRead = blob.value();
        firstRead[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, blob.value());
        assertNotSame(firstRead, blob.value());
        assertEquals(
                blob,
                ((OscMessage) OscCodec.decode(OscCodec.encode(OscMessage.of("/b", blob))))
                        .arguments()
                        .get(0));
    }

    @Test
    void rejectsEmptyAndOversizedPackets() {
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(new byte[0]));
        assertThrows(
                OscProtocolException.class,
                () -> OscCodec.decode(new byte[OscCodec.MAX_PACKET_SIZE + 1]));
    }

    @Test
    void rejectsInvalidAddressAndTypeTags() {
        assertThrows(
                OscProtocolException.class,
                () -> OscCodec.decode(messageBytes("no-slash", ",", new byte[0])));
        assertThrows(
                OscProtocolException.class,
                () -> OscCodec.decode(messageBytes("/a", "i", new byte[0])));
        assertThrows(
                OscProtocolException.class,
                () -> OscCodec.decode(messageBytes("/a", ",x", new byte[0])));
    }

    @Test
    void rejectsUnterminatedStringsAndInvalidPadding() {
        assertThrows(
                OscProtocolException.class,
                () -> OscCodec.decode(new byte[] {'/', 'a'}));

        byte[] invalidPadding = OscCodec.encode(OscMessage.of("/a"));
        invalidPadding[3] = 1;
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(invalidPadding));
    }

    @Test
    void rejectsTruncatedValues() {
        byte[] encoded = OscCodec.encode(OscMessage.of("/i", OscArgument.int32(1)));
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(truncated));
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] encoded = OscCodec.encode(OscMessage.of("/a"));
        byte[] withTrailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(withTrailing));
    }

    @Test
    void rejectsMalformedUtf8AndBlobLength() {
        byte[] invalidUtf8 =
                concat(
                        oscString("/s"),
                        oscString(",s"),
                        new byte[] {(byte) 0xC3, 0x28, 0, 0});
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(invalidUtf8));

        byte[] negativeBlob =
                concat(
                        oscString("/b"),
                        oscString(",b"),
                        new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(negativeBlob));
    }

    @Test
    void rejectsMalformedArrays() {
        byte[] nested =
                messageBytes(
                        "/a",
                        ",[[i]]",
                        new byte[] {0, 0, 0, 1});
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(nested));

        byte[] unclosed =
                messageBytes(
                        "/a",
                        ",[i",
                        new byte[] {0, 0, 0, 1});
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(unclosed));
    }

    @Test
    void rejectsExcessiveBundleDepthWhenDecodingAndEncoding() {
        byte[] raw = messageBytes("/a", ",", new byte[0]);
        for (int i = 0; i < 17; i++) {
            raw = bundleBytes(raw);
        }
        byte[] tooDeep = raw;
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(tooDeep));

        OscPacket packet = OscMessage.of("/a");
        for (int i = 0; i < 17; i++) {
            packet = OscBundle.immediate(packet);
        }
        OscPacket tooDeepModel = packet;
        assertThrows(OscProtocolException.class, () -> OscCodec.encode(tooDeepModel));
    }

    @Test
    void rejectsExcessiveElementCountAndBadBundleSizes() {
        byte[] message = messageBytes("/a", ",", new byte[0]);
        byte[][] children = new byte[4_097][];
        Arrays.fill(children, message);
        byte[] tooMany = bundleBytes(children);
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(tooMany));

        byte[] bundle = bundleBytes(message);
        putInt(bundle, 16, 7);
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(bundle));

        byte[] oversizedElement = bundleBytes(message);
        putInt(oversizedElement, 16, oversizedElement.length);
        assertThrows(OscProtocolException.class, () -> OscCodec.decode(oversizedElement));
    }

    @Test
    void rejectsTooManyArguments() {
        List<OscArgument> arguments = new ArrayList<>();
        for (int i = 0; i < 4_096; i++) {
            arguments.add(OscArgument.nil());
        }
        OscMessage message = new OscMessage("/many", arguments);
        assertThrows(OscProtocolException.class, () -> OscCodec.encode(message));
    }

    private static byte[] messageBytes(String address, String typeTags, byte[] data) {
        return concat(oscString(address), oscString(typeTags), data);
    }

    private static byte[] bundleBytes(byte[]... packets) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {'#', 'b', 'u', 'n', 'd', 'l', 'e', 0});
        output.writeBytes(new byte[] {0, 0, 0, 0, 0, 0, 0, 1});
        for (byte[] packet : packets) {
            writeInt(output, packet.length);
            output.writeBytes(packet);
        }
        return output.toByteArray();
    }

    private static byte[] oscString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(bytes);
        output.write(0);
        while (output.size() % 4 != 0) {
            output.write(0);
        }
        return output.toByteArray();
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
