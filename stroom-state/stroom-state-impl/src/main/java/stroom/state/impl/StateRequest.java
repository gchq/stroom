package stroom.state.impl;

import java.time.Instant;

public record StateRequest(String map, String key, Instant effectiveTime) {

}
