package stroom.state.impl.dao;

import java.time.Instant;

public record TemporalStateRequest(String map, String key, Instant effectiveTime) {

}
