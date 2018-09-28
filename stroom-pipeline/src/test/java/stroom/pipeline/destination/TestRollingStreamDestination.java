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
import stroom.feed.shared.Feed;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.SimpleCron;

import java.io.IOException;

public class TestRollingStreamDestination {
    @Test
    public void testFrequency() throws IOException {
        final long time = DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z");
        final MockStreamStore streamStore = new MockStreamStore();
        final Stream stream = Stream.createStream(StreamType.EVENTS, null, 1L);
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        final Feed feed = new Feed();
        final StreamKey streamKey = new StreamKey(feed, StreamType.EVENTS.getDisplayValue(), false);
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
        final MockStreamStore streamStore = new MockStreamStore();
        final Stream stream = Stream.createStream(StreamType.EVENTS, null, 1L);
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        final Feed feed = new Feed();
        final StreamKey streamKey = new StreamKey(feed, StreamType.EVENTS.getDisplayValue(), false);
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
