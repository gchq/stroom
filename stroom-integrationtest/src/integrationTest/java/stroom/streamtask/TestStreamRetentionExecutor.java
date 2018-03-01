/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamtask;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.streamstore.StreamMaintenanceService;
import stroom.streamstore.StreamRetentionExecutor;
import stroom.streamstore.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TestStreamRetentionExecutor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStreamRetentionExecutor.class);
    private static final int RETENTION_PERIOD_DAYS = 1;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private StreamStore streamStore;
    @Inject
    private StreamMaintenanceService streamMaintenanceService;
    @Inject
    private FeedService feedService;
    @Inject
    private StreamRetentionExecutor streamRetentionExecutor;

    @Test
    public void testMultipleRuns() {
        Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info("now: {}", DateUtil.createNormalDateTimeString(now));
        LOGGER.info("timeOutsideRetentionPeriod: {}", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

        // save two streams, one inside retention period, one outside
        feed.setRetentionDayAge(RETENTION_PERIOD_DAYS);
        Stream streamInsideRetetion = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, now);
        streamInsideRetetion.setStatusMs(now);
        Stream streamOutsideRetetion = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null,
                timeOutsideRetentionPeriod);
        streamOutsideRetetion.setStatusMs(now);

        feed = feedService.save(feed);
        streamInsideRetetion = streamMaintenanceService.save(streamInsideRetetion);
        streamOutsideRetetion = streamMaintenanceService.save(streamOutsideRetetion);

        dumpStreams();

        Long lastStatusMsInside = streamInsideRetetion.getStatusMs();
        Long lastStatusMsOutside = streamOutsideRetetion.getStatusMs();

        // run the stream retention task which should 'delete' one stream
        streamRetentionExecutor.exec();

        streamInsideRetetion = streamStore.loadStreamById(streamInsideRetetion.getId(), true);
        streamOutsideRetetion = streamStore.loadStreamById(streamOutsideRetetion.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetetion.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetetion.getStatus());
        // no change to the record
        Assert.assertEquals(lastStatusMsInside, streamInsideRetetion.getStatusMs());
        // record changed
        Assert.assertTrue(streamOutsideRetetion.getStatusMs().longValue() > lastStatusMsOutside);

        lastStatusMsInside = streamInsideRetetion.getStatusMs();
        lastStatusMsOutside = streamOutsideRetetion.getStatusMs();

        // run the task again, but this time no changes should be made as the
        // one outside the retention period is already 'deleted'
        streamRetentionExecutor.exec();

        streamInsideRetetion = streamStore.loadStreamById(streamInsideRetetion.getId(), true);
        streamOutsideRetetion = streamStore.loadStreamById(streamOutsideRetetion.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetetion.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetetion.getStatus());
        // no change to the records
        Assert.assertEquals(lastStatusMsInside, streamInsideRetetion.getStatusMs());
        Assert.assertEquals(lastStatusMsOutside, streamOutsideRetetion.getStatusMs());
    }

    private void dumpStreams() {
        final BaseResultList<Stream> streams = streamStore.find(new FindStreamCriteria());

        Assert.assertEquals(2, streams.size());

        for (final Stream stream : streams) {
            LOGGER.info("stream: {}, createMs: {}, statusMs: {}, status: {}", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
