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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SizeAwareInputStream;
import stroom.docref.DocRef;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.state.MetaDataProvider;
import stroom.pipeline.state.MetaHolder;
import stroom.util.date.DateUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StreamMetaDataProvider implements MetaDataProvider {

    private static final int MINIMUM_BYTE_COUNT = 10;
    private static final String FEED = "Feed";
    private static final String STREAM_TYPE = "StreamType";
    private static final String CREATED_TIME = "CreatedTime";
    private static final String EFFECTIVE_TIME = "EffectiveTime";
    private static final String PIPELINE = "Pipeline";

    private final MetaHolder metaHolder;
    private final PipelineStore pipelineStore;

    private final Map<String, String> parentData = new ConcurrentHashMap<>();
    private AttributeMap metaData;
    private long lastMetaStreamIndex = -1;

    public StreamMetaDataProvider(final MetaHolder metaHolder,
                                  final PipelineStore pipelineStore) {
        this.metaHolder = metaHolder;
        this.pipelineStore = pipelineStore;
    }

    @Override
    public String get(final String key) {
        if (key == null || key.length() == 0) {
            return null;
        }

        if (key.equalsIgnoreCase(FEED)) {
            return getFeed();
        } else if (key.equalsIgnoreCase(STREAM_TYPE)) {
            return getStreamType();
        } else if (key.equalsIgnoreCase(CREATED_TIME)) {
            return getCreatedTime();
        } else if (key.equalsIgnoreCase(EFFECTIVE_TIME)) {
            return getEffectiveTime();
        } else if (key.equalsIgnoreCase(PIPELINE)) {
            return getPipeline();
        }

        return getMetaData().get(key);
    }

    @Override
    public AttributeMap getMetaData() {
        try {
            // Determine if we need to read the Meta meta.
            if (metaData == null || lastMetaStreamIndex != metaHolder.getPartIndex()) {
                metaData = new AttributeMap();
                lastMetaStreamIndex = metaHolder.getPartIndex();

                // Setup meta data.
                final InputStreamProvider provider = metaHolder.getInputStreamProvider();
                if (provider != null) {
                    // Get the input stream.
                    final SizeAwareInputStream inputStream = provider.get(StreamTypeNames.META);

                    // Make sure we got an input stream.
                    if (inputStream != null) {
                        // Only use meta data if we actually have some.
                        final long byteCount = inputStream.size();
                        if (byteCount > MINIMUM_BYTE_COUNT) {
                            AttributeMapUtil.read(inputStream, metaData);
                        }
                    }
                }
            }
            return metaData;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getFeed() {
        return parentData.computeIfAbsent(FEED, k -> {
            final Meta meta = metaHolder.getMeta();
            if (meta != null) {
                return meta.getFeedName();
            }
            return null;
        });
    }

    private String getStreamType() {
        return parentData.computeIfAbsent(STREAM_TYPE, k -> {
            final Meta meta = metaHolder.getMeta();
            if (meta != null) {
                return meta.getTypeName();
            }
            return null;
        });
    }

    private String getCreatedTime() {
        return parentData.computeIfAbsent(CREATED_TIME, k -> {
            final Meta meta = metaHolder.getMeta();
            if (meta != null) {
                return DateUtil.createNormalDateTimeString(meta.getCreateMs());
            }
            return null;
        });
    }

    private String getEffectiveTime() {
        return parentData.computeIfAbsent(EFFECTIVE_TIME, k -> {
            final Meta meta = metaHolder.getMeta();
            if (meta != null) {
                return DateUtil.createNormalDateTimeString(meta.getEffectiveMs());
            }
            return null;
        });
    }

    private String getPipeline() {
        return parentData.computeIfAbsent(PIPELINE, k -> {
            final Meta meta = metaHolder.getMeta();
            if (meta != null && meta.getPipelineUuid() != null) {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE,
                        meta.getPipelineUuid()));
                if (pipelineDoc != null) {
                    return pipelineDoc.getName();
                }
            }
            return null;
        });
    }
}
