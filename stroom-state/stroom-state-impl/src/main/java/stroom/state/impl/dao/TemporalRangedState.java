package stroom.state.impl.dao;

import java.nio.ByteBuffer;
import java.time.Instant;

public record TemporalRangedState(
        long keyStart,
        long keyEnd,
        Instant effectiveTime,
        byte typeId,
        ByteBuffer value) {

}
