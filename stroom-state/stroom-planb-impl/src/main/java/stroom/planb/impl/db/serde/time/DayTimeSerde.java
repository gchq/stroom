package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class DayTimeSerde implements TimeSerde {

    private static final long SECONDS_IN_DAY = 86400;

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putInt((int) (instant.getEpochSecond() / SECONDS_IN_DAY));
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(byteBuffer.getInt() * SECONDS_IN_DAY);
    }

    @Override
    public int getSize() {
        return Integer.BYTES;
    }
}
