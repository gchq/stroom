package stroom.state.impl;

import java.nio.ByteBuffer;
import java.time.Instant;

public record RangedState(
        String map,
        long keyStart,
        long keyEnd,
        Instant effectiveTime,
        byte typeId,
        ByteBuffer value) {

}
