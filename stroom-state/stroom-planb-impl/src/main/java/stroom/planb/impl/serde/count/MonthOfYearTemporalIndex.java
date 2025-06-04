package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class MonthOfYearTemporalIndex implements TemporalIndex {

    private static final int MONTHS_IN_YEAR = 12;

    @Override
    public int getEntries() {
        return MONTHS_IN_YEAR;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.MONTHS;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        return zonedDateTime.getMonth().getValue() - 1;
    }
}
