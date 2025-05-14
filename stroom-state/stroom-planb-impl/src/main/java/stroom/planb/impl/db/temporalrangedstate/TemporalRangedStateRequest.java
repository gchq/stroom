package stroom.planb.impl.db.temporalrangedstate;

import java.time.Instant;

public record TemporalRangedStateRequest(long key, Instant effectiveTime) {

}
