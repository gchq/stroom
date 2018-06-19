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

package stroom.streamtask;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.DictionaryStore;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamMetaService;
import stroom.data.meta.api.StreamProperties;
import stroom.data.meta.api.StreamStatus;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class TestDataRetentionTransactionHelper extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionTransactionHelper.class);

    @Inject
    private StreamMetaService streamMetaService;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private DataSource dataSource;

    private static final int RETENTION_PERIOD_DAYS = 1;

    @Test
    public void testRowCount() throws SQLException {
        try (final Connection connection = dataSource.getConnection()) {
            final String feedName = FileSystemTestUtil.getUniqueTestString();

            final long now = System.currentTimeMillis();
            final long timeOutsideRetentionPeriod = now - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                    - TimeUnit.MINUTES.toMillis(1);

            LOGGER.info("now: %s", DateUtil.createNormalDateTimeString(now));
            LOGGER.info("timeOutsideRetentionPeriod: %s", DateUtil.createNormalDateTimeString(timeOutsideRetentionPeriod));

            final Stream streamInsideRetention = streamMetaService.createStream(
                    new StreamProperties.Builder()
                            .feedName(feedName)
                            .streamTypeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(now)
                            .statusMs(now)
                            .build());

            final Stream streamOutsideRetention = streamMetaService.createStream(
                    new StreamProperties.Builder()
                            .feedName(feedName)
                            .streamTypeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(timeOutsideRetentionPeriod)
                            .statusMs(now)
                            .build());

            // Streams are locked initially so unlock.
            streamMetaService.updateStatus(streamInsideRetention.getId(), StreamStatus.UNLOCKED);
            streamMetaService.updateStatus(streamOutsideRetention.getId(), StreamStatus.UNLOCKED);

            dumpStreams();

            // run the stream retention task which should 'delete' one stream
            final Period ageRange = new Period(null, timeOutsideRetentionPeriod + 1);

            // TODO : @66 Re-implement finding streams for data retention
//            try (final DataRetentionStreamFinder dataRetentionStreamFinder = new DataRetentionStreamFinder(connection, dictionaryStore)) {
//                final long count = dataRetentionStreamFinder.getRowCount(ageRange, Collections.singleton(StreamDataSource.STREAM_ID));
//                Assert.assertEquals(1, count);
//            }
        }
    }

    private void dumpStreams() {
        final BaseResultList<Stream> streams = streamMetaService.find(new FindStreamCriteria());

        Assert.assertEquals(2, streams.size());

        for (final Stream stream : streams) {
            LOGGER.info("stream: %s, createMs: %s, statusMs: %s, status: %s", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
