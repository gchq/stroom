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

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.query.shared.ExpressionBuilder;
import stroom.query.shared.ExpressionTerm.Condition;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.ExpressionTerm;
import stroom.streamstore.server.DataRetentionExecutor;
import stroom.streamstore.server.DataRetentionService;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.DataRetentionPolicy;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class TestDataRetentionExecutor extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestDataRetentionExecutor.class);

    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private FeedService feedService;
    @Resource
    private DataRetentionExecutor dataRetentionExecutor;
    @Resource
    private DataRetentionService dataRetentionService;

    private static final int RETENTION_PERIOD_DAYS = 1;

    @Test
    public void testMultipleRuns() {
        Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info("now: %s", DateUtil.createNormalDateTimeString(now));
        LOGGER.info("timeOutsideRetentionPeriod: %s", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

        // save two streams, one inside retention period, one outside
        final ExpressionBuilder builder = new ExpressionBuilder(true, Op.AND);
        builder.addTerm("Feed", Condition.EQUALS, feed.getName());
        final DataRetentionRule rule = new DataRetentionRule(builder.build(), RETENTION_PERIOD_DAYS, stroom.streamstore.shared.TimeUnit.DAYS, false);
        final DataRetentionPolicy currentPolicy = dataRetentionService.load();
        final DataRetentionPolicy dataRetentionPolicy = new DataRetentionPolicy(Collections.singletonList(rule));
        if (currentPolicy != null) {
            dataRetentionPolicy.setVersion(currentPolicy.getVersion());
        }
        dataRetentionService.save(dataRetentionPolicy);

//        feed.setRetentionDayAge(RETENTION_PERIOD_DAYS);
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
        dataRetentionExecutor.exec();

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
        dataRetentionExecutor.exec();

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
            LOGGER.info("stream: %s, createMs: %s, statusMs: %s, status: %s", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
