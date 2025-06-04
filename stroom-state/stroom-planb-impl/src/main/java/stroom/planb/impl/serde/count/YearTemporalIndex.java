package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class YearTemporalIndex implements TemporalIndex {

    @Override
    public int getEntries() {
        return 1;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.YEARS;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        return 0;
    }
}
