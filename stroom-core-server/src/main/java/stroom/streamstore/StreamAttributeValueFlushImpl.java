/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.StroomPropertyService;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.util.date.DateUtil;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.lifecycle.StroomShutdown;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
class StreamAttributeValueFlushImpl implements StreamAttributeValueFlush {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeValueFlushImpl.class);

    public static final int BATCH_SIZE = 1000;

    private final StreamAttributeKeyService streamAttributeKeyService;
    private final StreamAttributeValueService streamAttributeValueService;
    private final StreamAttributeValueServiceTransactionHelper streamAttributeValueServiceTransactionHelper;
    private final StroomPropertyService stroomPropertyService;
    private final ClusterLockService clusterLockService;

    private final Queue<AsyncFlush> queue = new ConcurrentLinkedQueue<>();

    @Inject
    StreamAttributeValueFlushImpl(final StreamAttributeKeyService streamAttributeKeyService,
                                  final StreamAttributeValueService streamAttributeValueService,
                                  final StreamAttributeValueServiceTransactionHelper streamAttributeValueServiceTransactionHelper,
                                  final StroomPropertyService stroomPropertyService,
                                  final ClusterLockService clusterLockService) {
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.streamAttributeValueService = streamAttributeValueService;
        this.streamAttributeValueServiceTransactionHelper = streamAttributeValueServiceTransactionHelper;
        this.stroomPropertyService = stroomPropertyService;
        this.clusterLockService = clusterLockService;
    }

    @Override
    public void persitAttributes(final Stream stream, final boolean append, final MetaMap metaMap) {
        queue.add(new AsyncFlush(stream, append, metaMap));
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @StroomShutdown
    public void shutdown() {
        flush();

    }

    /**
     * @return The oldest stream attribute that we should keep
     */
    private long getApplicableStreamAgeMs() {
        final String streamAttributeDatabaseAge = stroomPropertyService.getProperty("stroom.streamAttributeDatabaseAge");

        long applicableStreamAgeMs = 0;

        if (streamAttributeDatabaseAge != null && !streamAttributeDatabaseAge.isEmpty()) {
            final long duration = ModelStringUtil.parseDurationString(streamAttributeDatabaseAge);
            applicableStreamAgeMs = System.currentTimeMillis() - duration;
        }
        return applicableStreamAgeMs;
    }

    @Override
    @StroomFrequencySchedule("10s")
    // @Transactional
    public void flush() {
        final List<StreamAttributeKey> keys = streamAttributeKeyService.findAll();

        boolean ranOutOfItems = false;

        final long applicableStreamAgeMs = getApplicableStreamAgeMs();

        while (!ranOutOfItems) {
            final FindStreamAttributeValueCriteria criteria = new FindStreamAttributeValueCriteria();

            final ArrayList<AsyncFlush> batchInsert = new ArrayList<>();
            AsyncFlush item;
            while ((item = queue.poll()) != null && batchInsert.size() < BATCH_SIZE) {
                batchInsert.add(item);
                criteria.obtainStreamIdSet().add(item.getStream().getId());

            }
            if (batchInsert.size() < BATCH_SIZE) {
                ranOutOfItems = true;
            }

            if (batchInsert.size() > 0) {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();

                LOGGER.debug("flush() - Processing batch of {}, queue size is {}", batchInsert.size(), queue.size());

                int skipCount = 0;

                // Key by the StreamAttributeKey pk
                final Map<Long, Map<Long, StreamAttributeValue>> streamToAttributeMap = new HashMap<>();
                for (final StreamAttributeValue value : streamAttributeValueService.find(criteria)) {
                    streamToAttributeMap.computeIfAbsent(value.getStreamId(), k -> new HashMap<>())
                            .put(value.getStreamAttributeKeyId(), value);
                }

                final List<StreamAttributeValue> batchUpdate = new ArrayList<>();

                // Work out the batch inserts
                for (final StreamAttributeKey streamMDKey : keys) {
                    for (final AsyncFlush asyncFlush : batchInsert) {
                        if (asyncFlush.getStream().getCreateMs() > applicableStreamAgeMs) {
                            // Found a key
                            if (asyncFlush.getMetaMap().containsKey(streamMDKey.getName())) {
                                final String newValue = asyncFlush.getMetaMap().get(streamMDKey.getName());
                                boolean dirty = false;
                                StreamAttributeValue streamAttributeValue = null;
                                final Map<Long, StreamAttributeValue> map = streamToAttributeMap
                                        .get(asyncFlush.getStream().getId());
                                if (map != null) {
                                    streamAttributeValue = map.get(streamMDKey.getId());
                                }

                                // Existing Item
                                if (streamAttributeValue != null) {
                                    if (streamMDKey.getFieldType().isNumeric()) {
                                        final Long oldValueLong = streamAttributeValue.getValueNumber();
                                        final Long newValueLong = Long.parseLong(newValue);

                                        if (!oldValueLong.equals(newValueLong)) {
                                            dirty = true;
                                            streamAttributeValue.setValueNumber(newValueLong);
                                        }
                                    } else {
                                        final String oldValue = streamAttributeValue.getValueString();

                                        if (!oldValue.equals(newValue)) {
                                            dirty = true;
                                            streamAttributeValue.setValueString(newValue);
                                        }
                                    }

                                } else {
                                    dirty = true;
                                    streamAttributeValue = new StreamAttributeValue(asyncFlush.getStream().getId(), streamMDKey,
                                            newValue);
                                }

                                if (dirty) {
                                    batchUpdate.add(streamAttributeValue);
                                }

                            }
                        } else {
                            skipCount++;
                            LOGGER.debug("flush() - Skipping flush of old stream attributes {} {}",
                                    asyncFlush.getStream(), DateUtil.createNormalDateTimeString(applicableStreamAgeMs));
                        }
                    }
                }

                // We might have no keys so will not have built any batch
                // updates.
                if (batchUpdate.size() > 0) {
                    streamAttributeValueServiceTransactionHelper.saveBatch(batchUpdate);
                }

                if (logExecutionTime.getDuration() > 1000) {
                    LOGGER.warn("flush() - Saved {} updates, skipped {}, queue size is {}, completed in {}", batchUpdate.size(), skipCount, queue.size(), logExecutionTime);
                } else {
                    LOGGER.debug("flush() - Saved {} updates, skipped {}, queue size is {}, completed in {}", batchUpdate.size(), skipCount, queue.size(), logExecutionTime);
                }
            }
        }
    }

    public static class AsyncFlush {
        private final Stream stream;
        private final boolean append;
        private final MetaMap metaMap;

        public AsyncFlush(final Stream stream, final boolean append, final MetaMap metaMap) {
            this.stream = stream;
            this.append = append;
            this.metaMap = metaMap;
        }

        public Stream getStream() {
            return stream;
        }

        public boolean isAppend() {
            return append;
        }

        public MetaMap getMetaMap() {
            return metaMap;
        }
    }
}
