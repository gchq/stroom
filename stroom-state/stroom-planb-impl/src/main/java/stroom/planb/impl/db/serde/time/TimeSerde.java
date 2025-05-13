package stroom.planb.impl.db.serde.time;

import java.nio.ByteBuffer;
import java.time.Instant;

public interface TimeSerde {

    void write(ByteBuffer byteBuffer, Instant instant);

    Instant read(ByteBuffer byteBuffer);

    int getSize();
}
