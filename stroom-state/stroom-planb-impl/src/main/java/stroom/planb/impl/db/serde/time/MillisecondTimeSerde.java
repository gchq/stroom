package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class MillisecondTimeSerde implements TimeSerde {

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putLong(instant.toEpochMilli());
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochMilli(byteBuffer.getLong());
    }

    @Override
    public int getSize() {
        return Long.BYTES;
    }
}
