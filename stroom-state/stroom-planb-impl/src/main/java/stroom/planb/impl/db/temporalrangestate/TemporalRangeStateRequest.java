package stroom.planb.impl.db.temporalrangestate;

import java.time.Instant;

public record TemporalRangeStateRequest(long key, Instant effectiveTime) {

}
