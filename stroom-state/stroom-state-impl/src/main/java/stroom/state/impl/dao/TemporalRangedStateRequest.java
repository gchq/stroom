package stroom.state.impl.dao;

import java.time.Instant;

public record TemporalRangedStateRequest(String map, long key, Instant effectiveTime) {

}
