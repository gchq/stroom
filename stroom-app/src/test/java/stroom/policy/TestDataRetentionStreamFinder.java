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

package stroom.policy;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.shared.Data;
import stroom.data.meta.shared.DataMetaService;
import stroom.data.meta.shared.DataProperties;
import stroom.data.meta.shared.DataStatus;
import stroom.data.meta.shared.FindDataCriteria;
import stroom.dictionary.api.DictionaryStore;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataRetentionStreamFinder extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionStreamFinder.class);
    private static final int RETENTION_PERIOD_DAYS = 1;
    @Inject
    private DataMetaService streamMetaService;
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

            final Data streamInsideRetention = streamMetaService.create(
                    new DataProperties.Builder()
                            .feedName(feedName)
                            .typeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(now)
                            .statusMs(now)
                            .build());


            final Data streamOutsideRetention = streamMetaService.create(
                    new DataProperties.Builder()
                            .feedName(feedName)
                            .typeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(timeOutsideRetentionPeriod)
                            .statusMs(timeOutsideRetentionPeriod)
                            .build());

            // Streams are locked initially so unlock.
            streamMetaService.updateStatus(streamInsideRetention, DataStatus.UNLOCKED);
            streamMetaService.updateStatus(streamOutsideRetention, DataStatus.UNLOCKED);

            dumpStreams();

            // run the stream retention task which should 'delete' one stream
            final Period ageRange = new Period(null, timeOutsideRetentionPeriod + 1);

            // TODO : @66 Re-implement finding streams for data retention
//            try (final DataRetentionStreamFinder finder = new DataRetentionStreamFinder(connection, dictionaryStore)) {
//                final long count = finder.getRowCount(ageRange, Collections.singleton(StreamDataSource.STREAM_ID));
//                assertThat(count).isEqualTo(1);
//            }
        }
    }

    private void dumpStreams() {
        final BaseResultList<Data> streams = streamMetaService.find(new FindDataCriteria());

        assertThat(streams.size()).isEqualTo(2);

        for (final Data stream : streams) {
            LOGGER.info("stream: %s, createMs: %s, statusMs: %s, status: %s", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
