/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.server;

import stroom.jobsystem.server.ClusterLockService;
import stroom.node.server.StroomPropertyService;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeKeyService;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.feed.MetaMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class StreamAttributeValueFlushImpl implements StreamAttributeValueFlush {
    private static StroomLogger LOGGER = StroomLogger.getLogger(StreamAttributeValueFlushImpl.class);

    @Resource
    private StreamAttributeKeyService streamAttributeKeyService;
    @Resource
    private StreamAttributeValueService streamAttributeValueService;
    @Resource
    private StreamAttributeValueServiceTransactionHelper streamAttributeValueServiceTransactionHelper;
    @Resource
    private StroomPropertyService stroomPropertyService;
    @Resource
    private ClusterLockService clusterLockService;

    public static final String LOCK_NAME = "StreamAttributeDelete";

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

    final Queue<AsyncFlush> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void persitAttributes(final Stream stream, final boolean append, final MetaMap metaMap) {
        queue.add(new AsyncFlush(stream, append, metaMap));
    }

    @StroomShutdown
    public void shutdown() {
        flush();

    }

    public static final int MONTH_OLD_MS = 1000 * 60 * 60 * 24 * 30;
    public static final int BATCH_SIZE = 1000;

    /**
     * @return The oldest stream attribute that we should keep
     */
    private long getApplicableStreamAgeMs() {
        final String streamAttributeDatabaseAge = stroomPropertyService.getProperty("stroom.streamAttributeDatabaseAge");

        long applicableStreamAgeMs = 0;

        if (StringUtils.hasText(streamAttributeDatabaseAge)) {
            final long duration = ModelStringUtil.parseDurationString(streamAttributeDatabaseAge);
            applicableStreamAgeMs = System.currentTimeMillis() - duration;
        }
        return applicableStreamAgeMs;
    }

    @Override
    @StroomFrequencySchedule("10s")
    @Transactional(propagation = Propagation.NEVER)
    public void flush() {
        final List<StreamAttributeKey> keys = streamAttributeKeyService.findAll();

        boolean ranOutOfItems = false;

        final long applicableStreamAgeMs = getApplicableStreamAgeMs();

        while (!ranOutOfItems) {
            final FindStreamAttributeValueCriteria criteria = new FindStreamAttributeValueCriteria();

            final ArrayList<AsyncFlush> batchInsert = new ArrayList<AsyncFlush>();
            AsyncFlush item = null;
            while ((item = queue.poll()) != null && batchInsert.size() < BATCH_SIZE) {
                batchInsert.add(item);
                criteria.obtainStreamIdSet().add(item.getStream());

            }
            if (batchInsert.size() < BATCH_SIZE) {
                ranOutOfItems = true;
            }

            if (batchInsert.size() > 0) {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();

                LOGGER.debug("flush() - Processing batch of %s, queue size is %s", batchInsert.size(), queue.size());

                int skipCount = 0;

                // Key by the StreamAttributeKey pk
                final Map<Long, Map<Long, StreamAttributeValue>> streamToAttributeMap = new HashMap<>();
                for (final StreamAttributeValue value : streamAttributeValueService.find(criteria)) {
                    Map<Long, StreamAttributeValue> map = streamToAttributeMap.get(value.getStreamId());
                    if (map == null) {
                        map = new HashMap<>();
                        streamToAttributeMap.put(value.getStreamId(), map);
                    }
                    map.put(value.getStreamAttributeKeyId(), value);
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
                                    streamAttributeValue = new StreamAttributeValue(asyncFlush.getStream(), streamMDKey,
                                            newValue);
                                }

                                if (dirty) {
                                    batchUpdate.add(streamAttributeValue);
                                }

                            }
                        } else {
                            skipCount++;
                            LOGGER.debug("flush() - Skipping flush of old stream attributes %s %s",
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
                    LOGGER.warn("flush() - Saved %s updates, skipped %s, queue size is %s, completed in %s",
                            batchUpdate.size(), skipCount, queue.size(), logExecutionTime);
                } else {
                    LOGGER.debug("flush() - Saved %s updates, skipped %s, queue size is %s, completed in %s",
                            batchUpdate.size(), skipCount, queue.size(), logExecutionTime);
                }
            }
        }
    }
}
