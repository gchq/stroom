package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class HourTimeSerde implements TimeSerde {

    private static final long SECONDS_IN_HOUR = 3600L;

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putInt((int) (instant.getEpochSecond() / SECONDS_IN_HOUR));
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(byteBuffer.getInt() * SECONDS_IN_HOUR);
    }

    @Override
    public int getSize() {
        return Integer.BYTES;
    }
}
