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

package stroom.policy.server;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Period;
import stroom.policy.server.DataRetentionExecutor.Progress;
import stroom.policy.server.DataRetentionExecutor.Tracker;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.ruleset.shared.DataRetentionPolicy;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.streamstore.shared.StreamDataSource;
import stroom.util.date.DateUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestDataRetentionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionExecutor.class);

    @Test
    public void testTracker() {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(true, Op.AND);
        builder.addTerm(StreamDataSource.FEED_NAME, Condition.EQUALS, "TEST_FEED");
        final DataRetentionRule rule = createRule(1, builder.build(), 1, stroom.streamstore.shared.TimeUnit.DAYS);
        final DataRetentionPolicy dataRetentionPolicy = new DataRetentionPolicy(Collections.singletonList(rule));
        Tracker tracker = new Tracker(100L, dataRetentionPolicy);

        tracker.save();

        Tracker tracker2 = Tracker.load();

        Assert.assertTrue(tracker.policyEquals(tracker2.getDataRetentionPolicy()));
    }

    @Test
    public void testProgress() {
        final Period ageRange = new Period(DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z"), DateUtil.parseNormalDateTimeString("2010-01-02T00:00:00.000Z"));

        final Progress progress = new Progress(ageRange, 100);
        progress.nextStream(12345L, DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));

        LOGGER.info("stream " + progress.toString());

        Assert.assertEquals("age between 2010-01-01T00:00:00.000Z and 2010-01-02T00:00:00.000Z (1 of 100), 1% complete, current stream id=12345", progress.toString());
    }

    @Test
    public void testSubList() {
        List<Integer> streamIdDeleteList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            streamIdDeleteList.add(i);
        }

        final int batchSize = 10;
        while (streamIdDeleteList.size() >= batchSize) {
            final List<Integer> batch = new ArrayList<>(streamIdDeleteList.subList(0, batchSize));
            streamIdDeleteList = new ArrayList<>(streamIdDeleteList.subList(batchSize, streamIdDeleteList.size()));

            Assert.assertEquals(10, batch.size());
        }

        Assert.assertEquals(0, streamIdDeleteList.size());
    }

    private DataRetentionRule createRule(final int num, final ExpressionOperator expression, final int age, final stroom.streamstore.shared.TimeUnit timeUnit) {
        return new DataRetentionRule(num, System.currentTimeMillis(), "rule " + num, true, expression, age, timeUnit, false);
    }
}
