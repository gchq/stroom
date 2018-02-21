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

package stroom.pipeline.state;

import stroom.feed.MetaMap;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MetaDataHolder extends AbstractHolder<MetaDataHolder> implements Holder {
    private static final int MINIMUM_BYTE_COUNT = 10;
    private static final String FEED = "Feed";
    private static final String STREAM_TYPE = "StreamType";
    private static final String CREATED_TIME = "CreatedTime";
    private static final String EFFECTIVE_TIME = "EffectiveTime";
    private static final String PIPELINE = "Pipeline";

    private static final Set<String> FETCH_SET = Collections.singleton(PipelineEntity.ENTITY_TYPE);

    private final StreamHolder streamHolder;
    private final StreamProcessorService streamProcessorService;

    private Map<String, String> parentData = new ConcurrentHashMap<>();
    private MetaMap metaData;
    private long lastMetaStreamNo;

    @Inject
    MetaDataHolder(final StreamHolder streamHolder,
                   final StreamProcessorService streamProcessorService) {
        this.streamHolder = streamHolder;
        this.streamProcessorService = streamProcessorService;
    }

    public String get(final String key) throws IOException {
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

    public MetaMap getMetaData() throws IOException {
        // Determine if we need to read the meta stream.
        if (metaData == null || lastMetaStreamNo != streamHolder.getStreamNo()) {
            metaData = new MetaMap();
            lastMetaStreamNo = streamHolder.getStreamNo();

            // Setup meta data.
            final StreamSourceInputStreamProvider provider = streamHolder.getProvider(StreamType.META);
            if (provider != null) {
                // Get the input stream.
                final StreamSourceInputStream inputStream = provider.getStream(lastMetaStreamNo);

                // Make sure we got an input stream.
                if (inputStream != null) {
                    // Only use meta data if we actually have some.
                    final long byteCount = inputStream.size();
                    if (byteCount > MINIMUM_BYTE_COUNT) {
                        metaData.read(inputStream, false);
                    }
                }
            }
        }
        return metaData;
    }

    private String getFeed() {
        return parentData.computeIfAbsent(FEED, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null && stream.getFeed() != null) {
                return stream.getFeed().getName();
            }
            return null;
        });
    }

    private String getStreamType() {
        return parentData.computeIfAbsent(STREAM_TYPE, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null) {
                return stream.getStreamType().getDisplayValue();
            }
            return null;
        });
    }

    private String getCreatedTime() {
        return parentData.computeIfAbsent(CREATED_TIME, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null) {
                return DateUtil.createNormalDateTimeString(stream.getCreateMs());
            }
            return null;
        });
    }

    private String getEffectiveTime() {
        return parentData.computeIfAbsent(EFFECTIVE_TIME, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null) {
                return DateUtil.createNormalDateTimeString(stream.getEffectiveMs());
            }
            return null;
        });
    }

    private String getPipeline() {
        return parentData.computeIfAbsent(PIPELINE, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null && stream.getStreamProcessor() != null) {
                final StreamProcessor streamProcessor = streamProcessorService.load(stream.getStreamProcessor(), FETCH_SET);
                if (streamProcessor != null && streamProcessor.getPipeline() != null) {
                    return streamProcessor.getPipeline().getName();
                }
            }
            return null;
        });
    }
}
