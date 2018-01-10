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

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.policy.server.DataRetentionTransactionHelper;
import stroom.streamstore.server.StreamFields;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class TestDataRetentionTransactionHelper extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestDataRetentionTransactionHelper.class);

    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private DataRetentionTransactionHelper dataRetentionTransactionHelper;

    private static final int RETENTION_PERIOD_DAYS = 1;

    @Test
    public void test() {
        Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info("now: %s", DateUtil.createNormalDateTimeString(now));
        LOGGER.info("timeOutsideRetentionPeriod: %s", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

        Stream streamInsideRetention = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, now);
        streamInsideRetention.setStatusMs(now);
        Stream streamOutsideRetention = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null,
                timeOutsideRetentionPeriod);
        streamOutsideRetention.setStatusMs(now);

        streamMaintenanceService.save(streamInsideRetention);
        streamMaintenanceService.save(streamOutsideRetention);

        dumpStreams();

        // run the stream retention task which should 'delete' one stream
        final Period ageRange = new Period(null, timeOutsideRetentionPeriod + 1);
        final long count = dataRetentionTransactionHelper.getRowCount(ageRange, Collections.singleton(StreamFields.STREAM_ID));
        Assert.assertEquals(1, count);
    }

    private void dumpStreams() {
        final BaseResultList<Stream> streams = streamStore.find(new FindStreamCriteria());

        Assert.assertEquals(2, streams.size());

        for (final Stream stream : streams) {
            LOGGER.info("stream: %s, createMs: %s, statusMs: %s, status: %s", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
