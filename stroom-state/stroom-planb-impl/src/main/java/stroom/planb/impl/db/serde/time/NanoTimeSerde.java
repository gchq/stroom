package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public class NanoTimeSerde implements TimeSerde {

    private static final int SIZE = Long.BYTES + Integer.BYTES;

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        byteBuffer.putLong(instant.getEpochSecond());
        byteBuffer.putInt(instant.getNano());
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond(byteBuffer.getLong(), byteBuffer.getInt());
    }

    @Override
    public int getSize() {
        return SIZE;
    }
}
