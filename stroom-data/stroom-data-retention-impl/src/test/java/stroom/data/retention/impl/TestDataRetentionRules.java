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

package stroom.data.retention.impl;


import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.TimeUnit;
import stroom.query.api.v2.ExpressionOperator;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataRetentionRules {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionRules.class);

//    @Test
//    void testProgress() {
//        final Period ageRange = new Period(DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z"), DateUtil.parseNormalDateTimeString("2010-01-02T00:00:00.000Z"));
//
//        final Progress progress = new Progress(ageRange, 100);
//        progress.nextStream(12345L, DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));
//
//        LOGGER.info("stream " + progress.toString());
//
//        assertThat(progress.toString()).isEqualTo("age between 2010-01-01T00:00:00.000Z and 2010-01-02T00:00:00.000Z (1 of 100), 1% complete, current stream id=12345");
//    }

    @Test
    void testSubList() {
        List<Integer> streamIdDeleteList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            streamIdDeleteList.add(i);
        }

        final int batchSize = 10;
        while (streamIdDeleteList.size() >= batchSize) {
            final List<Integer> batch = new ArrayList<>(streamIdDeleteList.subList(0, batchSize));
            streamIdDeleteList = new ArrayList<>(streamIdDeleteList.subList(batchSize, streamIdDeleteList.size()));

            assertThat(batch.size()).isEqualTo(10);
        }

        assertThat(streamIdDeleteList.size()).isEqualTo(0);
    }

    private DataRetentionRule createRule(final int num, final ExpressionOperator expression, final int age, final TimeUnit timeUnit) {
        return new DataRetentionRule(num, System.currentTimeMillis(), "rule " + num, true, expression, age, timeUnit, false);
    }
}
