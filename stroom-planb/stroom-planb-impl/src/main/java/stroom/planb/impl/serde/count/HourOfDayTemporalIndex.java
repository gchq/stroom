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
