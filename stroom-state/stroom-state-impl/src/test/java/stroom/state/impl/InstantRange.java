package stroom.state.impl;

import java.time.Instant;

public record InstantRange(Instant min, Instant max) {

    public static InstantRange combine(final InstantRange one, final InstantRange two) {
        final Instant min = Instant.ofEpochMilli(Math.min(one.min.toEpochMilli(), two.min.toEpochMilli()));
        final Instant max = Instant.ofEpochMilli(Math.max(one.max.toEpochMilli(), two.max.toEpochMilli()));
        return new InstantRange(min, max);
    }
}
