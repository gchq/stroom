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

package stroom.data.retention;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.meta.shared.FindMetaCriteria;
import stroom.dictionary.impl.DictionaryStore;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Period;
import stroom.data.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataRetentionTransactionHelper extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionTransactionHelper.class);
    private static final int RETENTION_PERIOD_DAYS = 1;
    @Inject
    private MetaService metaService;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private DataSource dataSource;

    @Test
    void testRowCount() throws SQLException {
        try (final Connection connection = dataSource.getConnection()) {
            final String feedName = FileSystemTestUtil.getUniqueTestString();

            final long now = System.currentTimeMillis();
            final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                    - TimeUnit.MINUTES.toMillis(1);

            LOGGER.info("now: %s", DateUtil.createNormalDateTimeString(now));
            LOGGER.info("timeOutsideRetentionPeriod: %s", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

            final Meta metaInsideRetention = metaService.create(
                    new MetaProperties.Builder()
                            .feedName(feedName)
                            .typeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(now)
                            .statusMs(now)
                            .build());

            final Meta metaOutsideRetention = metaService.create(
                    new MetaProperties.Builder()
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

            // TODO : @66 Re-implement finding streams for data retention
//            try (final DataRetentionStreamFinder dataRetentionStreamFinder = new DataRetentionStreamFinder(connection, dictionaryStore)) {
//                final long count = dataRetentionStreamFinder.getRowCount(ageRange, Collections.singleton(StreamDataSource.ID));
//                assertThat(count).isEqualTo(1);
//            }
        }
    }

    private void dumpStreams() {
        final BaseResultList<Meta> list = metaService.find(new FindMetaCriteria());

        assertThat(list.size()).isEqualTo(2);

        for (final Meta meta : list) {
            LOGGER.info("meta: %s, createMs: %s, statusMs: %s, status: %s", meta,
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()),
                    DateUtil.createNormalDateTimeString(meta.getStatusMs()), meta.getStatus());
        }
    }
}
