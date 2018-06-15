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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.policy.server.DataRetentionExecutor;
import stroom.policy.server.DataRetentionService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.ruleset.shared.DataRetentionPolicy;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.*;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.date.DateUtil;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class TestDataRetentionExecutor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionExecutor.class);
    private static final int RETENTION_PERIOD_DAYS = 1;
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

    @Test
    public void testMultipleRuns() {
        Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info("now: %s", DateUtil.createNormalDateTimeString(now));
        LOGGER.info("timeOutsideRetentionPeriod: %s", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

        // save two streams, one inside retention period, one outside
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(true, Op.AND);
        builder.addTerm(StreamDataSource.FEED_NAME, Condition.EQUALS, feed.getName());
        final DataRetentionRule rule = createRule(1, builder.build(), RETENTION_PERIOD_DAYS, stroom.streamstore.shared.TimeUnit.DAYS);
        final DataRetentionPolicy currentPolicy = dataRetentionService.load();
        final DataRetentionPolicy dataRetentionPolicy = new DataRetentionPolicy(Collections.singletonList(rule));
        if (currentPolicy != null) {
            dataRetentionPolicy.setVersion(currentPolicy.getVersion());
        }
        dataRetentionService.save(dataRetentionPolicy);

        Stream streamInsideRetention = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, now);
        streamInsideRetention.setStatusMs(now);
        Stream streamOutsideRetention = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null,
                timeOutsideRetentionPeriod);
        streamOutsideRetention.setStatusMs(now);

        streamInsideRetention = streamMaintenanceService.save(streamInsideRetention);
        streamOutsideRetention = streamMaintenanceService.save(streamOutsideRetention);

        dumpStreams();

        Long lastStatusMsInside = streamInsideRetention.getStatusMs();
        Long lastStatusMsOutside = streamOutsideRetention.getStatusMs();

        // run the stream retention task which should 'delete' one stream
        dataRetentionExecutor.exec();

        streamInsideRetention = streamStore.loadStreamById(streamInsideRetention.getId(), true);
        streamOutsideRetention = streamStore.loadStreamById(streamOutsideRetention.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetention.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetention.getStatus());
        // no change to the record
        Assert.assertEquals(lastStatusMsInside, streamInsideRetention.getStatusMs());
        // record changed
        Assert.assertTrue(streamOutsideRetention.getStatusMs().longValue() > lastStatusMsOutside);

        lastStatusMsInside = streamInsideRetention.getStatusMs();
        lastStatusMsOutside = streamOutsideRetention.getStatusMs();

        // run the task again, but this time no changes should be made as the
        // one outside the retention period is already 'deleted'
        dataRetentionExecutor.exec();

        streamInsideRetention = streamStore.loadStreamById(streamInsideRetention.getId(), true);
        streamOutsideRetention = streamStore.loadStreamById(streamOutsideRetention.getId(), true);

        dumpStreams();

        Assert.assertEquals(StreamStatus.UNLOCKED, streamInsideRetention.getStatus());
        Assert.assertEquals(StreamStatus.DELETED, streamOutsideRetention.getStatus());
        // no change to the records
        Assert.assertEquals(lastStatusMsInside, streamInsideRetention.getStatusMs());
        Assert.assertEquals(lastStatusMsOutside, streamOutsideRetention.getStatusMs());
    }

    private DataRetentionRule createRule(final int num, final ExpressionOperator expression, final int age, final stroom.streamstore.shared.TimeUnit timeUnit) {
        return new DataRetentionRule(num, System.currentTimeMillis(), "rule " + num, true, expression, age, timeUnit, false);
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
