/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.shared.common;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;

public class TestEventStoreTimeIntervalEnum {
    @Test
    public void testFromColumnInterval() throws Exception {
        for (final EventStoreTimeIntervalEnum intervalEnum : EventStoreTimeIntervalEnum.values()) {
            Assert.assertEquals(intervalEnum,
                    EventStoreTimeIntervalEnum.fromColumnInterval(intervalEnum.columnInterval()));
        }
    }

    @Test
    public void testFromShortName() throws Exception {
        for (final EventStoreTimeIntervalEnum intervalEnum : EventStoreTimeIntervalEnum.values()) {
            Assert.assertEquals(intervalEnum, EventStoreTimeIntervalEnum.fromShortName(intervalEnum.shortName()));
        }
    }

    @Test
    public void testRoundTimeToColumnInterval() throws Exception {
        for (final EventStoreTimeIntervalEnum interval : EventStoreTimeIntervalEnum.values()) {
            final long timeMs = (interval.columnInterval() * 2) + 1;
            final long expectedTimeMs = (interval.columnInterval() * 2);

            final long roundedTime = interval.roundTimeToColumnInterval(timeMs);

            Assert.assertEquals(expectedTimeMs, roundedTime);
        }
    }

    @Test
    public void testRoundTimeToRowKeyInterval() throws Exception {
        for (final EventStoreTimeIntervalEnum interval : EventStoreTimeIntervalEnum.values()) {
            final long timeMs = (interval.rowKeyInterval() * 2) + 1;
            final long expectedTimeMs = (interval.rowKeyInterval() * 2);

            final long roundedTime = interval.roundTimeToRowKeyInterval(timeMs);

            Assert.assertEquals(expectedTimeMs, roundedTime);
        }
    }
}
