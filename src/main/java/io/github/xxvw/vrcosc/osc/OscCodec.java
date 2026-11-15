package io.github.xxvw.vrcosc.osc;

import io.github.xxvw.vrcosc.OscProtocolException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Encodes and decodes OSC 1.0 packets. */
public final class OscCodec {
    /** Maximum UDP payload size supported by IPv4. */
    public static final int MAX_PACKET_SIZE = 65_507;

    private static final int MAX_BUNDLE_DEPTH = 16;
    private static final int MAX_ELEMENTS = 4_096;
    private static final byte[] BUNDLE_HEADER = {'#', 'b', 'u', 'n', 'd', 'l', 'e', 0};

    private OscCodec() {}

    /** Encodes one OSC packet into a UDP payload. */
    public static byte[] encode(OscPacket packet) {
        Objects.requireNonNull(packet, "packet");
        EncoderContext context = new EncoderContext();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writePacket(output, packet, 0, context);
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_PACKET_SIZE) {
            throw new OscProtocolException(
                    "OSC packet is larger than " + MAX_PACKET_SIZE + " bytes");
        }
        return encoded;
    }

    /** Decodes one complete OSC UDP payload. */
    public static OscPacket decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0 || data.length > MAX_PACKET_SIZE) {
            throw new OscProtocolException("OSC packet size must be between 1 and " + MAX_PACKET_SIZE);
        }
        Cursor cursor = new Cursor(data, 0, data.length);
        OscPacket packet = readPacket(cursor, 0);
        if (cursor.position != cursor.end) {
            throw new OscProtocolException("Trailing bytes after OSC packet");
        }
        return packet;
    }

    private static void writePacket(
            ByteArrayOutputStream output, OscPacket packet, int depth, EncoderContext context) {
        if (depth > MAX_BUNDLE_DEPTH) {
            throw new OscProtocolException("OSC bundle nesting exceeds " + MAX_BUNDLE_DEPTH);
        }
        context.consume();
        if (packet instanceof OscMessage message) {
            writeMessage(output, message, context);
        } else if (packet instanceof OscBundle bundle) {
            writeBundle(output, bundle, depth, context);
        } else {
            throw new OscProtocolException("Unsupported OSC packet type: " + packet.getClass());
        }
    }

    private static void writeMessage(
            ByteArrayOutputStream output, OscMessage message, EncoderContext context) {
        writeOscString(output, message.address());

        StringBuilder typeTags = new StringBuilder(",");
        appendTypeTags(typeTags, message.arguments(), context);
        writeOscString(output, typeTags.toString());

        for (OscArgument argument : message.arguments()) {
            writeArgument(output, argument, context);
        }
    }

    private static void appendTypeTags(
            StringBuilder typeTags, List<OscArgument> arguments, EncoderContext context) {
        for (OscArgument argument : arguments) {
            context.consume();
            if (argument instanceof OscArgument.Int32) {
                typeTags.append('i');
            } else if (argument instanceof OscArgument.Float32) {
                typeTags.append('f');
            } else if (argument instanceof OscArgument.StringValue) {
                typeTags.append('s');
            } else if (argument instanceof OscArgument.Blob) {
                typeTags.append('b');
            } else if (argument instanceof OscArgument.Int64) {
                typeTags.append('h');
            } else if (argument instanceof OscArgument.Double64) {
                typeTags.append('d');
            } else if (argument instanceof OscArgument.TimeTag) {
                typeTags.append('t');
            } else if (argument instanceof OscArgument.CharValue) {
                typeTags.append('c');
            } else if (argument instanceof OscArgument.Symbol) {
                typeTags.append('S');
            } else if (argument instanceof OscArgument.Midi) {
                typeTags.append('m');
            } else if (argument instanceof OscArgument.Rgba) {
                typeTags.append('r');
            } else if (argument instanceof OscArgument.Bool bool) {
                typeTags.append(bool.value() ? 'T' : 'F');
            } else if (argument instanceof OscArgument.Nil) {
                typeTags.append('N');
            } else if (argument instanceof OscArgument.Impulse) {
                typeTags.append('I');
            } else if (argument instanceof OscArgument.Array array) {
                typeTags.append('[');
                appendTypeTags(typeTags, array.values(), context);
                typeTags.append(']');
            } else {
                throw new OscProtocolException(
                        "Unsupported OSC argument type: " + argument.getClass());
            }
        }
    }

    private static void writeArgument(
            ByteArrayOutputStream output, OscArgument argument, EncoderContext context) {
        if (argument instanceof OscArgument.Int32 value) {
            writeInt(output, value.value());
        } else if (argument instanceof OscArgument.Float32 value) {
            writeInt(output, Float.floatToRawIntBits(value.value()));
        } else if (argument instanceof OscArgument.StringValue value) {
            writeOscString(output, value.value());
        } else if (argument instanceof OscArgument.Blob value) {
            byte[] bytes = value.value();
            writeInt(output, bytes.length);
            output.writeBytes(bytes);
            writePadding(output, bytes.length);
        } else if (argument instanceof OscArgument.Int64 value) {
            writeLong(output, value.value());
        } else if (argument instanceof OscArgument.Double64 value) {
            writeLong(output, Double.doubleToRawLongBits(value.value()));
        } else if (argument instanceof OscArgument.TimeTag value) {
            writeLong(output, value.value().ntpBits());
        } else if (argument instanceof OscArgument.CharValue value) {
            writeInt(output, value.value());
        } else if (argument instanceof OscArgument.Symbol value) {
            writeOscString(output, value.value());
        } else if (argument instanceof OscArgument.Midi value) {
            writeInt(
                    output,
                    (value.port() << 24)
                            | (value.status() << 16)
                            | (value.data1() << 8)
                            | value.data2());
        } else if (argument instanceof OscArgument.Rgba value) {
            writeInt(
                    output,
                    (value.red() << 24)
                            | (value.green() << 16)
                            | (value.blue() << 8)
                            | value.alpha());
        } else if (argument instanceof OscArgument.Bool
                || argument instanceof OscArgument.Nil
                || argument instanceof OscArgument.Impulse) {
            return;
        } else if (argument instanceof OscArgument.Array array) {
            for (OscArgument element : array.values()) {
                writeArgument(output, element, context);
            }
        } else {
            throw new OscProtocolException(
                    "Unsupported OSC argument type: " + argument.getClass());
        }
    }

    private static void writeBundle(
            ByteArrayOutputStream output,
            OscBundle bundle,
            int depth,
            EncoderContext context) {
        output.writeBytes(BUNDLE_HEADER);
        writeLong(output, bundle.timeTag().ntpBits());
        for (OscPacket packet : bundle.packets()) {
            ByteArrayOutputStream childOutput = new ByteArrayOutputStream();
            writePacket(childOutput, packet, depth + 1, context);
            byte[] child = childOutput.toByteArray();
            writeInt(output, child.length);
            output.writeBytes(child);
        }
    }

    private static void writeOscString(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeBytes(bytes);
        output.write(0);
        writePadding(output, bytes.length + 1);
    }

    private static void writePadding(ByteArrayOutputStream output, int dataLength) {
        int padding = (4 - (dataLength % 4)) % 4;
        for (int i = 0; i < padding; i++) {
            output.write(0);
        }
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        writeInt(output, (int) (value >>> 32));
        writeInt(output, (int) value);
    }

    private static OscPacket readPacket(Cursor cursor, int depth) {
        if (depth > MAX_BUNDLE_DEPTH) {
            throw new OscProtocolException("OSC bundle nesting exceeds " + MAX_BUNDLE_DEPTH);
        }
        cursor.consume();
        if (cursor.remaining() >= BUNDLE_HEADER.length
                && Arrays.equals(
                        cursor.data,
                        cursor.position,
                        cursor.position + BUNDLE_HEADER.length,
                        BUNDLE_HEADER,
                        0,
                        BUNDLE_HEADER.length)) {
            return readBundle(cursor, depth);
        }
        return readMessage(cursor);
    }

    private static OscBundle readBundle(Cursor cursor, int depth) {
        cursor.position += BUNDLE_HEADER.length;
        long ntpBits = cursor.readLong();
        List<OscPacket> packets = new ArrayList<>();
        while (cursor.remaining() > 0) {
            int size = cursor.readInt();
            if (size <= 0 || size > cursor.remaining()) {
                throw new OscProtocolException("Invalid OSC bundle element size: " + size);
            }
            if (size % 4 != 0) {
                throw new OscProtocolException("OSC bundle element size is not 4-byte aligned");
            }
            int previousEnd = cursor.end;
            int elementEnd = cursor.position + size;
            cursor.end = elementEnd;
            packets.add(readPacket(cursor, depth + 1));
            if (cursor.position != elementEnd) {
                throw new OscProtocolException("OSC bundle element contains trailing bytes");
            }
            cursor.end = previousEnd;
        }
        return new OscBundle(OscTimeTag.ofNtpBits(ntpBits), packets);
    }

    private static OscMessage readMessage(Cursor cursor) {
        String address = readOscString(cursor);
        if (!address.startsWith("/")) {
            throw new OscProtocolException("OSC message address must start with '/'");
        }
        String typeTags = readOscString(cursor);
        if (!typeTags.startsWith(",")) {
            throw new OscProtocolException("OSC type tag string must start with ','");
        }
        int[] index = {1};
        List<OscArgument> arguments = readArgumentList(cursor, typeTags, index, false);
        if (index[0] != typeTags.length()) {
            throw new OscProtocolException("Unexpected OSC type tag structure");
        }
        if (cursor.remaining() != 0) {
            throw new OscProtocolException("Trailing bytes after OSC message arguments");
        }
        return new OscMessage(address, arguments);
    }

    private static List<OscArgument> readArgumentList(
            Cursor cursor, String typeTags, int[] index, boolean insideArray) {
        List<OscArgument> arguments = new ArrayList<>();
        while (index[0] < typeTags.length()) {
            char typeTag = typeTags.charAt(index[0]);
            if (typeTag == ']') {
                if (!insideArray) {
                    throw new OscProtocolException("Unexpected closing array type tag");
                }
                index[0]++;
                return arguments;
            }
            if (typeTag == '[') {
                if (insideArray) {
                    throw new OscProtocolException("OSC arrays cannot be nested");
                }
                index[0]++;
                List<OscArgument> elements = readArgumentList(cursor, typeTags, index, true);
                arguments.add(new OscArgument.Array(elements));
                continue;
            }
            if (typeTag == ',') {
                throw new OscProtocolException("Unexpected comma in OSC type tags");
            }
            index[0]++;
            cursor.consume();
            arguments.add(readArgument(cursor, typeTag));
        }
        if (insideArray) {
            throw new OscProtocolException("Unclosed OSC array type tag");
        }
        return arguments;
    }

    private static OscArgument readArgument(Cursor cursor, char typeTag) {
        return switch (typeTag) {
            case 'i' -> new OscArgument.Int32(cursor.readInt());
            case 'f' -> new OscArgument.Float32(Float.intBitsToFloat(cursor.readInt()));
            case 's' -> new OscArgument.StringValue(readOscString(cursor));
            case 'b' -> readBlob(cursor);
            case 'h' -> new OscArgument.Int64(cursor.readLong());
            case 'd' -> new OscArgument.Double64(Double.longBitsToDouble(cursor.readLong()));
            case 't' -> new OscArgument.TimeTag(OscTimeTag.ofNtpBits(cursor.readLong()));
            case 'c' -> new OscArgument.CharValue((char) cursor.readInt());
            case 'S' -> new OscArgument.Symbol(readOscString(cursor));
            case 'm' -> {
                int packed = cursor.readInt();
                yield new OscArgument.Midi(
                        (packed >>> 24) & 0xFF,
                        (packed >>> 16) & 0xFF,
                        (packed >>> 8) & 0xFF,
                        packed & 0xFF);
            }
            case 'r' -> {
                int packed = cursor.readInt();
                yield new OscArgument.Rgba(
                        (packed >>> 24) & 0xFF,
                        (packed >>> 16) & 0xFF,
                        (packed >>> 8) & 0xFF,
                        packed & 0xFF);
            }
            case 'T' -> new OscArgument.Bool(true);
            case 'F' -> new OscArgument.Bool(false);
            case 'N' -> OscArgument.Nil.INSTANCE;
            case 'I' -> OscArgument.Impulse.INSTANCE;
            default -> throw new OscProtocolException("Unsupported OSC type tag: " + typeTag);
        };
    }

    private static OscArgument.Blob readBlob(Cursor cursor) {
        int length = cursor.readInt();
        if (length < 0 || length > cursor.remaining()) {
            throw new OscProtocolException("Invalid OSC blob length: " + length);
        }
        byte[] bytes = Arrays.copyOfRange(cursor.data, cursor.position, cursor.position + length);
        cursor.position += length;
        int padding = (4 - (length % 4)) % 4;
        cursor.require(padding);
        for (int i = 0; i < padding; i++) {
            if (cursor.data[cursor.position + i] != 0) {
                throw new OscProtocolException("OSC blob padding must be zero");
            }
        }
        cursor.position += padding;
        return new OscArgument.Blob(bytes);
    }

    private static String readOscString(Cursor cursor) {
        int start = cursor.position;
        int nul = -1;
        for (int i = start; i < cursor.end; i++) {
            if (cursor.data[i] == 0) {
                nul = i;
                break;
            }
        }
        if (nul < 0) {
            throw new OscProtocolException("Unterminated OSC string");
        }
        int paddedEnd = align4(nul + 1);
        if (paddedEnd > cursor.end) {
            throw new OscProtocolException("OSC string padding exceeds packet boundary");
        }
        for (int i = nul + 1; i < paddedEnd; i++) {
            if (cursor.data[i] != 0) {
                throw new OscProtocolException("OSC string padding must be zero");
            }
        }
        String value;
        try {
            value = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cursor.data, start, nul - start))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new OscProtocolException("OSC string is not valid UTF-8", exception);
        }
        cursor.position = paddedEnd;
        return value;
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static final class EncoderContext {
        private int elements;

        private void consume() {
            elements++;
            if (elements > MAX_ELEMENTS) {
                throw new OscProtocolException(
                        "OSC packet contains more than " + MAX_ELEMENTS + " elements");
            }
        }
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;
        private int end;
        private int elements;

        private Cursor(byte[] data, int position, int end) {
            this.data = data;
            this.position = position;
            this.end = end;
        }

        private int remaining() {
            return end - position;
        }

        private void require(int bytes) {
            if (bytes < 0 || remaining() < bytes) {
                throw new OscProtocolException("Truncated OSC packet");
            }
        }

        private void consume() {
            elements++;
            if (elements > MAX_ELEMENTS) {
                throw new OscProtocolException(
                        "OSC packet contains more than " + MAX_ELEMENTS + " elements");
            }
        }

        private int readInt() {
            require(4);
            int value =
                    ((data[position] & 0xFF) << 24)
                            | ((data[position + 1] & 0xFF) << 16)
                            | ((data[position + 2] & 0xFF) << 8)
                            | (data[position + 3] & 0xFF);
            position += 4;
            return value;
        }

        private long readLong() {
            require(8);
            long value = ((long) readInt() << 32) | (readInt() & 0xFFFF_FFFFL);
            return value;
        }
    }
}
