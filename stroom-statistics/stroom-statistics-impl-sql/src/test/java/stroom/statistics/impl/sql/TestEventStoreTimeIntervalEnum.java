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

package stroom.statistics.impl.sql;


import stroom.statistics.impl.sql.shared.EventStoreTimeIntervalEnum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventStoreTimeIntervalEnum {

    @Test
    void testFromColumnInterval() {
        for (final EventStoreTimeIntervalEnum intervalEnum : EventStoreTimeIntervalEnum.values()) {
            assertThat(EventStoreTimeIntervalEnum.fromColumnInterval(intervalEnum.columnInterval())).isEqualTo(
                    intervalEnum);
        }
    }

    @Test
    void testFromShortName() {
        for (final EventStoreTimeIntervalEnum intervalEnum : EventStoreTimeIntervalEnum.values()) {
            assertThat(EventStoreTimeIntervalEnum.fromShortName(intervalEnum.shortName())).isEqualTo(intervalEnum);
        }
    }

    @Test
    void testRoundTimeToColumnInterval() {
        for (final EventStoreTimeIntervalEnum interval : EventStoreTimeIntervalEnum.values()) {
            final long timeMs = (interval.columnInterval() * 2) + 1;
            final long expectedTimeMs = (interval.columnInterval() * 2);

            final long roundedTime = interval.roundTimeToColumnInterval(timeMs);

            assertThat(roundedTime).isEqualTo(expectedTimeMs);
        }
    }

    @Test
    void testRoundTimeToRowKeyInterval() {
        for (final EventStoreTimeIntervalEnum interval : EventStoreTimeIntervalEnum.values()) {
            final long timeMs = (interval.rowKeyInterval() * 2) + 1;
            final long expectedTimeMs = (interval.rowKeyInterval() * 2);

            final long roundedTime = interval.roundTimeToRowKeyInterval(timeMs);

            assertThat(roundedTime).isEqualTo(expectedTimeMs);
        }
    }
}
