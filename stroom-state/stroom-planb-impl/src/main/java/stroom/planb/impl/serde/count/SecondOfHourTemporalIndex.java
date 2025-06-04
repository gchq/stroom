package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class SecondOfHourTemporalIndex implements TemporalIndex {

    private static final int SECONDS_IN_HOUR = 60 * 60;

    @Override
    public int getEntries() {
        return SECONDS_IN_HOUR;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.SECONDS;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        return (zonedDateTime.getMinute() * 60) + zonedDateTime.getSecond();
    }
}
