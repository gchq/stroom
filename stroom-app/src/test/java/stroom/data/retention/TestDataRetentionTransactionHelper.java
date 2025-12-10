/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.retention;


import stroom.data.retention.impl.DataRetentionMetaCriteriaUtil;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.Period;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataRetentionTransactionHelper extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDataRetentionTransactionHelper.class);
    private static final int RETENTION_PERIOD_DAYS = 1;

    @Inject
    private MetaService metaService;

    @Test
    void testRowCount() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - TimeUnit.MINUTES.toMillis(1);

        LOGGER.info(() -> "now: " + DateUtil.createNormalDateTimeString(now));
        LOGGER.info(() -> "timeOutsideRetentionPeriod: " + DateUtil.createNormalDateTimeString(
                timeOutsideRetentionPeriod));

        final Meta metaInsideRetention = metaService.create(
                MetaProperties.builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(now)
                        .statusMs(now)
                        .build());

        final Meta metaOutsideRetention = metaService.create(
                MetaProperties.builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(timeOutsideRetentionPeriod)
                        .statusMs(now)
                        .build());

        // Streams are locked initially so unlock.
        metaService.updateStatus(metaInsideRetention, Status.LOCKED, Status.UNLOCKED);
        metaService.updateStatus(metaOutsideRetention, Status.LOCKED, Status.UNLOCKED);

        dumpStreams();

        // run the stream retention task which should 'delete' one stream
        final Period ageRange = new Period(null, timeOutsideRetentionPeriod + 1);

        final FindMetaCriteria findMetaCriteria = DataRetentionMetaCriteriaUtil.createCriteria(ageRange,
                Collections.emptyList(),
                100);
        final ResultPage<Meta> list = metaService.find(findMetaCriteria);
        assertThat(list.size()).isEqualTo(1);
    }

    private void dumpStreams() {
        final List<Meta> list = metaService.find(new FindMetaCriteria()).getValues();

        assertThat(list.size()).isEqualTo(2);

        for (final Meta meta : list) {
            LOGGER.info(() -> "meta: " +
                    meta +
                    ", createMs:" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", statusMs: " +
                    DateUtil.createNormalDateTimeString(meta.getStatusMs()) +
                    ", status: " +
                    meta.getStatus());
        }
    }
}
