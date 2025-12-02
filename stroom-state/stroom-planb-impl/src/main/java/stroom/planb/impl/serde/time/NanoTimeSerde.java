package stroom.planb.impl.serde.time;

import stroom.query.language.functions.ValDuration;

import java.nio.ByteBuffer;
import java.time.Instant;

public class NanoTimeSerde implements TimeSerde {

    // Always offset by year 2000 epoch seconds to give us an extra 30 years.
    private static final long YEAR_2000_EPOCH_SECONDS = 946684800;
    private static final long NANOS_PER_SECOND = 1000000000L;
    private static final ValDuration TEMPORAL_RESOLUTION = ValDuration.create(0);

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        final long totalNanos = ((instant.getEpochSecond() - YEAR_2000_EPOCH_SECONDS) * NANOS_PER_SECOND) +
                                instant.getNano();
        byteBuffer.putLong(totalNanos);
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        final long nanos = byteBuffer.getLong();
        final long seconds = nanos / NANOS_PER_SECOND;
        final long remainingNanos = nanos % NANOS_PER_SECOND;
        return Instant.ofEpochSecond(seconds + YEAR_2000_EPOCH_SECONDS, remainingNanos);
    }

    @Override
    public int getSize() {
        return Long.BYTES;
    }

    @Override
    public ValDuration getTemporalResolution() {
        return TEMPORAL_RESOLUTION;
    }
}
