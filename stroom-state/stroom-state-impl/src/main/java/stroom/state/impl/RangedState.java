package stroom.state.impl;

import java.nio.ByteBuffer;
import java.time.Instant;

public record RangedState(String map, long from, long to, Instant effectiveTime, ValueTypeId typeId, ByteBuffer value) {

}
