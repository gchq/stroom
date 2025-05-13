package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class SecondTimeSerde implements TimeSerde {

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putLong(instant.getEpochSecond());
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(byteBuffer.getLong());
    }

    @Override
    public int getSize() {
        return Long.BYTES;
    }
}
