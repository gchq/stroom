package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class MinuteTimeSerde implements TimeSerde {

    private static final long SECONDS_IN_MINUTE = 60L;

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putInt((int) (instant.getEpochSecond() / SECONDS_IN_MINUTE));
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(byteBuffer.getInt() * SECONDS_IN_MINUTE);
    }

    @Override
    public int getSize() {
        return Integer.BYTES;
    }
}
