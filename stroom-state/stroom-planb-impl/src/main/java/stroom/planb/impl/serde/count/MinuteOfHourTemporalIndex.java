package stroom.planb.impl.serde.count;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class MinuteOfHourTemporalIndex implements TemporalIndex {

    private static final int MINUTES_IN_HOUR = 60;

    @Override
    public int getEntries() {
        return MINUTES_IN_HOUR;
    }

    @Override
    public TemporalUnit getTemporalUnit() {
        return ChronoUnit.MINUTES;
    }

    @Override
    public int getEntryIndex(final Instant instant, final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        return zonedDateTime.getMinute();
    }
}
