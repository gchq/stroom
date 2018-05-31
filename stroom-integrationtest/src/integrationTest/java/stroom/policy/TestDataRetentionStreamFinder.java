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

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.DictionaryStore;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.fs.FileSystemStreamMaintenanceService;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class TestDataRetentionStreamFinder extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionStreamFinder.class);

    @Inject
    private StreamStore streamStore;
    @Inject
    private FileSystemStreamMaintenanceService streamMaintenanceService;
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

            final StreamEntity streamInsideRetention = streamStore.createStream(
                    new StreamProperties.Builder()
                            .feedName(feedName)
                            .streamTypeName(StreamType.RAW_EVENTS.getName())
                            .createMs(now)
                            .statusMs(now)
                            .build());

            final StreamEntity streamOutsideRetention = streamStore.createStream(
                    new StreamProperties.Builder()
                            .feedName(feedName)
                            .streamTypeName(StreamType.RAW_EVENTS.getName())
                            .createMs(timeOutsideRetentionPeriod)
                            .statusMs(timeOutsideRetentionPeriod)
                            .build());

            streamMaintenanceService.save(streamInsideRetention);
            streamMaintenanceService.save(streamOutsideRetention);

            dumpStreams();

            // run the stream retention task which should 'delete' one stream
            final Period ageRange = new Period(null, timeOutsideRetentionPeriod + 1);
            try (final DataRetentionStreamFinder finder = new DataRetentionStreamFinder(connection, dictionaryStore)) {
                final long count = finder.getRowCount(ageRange, Collections.singleton(StreamDataSource.STREAM_ID));
                Assert.assertEquals(1, count);
            }
        }
    }

    private void dumpStreams() {
        final BaseResultList<StreamEntity> streams = streamStore.find(new FindStreamCriteria());

        Assert.assertEquals(2, streams.size());

        for (final StreamEntity stream : streams) {
            LOGGER.info("stream: %s, createMs: %s, statusMs: %s, status: %s", stream,
                    DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                    DateUtil.createNormalDateTimeString(stream.getStatusMs()), stream.getStatus());
        }
    }
}
