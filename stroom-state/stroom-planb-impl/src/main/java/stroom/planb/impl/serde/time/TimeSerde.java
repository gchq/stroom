package stroom.planb.impl.serde.time;

import stroom.query.language.functions.Val;

import java.nio.ByteBuffer;
import java.time.Instant;

public interface TimeSerde {

    void write(ByteBuffer byteBuffer, Instant instant);

    Instant read(ByteBuffer byteBuffer);

    int getSize();

    Val getTemporalResolution();
}
