/*
 * Copyright 2018 Crown Copyright
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

import org.junit.Assert;
import org.junit.Test;
import stroom.data.meta.api.DataProperties;
import stroom.data.store.api.StreamTarget;
import stroom.data.store.impl.fs.MockStreamStore;
import stroom.datafeed.TestBase;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import java.io.IOException;

public class TestRollingStreamDestination extends TestBase {
    @Inject
    private MockStreamStore streamStore;

    @Test
    public void testFrequency() throws IOException {
        final long time = DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z");
        final DataProperties dataProperties = new DataProperties.Builder().typeName(StreamTypeNames.EVENTS).build();
        final StreamTarget streamTarget = streamStore.openStreamTarget(dataProperties);
        final StreamKey streamKey = new StreamKey("test", StreamTypeNames.EVENTS, false);
        final RollingStreamDestination rollingStreamDestination = new RollingStreamDestination(streamKey,
                60000L,
                null,
                100,
                time,
                streamStore,
                streamTarget,
                "test");

        Assert.assertFalse(rollingStreamDestination.tryFlushAndRoll(false, time));
        Assert.assertFalse(rollingStreamDestination.tryFlushAndRoll(false, time + 60000));
        Assert.assertTrue(rollingStreamDestination.tryFlushAndRoll(false, time + 60001));
    }

    @Test
    public void testSchedule() throws IOException {
        final long time = DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z");
        final DataProperties dataProperties = new DataProperties.Builder().typeName(StreamTypeNames.EVENTS).build();
        final StreamTarget streamTarget = streamStore.openStreamTarget(dataProperties);
        final StreamKey streamKey = new StreamKey("test", StreamTypeNames.EVENTS, false);
        final RollingStreamDestination rollingStreamDestination = new RollingStreamDestination(streamKey,
                null,
                SimpleCron.compile("* * *"),
                100,
                time,
                streamStore,
                streamTarget,
                "test");

        Assert.assertFalse(rollingStreamDestination.tryFlushAndRoll(false, time));
        Assert.assertFalse(rollingStreamDestination.tryFlushAndRoll(false, time + 60000));
        Assert.assertTrue(rollingStreamDestination.tryFlushAndRoll(false, time + 60001));
    }
}
