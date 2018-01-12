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
import stroom.entity.shared.Period;
import stroom.policy.server.DataRetentionExecutor.Progress;
import stroom.policy.server.DataRetentionExecutor.Tracker;
import stroom.policy.shared.DataRetentionPolicy;
import stroom.policy.shared.DataRetentionRule;
import stroom.query.shared.ExpressionBuilder;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.ExpressionTerm.Condition;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;

import java.util.Collections;

public class TestDataRetentionExecutor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestDataRetentionExecutor.class);

    @Test
    public void testTracker() {
        final ExpressionBuilder builder = new ExpressionBuilder(true, Op.AND);
        builder.addTerm("Feed", Condition.EQUALS, "TEST_FEED");
        final DataRetentionRule rule = createRule(1, builder.build(),1, stroom.streamstore.shared.TimeUnit.DAYS);
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

    private DataRetentionRule createRule(final int num, final ExpressionOperator expression, final int age, final stroom.streamstore.shared.TimeUnit timeUnit) {
        return new DataRetentionRule(num, System.currentTimeMillis(), "rule " + num, true, expression, age, timeUnit, false);
    }
}
