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


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.retention.impl.DataRetentionExecutor;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// TODO : @66 FIX DATA RETENTION

@Disabled
class TestStreamRetentionExecutor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStreamRetentionExecutor.class);
    private static final int RETENTION_PERIOD_DAYS = 1;

    @Inject
    private MetaService metaService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private DataRetentionExecutor streamRetentionExecutor;

    @Test
    void testMultipleRuns() {
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

        Meta metaInsideRetention = metaService.create(
                new MetaProperties.Builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(now)
                        .statusMs(now)
                        .build());
        Meta metaOutsideRetention = metaService.create(
                new MetaProperties.Builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(timeOutsideRetentionPeriod)
                        .statusMs(timeOutsideRetentionPeriod)
                        .build());

        // Streams are locked initially so unlock.
        metaService.updateStatus(metaInsideRetention, Status.LOCKED, Status.UNLOCKED);
        metaService.updateStatus(metaOutsideRetention, Status.LOCKED, Status.UNLOCKED);

        feedStore.writeDocument(feedDoc);

        dumpStreams();

        Long lastStatusMsInside = metaInsideRetention.getStatusMs();
        Long lastStatusMsOutside = metaOutsideRetention.getStatusMs();

        // run the stream retention task which should 'delete' one stream
        streamRetentionExecutor.exec();

        metaInsideRetention = metaService.getMeta(metaInsideRetention.getId(), true);
        metaOutsideRetention = metaService.getMeta(metaOutsideRetention.getId(), true);

        dumpStreams();

        assertThat(metaInsideRetention.getStatus()).isEqualTo(Status.UNLOCKED);
        assertThat(metaOutsideRetention.getStatus()).isEqualTo(Status.DELETED);
        // no change to the record
        assertThat(metaInsideRetention.getStatusMs()).isEqualTo(lastStatusMsInside);
        // record changed
        assertThat(metaOutsideRetention.getStatusMs() > lastStatusMsOutside).isTrue();

        lastStatusMsInside = metaInsideRetention.getStatusMs();
        lastStatusMsOutside = metaOutsideRetention.getStatusMs();

        // run the task again, but this time no changes should be made as the
        // one outside the retention period is already 'deleted'
        streamRetentionExecutor.exec();

        metaInsideRetention = metaService.getMeta(metaInsideRetention.getId(), true);
        metaOutsideRetention = metaService.getMeta(metaOutsideRetention.getId(), true);

        dumpStreams();

        assertThat(metaInsideRetention.getStatus()).isEqualTo(Status.UNLOCKED);
        assertThat(metaOutsideRetention.getStatus()).isEqualTo(Status.DELETED);
        // no change to the records
        assertThat(metaInsideRetention.getStatusMs()).isEqualTo(lastStatusMsInside);
        assertThat(metaOutsideRetention.getStatusMs()).isEqualTo(lastStatusMsOutside);
    }

    private void dumpStreams() {
        final BaseResultList<Meta> list = metaService.find(new FindMetaCriteria());

        assertThat(list.size()).isEqualTo(2);

        for (final Meta meta : list) {
            LOGGER.info("meta: {}, createMs: {}, statusMs: {}, status: {}", meta,
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()),
                    DateUtil.createNormalDateTimeString(meta.getStatusMs()), meta.getStatus());
        }
    }
}
