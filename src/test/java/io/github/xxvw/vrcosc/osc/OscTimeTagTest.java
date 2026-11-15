package io.github.xxvw.vrcosc.osc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OscTimeTagTest {
    @Test
    void roundTripsInstantWithSubNanosecondWirePrecision() {
        Instant instant = Instant.parse("2026-11-16T01:02:03.123456789Z");

        OscTimeTag timeTag = OscTimeTag.at(instant);
        Instant decoded = timeTag.instant().orElseThrow();

        assertFalse(timeTag.isImmediate());
        assertTrue(Duration.between(decoded, instant).abs().compareTo(Duration.ofNanos(1)) <= 0);
    }

    @Test
    void representsImmediateAndRawNtpValues() {
        assertTrue(OscTimeTag.immediate().isImmediate());
        assertTrue(OscTimeTag.immediate().instant().isEmpty());
        assertEquals(
                OscTimeTag.ofNtpBits(0xE000000080000000L),
                OscTimeTag.ofNtpBits(0xE000000080000000L));
    }

    @Test
    void rejectsInstantsOutsideOscNtpRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OscTimeTag.at(Instant.parse("1899-12-31T23:59:59Z")));
        assertThrows(
                IllegalArgumentException.class,
                () -> OscTimeTag.at(Instant.parse("2036-02-07T06:28:16Z")));
    }
}
