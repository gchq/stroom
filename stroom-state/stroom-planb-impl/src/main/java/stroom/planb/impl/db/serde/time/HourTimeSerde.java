package stroom.planb.impl.db.serde.time;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;

import java.nio.ByteBuffer;
import java.time.Instant;

public class HourTimeSerde implements TimeSerde {

    private static final long SECONDS_IN_HOUR = 3600L;
    // Three bytes gives us approx 1964 years from epoch of 1970
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.THREE;

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        UNSIGNED_BYTES.put(byteBuffer, instant.getEpochSecond() / SECONDS_IN_HOUR);
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(UNSIGNED_BYTES.get(byteBuffer) * SECONDS_IN_HOUR);
    }

    @Override
    public int getSize() {
        return UNSIGNED_BYTES.length();
    }
}
