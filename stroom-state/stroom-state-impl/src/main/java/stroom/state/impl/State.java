package stroom.state.impl;

import java.nio.ByteBuffer;
import java.time.Instant;

public record State(
        String map,
        String key,
        Instant effectiveTime,
        ValueTypeId typeId,
        ByteBuffer value) {

}
