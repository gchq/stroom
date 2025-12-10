/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
