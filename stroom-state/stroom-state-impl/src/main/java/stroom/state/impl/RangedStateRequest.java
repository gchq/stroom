package stroom.state.impl;

import java.time.Instant;

public record RangedStateRequest(String map, long key, Instant effectiveTime) {

}
