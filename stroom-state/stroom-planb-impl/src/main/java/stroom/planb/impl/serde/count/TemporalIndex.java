package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalUnit;

public interface TemporalIndex {

    int getEntries();

    TemporalUnit getTemporalUnit();

    int getEntryIndex(Instant instant, ZoneId zoneId);
}
