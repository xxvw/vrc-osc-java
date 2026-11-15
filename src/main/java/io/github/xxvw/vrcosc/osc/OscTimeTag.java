package io.github.xxvw.vrcosc.osc;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** An OSC 1.0 NTP timestamp, including the special immediate value. */
public final class OscTimeTag {
    private static final long NTP_UNIX_EPOCH_OFFSET_SECONDS = 2_208_988_800L;
    private static final long NTP_IMMEDIATE = 1L;

    private final long ntpBits;

    private OscTimeTag(long ntpBits) {
        this.ntpBits = ntpBits;
    }

    /** Returns the OSC immediate-delivery timestamp. */
    public static OscTimeTag immediate() {
        return new OscTimeTag(NTP_IMMEDIATE);
    }

    /** Creates a timestamp for the given instant. */
    public static OscTimeTag at(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        long ntpSeconds = instant.getEpochSecond() + NTP_UNIX_EPOCH_OFFSET_SECONDS;
        if (ntpSeconds < 0 || ntpSeconds > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Instant is outside the OSC NTP timestamp range");
        }
        long fraction = (instant.getNano() * (1L << 32)) / 1_000_000_000L;
        return new OscTimeTag((ntpSeconds << 32) | fraction);
    }

    /** Creates a timestamp from its raw 64-bit NTP value. */
    public static OscTimeTag ofNtpBits(long ntpBits) {
        return new OscTimeTag(ntpBits);
    }

    /** Returns the raw 64-bit NTP value used on the wire. */
    public long ntpBits() {
        return ntpBits;
    }

    /** Returns whether this is the special immediate-delivery timestamp. */
    public boolean isImmediate() {
        return ntpBits == NTP_IMMEDIATE;
    }

    /** Returns the represented instant, or empty for the immediate timestamp. */
    public Optional<Instant> instant() {
        if (isImmediate()) {
            return Optional.empty();
        }
        long secondsSince1900 = ntpBits >>> 32;
        long fraction = ntpBits & 0xFFFF_FFFFL;
        long epochSecond = secondsSince1900 - NTP_UNIX_EPOCH_OFFSET_SECONDS;
        long nanos = (fraction * 1_000_000_000L) / (1L << 32);
        return Optional.of(Instant.ofEpochSecond(epochSecond, nanos));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OscTimeTag timeTag && ntpBits == timeTag.ntpBits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ntpBits);
    }

    @Override
    public String toString() {
        return isImmediate()
                ? "OscTimeTag[immediate]"
                : "OscTimeTag[" + instant().orElseThrow() + "]";
    }
}
