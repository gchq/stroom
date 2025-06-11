package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class DayOfYearTemporalIndex implements TemporalIndex {

    private static final int DAYS_IN_YEAR = 366;

    @Override
    public int getEntries() {
        return DAYS_IN_YEAR;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.DAYS;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        return zonedDateTime.getDayOfYear() - 1;
    }
}
