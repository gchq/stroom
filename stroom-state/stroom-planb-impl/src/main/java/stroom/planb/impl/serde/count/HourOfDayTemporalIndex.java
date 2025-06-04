package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class HourOfDayTemporalIndex implements TemporalIndex {

    private static final int HOURS_IN_DAY = 24;

    @Override
    public int getEntries() {
        return HOURS_IN_DAY;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.HOURS;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        return zonedDateTime.getHour();
    }
}
