package stroom.planb.impl.serde.time;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.query.language.functions.ValDuration;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

public class SecondTimeSerde implements TimeSerde {

    // Four bytes gives us approx 140 years from epoch of 1970 so adding offset to 2000
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.FOUR;
    // Always offset by year 2000 epoch seconds to give us an extra 30 years.
    private static final long YEAR_2000_EPOCH_SECONDS = 946684800;
    private static final ValDuration TEMPORAL_RESOLUTION = ValDuration.create(Duration.ofSeconds(1));

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        UNSIGNED_BYTES.put(byteBuffer, instant.getEpochSecond() - YEAR_2000_EPOCH_SECONDS);
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(UNSIGNED_BYTES.get(byteBuffer) + YEAR_2000_EPOCH_SECONDS);
    }

    @Override
    public int getSize() {
        return UNSIGNED_BYTES.length();
    }

    @Override
    public ValDuration getTemporalResolution() {
        return TEMPORAL_RESOLUTION;
    }
}
