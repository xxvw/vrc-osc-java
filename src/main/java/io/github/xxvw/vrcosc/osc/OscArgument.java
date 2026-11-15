package io.github.xxvw.vrcosc.osc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A typed OSC argument. */
public sealed interface OscArgument
        permits OscArgument.Int32,
                OscArgument.Float32,
                OscArgument.StringValue,
                OscArgument.Blob,
                OscArgument.Int64,
                OscArgument.Double64,
                OscArgument.TimeTag,
                OscArgument.CharValue,
                OscArgument.Symbol,
                OscArgument.Midi,
                OscArgument.Rgba,
                OscArgument.Bool,
                OscArgument.Nil,
                OscArgument.Impulse,
                OscArgument.Array {

    /** A 32-bit signed integer. */
    record Int32(int value) implements OscArgument {}

    /** A 32-bit IEEE floating-point number. */
    record Float32(float value) implements OscArgument {}

    /** A UTF-8 string. */
    record StringValue(String value) implements OscArgument {
        /** Creates a string argument. */
        public StringValue {
            requireText(value, "value");
        }
    }

    /** An opaque byte array. */
    final class Blob implements OscArgument {
        private final byte[] value;

        /** Creates a blob and defensively copies its bytes. */
        public Blob(byte[] value) {
            this.value = Objects.requireNonNull(value, "value").clone();
        }

        /** Returns a defensive copy of the blob bytes. */
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Blob blob && Arrays.equals(value, blob.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "Blob[length=" + value.length + "]";
        }
    }

    /** A 64-bit signed integer. */
    record Int64(long value) implements OscArgument {}

    /** A 64-bit IEEE floating-point number. */
    record Double64(double value) implements OscArgument {}

    /** An NTP timestamp value. */
    record TimeTag(OscTimeTag value) implements OscArgument {
        /** Creates a timestamp argument. */
        public TimeTag {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A Unicode character stored as a 32-bit code unit. */
    record CharValue(char value) implements OscArgument {}

    /** An OSC symbol. */
    record Symbol(String value) implements OscArgument {
        /** Creates a symbol argument. */
        public Symbol {
            requireText(value, "value");
        }
    }

    /** A four-byte MIDI message. */
    record Midi(int port, int status, int data1, int data2) implements OscArgument {
        /** Creates a MIDI argument and validates all byte values. */
        public Midi {
            requireByte(port, "port");
            requireByte(status, "status");
            requireByte(data1, "data1");
            requireByte(data2, "data2");
        }
    }

    /** A four-channel RGBA color. */
    record Rgba(int red, int green, int blue, int alpha) implements OscArgument {
        /** Creates an RGBA argument and validates all channels. */
        public Rgba {
            requireByte(red, "red");
            requireByte(green, "green");
            requireByte(blue, "blue");
            requireByte(alpha, "alpha");
        }
    }

    /** A boolean value encoded with the T or F type tag. */
    record Bool(boolean value) implements OscArgument {}

    /** The OSC nil value. */
    record Nil() implements OscArgument {
        /** Shared nil value. */
        public static final Nil INSTANCE = new Nil();
    }

    /** The OSC impulse value. */
    record Impulse() implements OscArgument {
        /** Shared impulse value. */
        public static final Impulse INSTANCE = new Impulse();
    }

    /** An OSC array; OSC 1.0 arrays cannot contain another array. */
    record Array(List<OscArgument> values) implements OscArgument {
        /** Creates an immutable array argument. */
        public Array {
            Objects.requireNonNull(values, "values");
            List<OscArgument> copy = new ArrayList<>(values.size());
            for (OscArgument value : values) {
                Objects.requireNonNull(value, "array element");
                if (value instanceof Array) {
                    throw new IllegalArgumentException("OSC arrays cannot be nested");
                }
                copy.add(value);
            }
            values = List.copyOf(copy);
        }
    }

    /** Creates an int32 argument. */
    static Int32 int32(int value) {
        return new Int32(value);
    }

    /** Creates a float32 argument. */
    static Float32 float32(float value) {
        return new Float32(value);
    }

    /** Creates a string argument. */
    static StringValue string(String value) {
        return new StringValue(value);
    }

    /** Creates a blob argument. */
    static Blob blob(byte[] value) {
        return new Blob(value);
    }

    /** Creates an int64 argument. */
    static Int64 int64(long value) {
        return new Int64(value);
    }

    /** Creates a double argument. */
    static Double64 double64(double value) {
        return new Double64(value);
    }

    /** Creates a timestamp argument. */
    static TimeTag timeTag(OscTimeTag value) {
        return new TimeTag(value);
    }

    /** Creates a character argument. */
    static CharValue charValue(char value) {
        return new CharValue(value);
    }

    /** Creates a symbol argument. */
    static Symbol symbol(String value) {
        return new Symbol(value);
    }

    /** Creates a MIDI argument. */
    static Midi midi(int port, int status, int data1, int data2) {
        return new Midi(port, status, data1, data2);
    }

    /** Creates an RGBA argument. */
    static Rgba rgba(int red, int green, int blue, int alpha) {
        return new Rgba(red, green, blue, alpha);
    }

    /** Creates a boolean argument. */
    static Bool bool(boolean value) {
        return new Bool(value);
    }

    /** Returns the shared nil argument. */
    static Nil nil() {
        return Nil.INSTANCE;
    }

    /** Returns the shared impulse argument. */
    static Impulse impulse() {
        return Impulse.INSTANCE;
    }

    /** Creates an array argument from a list. */
    static Array array(List<? extends OscArgument> values) {
        Objects.requireNonNull(values, "values");
        return new Array(List.copyOf(values));
    }

    /** Creates an array argument from values. */
    static Array array(OscArgument... values) {
        Objects.requireNonNull(values, "values");
        return new Array(List.of(values));
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " cannot contain NUL");
        }
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
    }
}
