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

package stroom.pipeline.task;

import stroom.docref.DocRef;
import stroom.feed.MetaMap;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.state.MetaDataProvider;
import stroom.pipeline.state.StreamHolder;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.shared.StreamTypeNames;
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

    private final StreamHolder streamHolder;
    private final PipelineStore pipelineStore;

    private Map<String, String> parentData = new ConcurrentHashMap<>();
    private MetaMap metaData;
    private long lastMetaStreamNo;

    public StreamMetaDataProvider(final StreamHolder streamHolder,
                                  final PipelineStore pipelineStore) {
        this.streamHolder = streamHolder;
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
    public MetaMap getMetaData() {
        try {
            // Determine if we need to read the meta stream.
            if (metaData == null || lastMetaStreamNo != streamHolder.getStreamNo()) {
                metaData = new MetaMap();
                lastMetaStreamNo = streamHolder.getStreamNo();

                // Setup meta data.
                final StreamSourceInputStreamProvider provider = streamHolder.getProvider(StreamTypeNames.META);
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
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getFeed() {
        return parentData.computeIfAbsent(FEED, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null) {
                return stream.getFeedName();
            }
            return null;
        });
    }

    private String getStreamType() {
        return parentData.computeIfAbsent(STREAM_TYPE, k -> {
            final Stream stream = streamHolder.getStream();
            if (stream != null) {
                return stream.getStreamTypeName();
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
//            if (stream != null) {
//                return stream.getPipelineName();
//            }

            if (stream != null && stream.getPipelineUuid() != null) {
//                final StreamProcessor streamProcessor = streamProcessorService.load(stream.getStreamProcessor(), FETCH_SET);
//                if (streamProcessor != null && streamProcessor.getPipelineUuid() != null) {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.DOCUMENT_TYPE, stream.getPipelineUuid()));
                if (pipelineDoc != null) {
                    return pipelineDoc.getName();
                }
//                }
            }
            return null;
        });
    }
}
