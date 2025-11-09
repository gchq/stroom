package stroom.planb.impl.db.trace;

import stroom.pathways.shared.otel.trace.NanoTime;

import java.time.Instant;

public class NanoTimeUtil {

    public static NanoTime now() {
        return fromInstant(Instant.now());
    }

    public static NanoTime fromInstant(final Instant instant) {
        return new NanoTime(instant.getEpochSecond(), instant.getNano());
    }

    public static Instant toInstant(final NanoTime nanoTime) {
        return Instant.ofEpochSecond(nanoTime.getSeconds(), nanoTime.getNanos());
    }
}
