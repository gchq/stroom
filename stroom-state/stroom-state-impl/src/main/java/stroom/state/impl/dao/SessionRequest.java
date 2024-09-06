package stroom.state.impl.dao;

import java.time.Instant;

public record SessionRequest(String map, String key, Instant time) {

}
