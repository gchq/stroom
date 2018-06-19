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
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.streamstore.StreamRetentionExecutor;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamMetaService;
import stroom.data.meta.api.StreamProperties;
import stroom.data.meta.api.StreamStatus;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TestStreamRetentionExecutor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStreamRetentionExecutor.class);
    private static final int RETENTION_PERIOD_DAYS = 1;

    @Inject
    private StreamMetaService streamMetaService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private StreamRetentionExecutor streamRetentionExecutor;

    @Test
    public void testMultipleRuns() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info("now: {}", DateUtil.createNormalDateTimeString(now));
        LOGGER.info("timeOutsideRetentionPeriod: {}", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

        // save two streams, one inside retention period, one outside
        final DocRef feedRef = feedStore.createDocument(feedName);
        final FeedDoc feedDoc = feedStore.readDocument(feedRef);
        feedDoc.setRetentionDayAge(RETENTION_PERIOD_DAYS);
        feedStore.writeDocument(feedDoc);

        Stream streamInsideRetention = streamMetaService.createStream(
                new StreamProperties.Builder()
                        .feedName(feedName)
                        .streamTypeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(now)
                        .statusMs(now)
                        .build());
        Stream streamOutsideRetention = streamMetaService.createStream(
                new StreamProperties.Builder()
                        .feedName(feedName)
                        .streamTypeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(timeOutsideRetentionPeriod)
                        .statusMs(timeOutsideRetentionPeriod)
                        .build());

        // Streams are locked initially so unlock.
        streamMetaService.updateStatus(streamInsideRetention.getId(), StreamStatus.UNLOCKED);
        streamMetaService.updateStatus(streamOutsideRetention.getId(), StreamStatus.UNLOCKED);

        feedStore.writeDocument(feedDoc);

        dumpStreams();

        Long lastStatusMsInside = streamInsideRetention.getStatusMs();
        Long lastStatusMsOutside = streamOutsideRetention.getStatusMs();

        // run the stream retention task which should 'delete' one stream
        streamRetentionExecutor.exec();

        streamInsideRetention = streamMetaService.getStream(streamInsideRetention.getId(), true);
        streamOutsideRetention = streamMetaService.getStream(streamOutsideRetention.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetention.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetention.getStatus());
        // no change to the record
        Assert.assertEquals(lastStatusMsInside, streamInsideRetention.getStatusMs());
        // record changed
        Assert.assertTrue(streamOutsideRetention.getStatusMs() > lastStatusMsOutside);

        lastStatusMsInside = streamInsideRetention.getStatusMs();
        lastStatusMsOutside = streamOutsideRetention.getStatusMs();

        // run the task again, but this time no changes should be made as the
        // one outside the retention period is already 'deleted'
        streamRetentionExecutor.exec();

        streamInsideRetention = streamMetaService.getStream(streamInsideRetention.getId(), true);
        streamOutsideRetention = streamMetaService.getStream(streamOutsideRetention.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetention.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetention.getStatus());
        // no change to the records
        Assert.assertEquals(lastStatusMsInside, streamInsideRetention.getStatusMs());
        Assert.assertEquals(lastStatusMsOutside, streamOutsideRetention.getStatusMs());
    }

    private void dumpStreams() {
        final BaseResultList<Stream> streams = streamMetaService.find(new FindStreamCriteria());

        Assert.assertEquals(2, streams.size());

        for (final Stream stream : streams) {
            LOGGER.info("stream: {}, createMs: {}, statusMs: {}, status: {}", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
