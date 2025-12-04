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

package stroom.pipeline.destination;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Target;
import stroom.data.store.mock.MockStore;
import stroom.meta.api.MetaProperties;
import stroom.meta.mock.MockMetaService;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.CronTrigger;
import stroom.util.scheduler.FrequencyTrigger;
import stroom.util.shared.scheduler.CronExpressions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestRollingStreamDestination {

    private MockStore streamStore = new MockStore(new MockMetaService());

    @Test
    void testFrequency() throws IOException {
        final Instant time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T00:00:00.000Z");
        final MetaProperties dataProperties = MetaProperties.builder().typeName(StreamTypeNames.EVENTS).build();
        final Target streamTarget = streamStore.openTarget(dataProperties);
        final StreamKey streamKey = new StreamKey("test", StreamTypeNames.EVENTS, false);
        final RollingStreamDestination rollingStreamDestination = new RollingStreamDestination(streamKey,
                new FrequencyTrigger(60000L),
                null,
                100,
                time,
                streamStore,
                streamTarget,
                "test");

        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time)).isFalse();
        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time.plusMillis(60000))).isFalse();
        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time.plusMillis(60001))).isTrue();
    }

    @Test
    void testSchedule() throws IOException {
        final Instant time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T00:00:00.000Z");
        final MetaProperties dataProperties = MetaProperties.builder().typeName(StreamTypeNames.EVENTS).build();
        final Target streamTarget = streamStore.openTarget(dataProperties);
        final StreamKey streamKey = new StreamKey("test", StreamTypeNames.EVENTS, false);
        final RollingStreamDestination rollingStreamDestination = new RollingStreamDestination(streamKey,
                null,
                new CronTrigger(CronExpressions.EVERY_MINUTE.getExpression()),
                100,
                time,
                streamStore,
                streamTarget,
                "test");

        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time)).isFalse();
        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time.plusMillis(60000))).isFalse();
        assertThat(rollingStreamDestination.tryFlushAndRoll(false, time.plusMillis(60001))).isTrue();
    }
}
