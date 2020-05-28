package stroom.util.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimePeriod {

    @Test
    void testGetPeriod() {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant future = now.plus(2, ChronoUnit.DAYS);

        final TimePeriod period = TimePeriod.between(now, future);

        assertThat(now).isEqualTo(period.getFrom());
        assertThat(future).isEqualTo(period.getTo());

        assertThat(period.getPeriod()).isEqualTo(Period.ofDays(2));
    }

    @Test
    void testGetDuration() {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant future = now.plus(2, ChronoUnit.DAYS);

        final TimePeriod period = TimePeriod.between(now, future);

        assertThat(now).isEqualTo(period.getFrom());
        assertThat(future).isEqualTo(period.getTo());

        assertThat(period.getDuration()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void testConstructors() {
        final Instant now = Instant.now();
        final Instant future = now.plus(2, ChronoUnit.DAYS);
        final long nowMs = now.toEpochMilli();
        final long futureMs = future.toEpochMilli();

        final TimePeriod period1 = TimePeriod.between(now, future);
        final TimePeriod period2 = TimePeriod.between(nowMs, futureMs);

        assertThat(period1).isEqualTo(period2);

        assertThat(period1.getFrom()).isEqualTo(now.truncatedTo(ChronoUnit.MILLIS));
        assertThat(period1.getTo()).isEqualTo(future.truncatedTo(ChronoUnit.MILLIS));
    }
}