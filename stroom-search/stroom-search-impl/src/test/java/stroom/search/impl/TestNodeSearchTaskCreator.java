/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeRange;
import stroom.util.shared.Range;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestNodeSearchTaskCreator {

    private static final DateTimeSettings SETTINGS = DateTimeSettings.builder().build();
    private static final String MID_MARCH = "2024-03-15T10:00:00.000Z";
    private static final String END_MARCH = "2024-03-31T23:00:00.000Z";

    @Test
    void testPartitionTimeRange_isTheQueryTimesNotRoundedToPartitions() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("March", MID_MARCH, END_MARCH), SETTINGS);

        assertThat(range.getFrom()).isEqualTo(Instant.parse(MID_MARCH).toEpochMilli());
        assertThat(range.getTo()).isEqualTo(Instant.parse(END_MARCH).toEpochMilli());
    }

    @Test
    void testPartitionTimeRange_fromOnly() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("From", MID_MARCH, null), SETTINGS);

        assertThat(range.getFrom()).isEqualTo(Instant.parse(MID_MARCH).toEpochMilli());
        assertThat(range.getTo()).isNull();
    }

    @Test
    void testPartitionTimeRange_toOnly() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("To", " ", END_MARCH), SETTINGS);

        assertThat(range.getFrom()).isNull();
        assertThat(range.getTo()).isEqualTo(Instant.parse(END_MARCH).toEpochMilli());
    }

    @Test
    void testPartitionTimeRange_noTimeRange() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(null, SETTINGS);

        assertThat(range.getFrom()).isNull();
        assertThat(range.getTo()).isNull();
    }
}
