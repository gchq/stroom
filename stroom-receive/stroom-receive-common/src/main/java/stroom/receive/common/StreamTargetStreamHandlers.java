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

package stroom.receive.common;

import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.proxy.StroomStatusCode;

import jakarta.inject.Inject;

import java.util.function.Consumer;

public class StreamTargetStreamHandlers implements StreamHandlers {

    private final Store store;
    private final FeedProperties feedProperties;
    private final MetaService metaService;
    private final MetaStatistics metaDataStatistics;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    @Inject
    public StreamTargetStreamHandlers(final Store store,
                                      final FeedProperties feedProperties,
                                      final MetaService metaService,
                                      final MetaStatistics metaDataStatistics,
                                      final VolumeGroupNameProvider volumeGroupNameProvider) {
        this.store = store;
        this.feedProperties = feedProperties;
        this.metaService = metaService;
        this.metaDataStatistics = metaDataStatistics;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    @Override
    public void handle(final String feedName,
                       final String typeName,
                       final AttributeMap attributeMap,
                       final Consumer<StreamHandler> consumer) {
        StreamTargetStreamHandler streamHandler = null;
        try {
            if (feedName == null || feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }

            String type = typeName;
            if (type == null || type.isEmpty()) {
                // If no type name is supplied then get the default for the feed.
                type = feedProperties.getStreamTypeName(feedName);
            }

            // Validate the data type name.
            if (!metaService.getTypes().contains(type)) {
                throw new StroomStreamException(StroomStatusCode.UNEXPECTED_DATA_TYPE, attributeMap);
            }

            streamHandler = new StreamTargetStreamHandler(
                    store,
                    feedProperties,
                    metaDataStatistics,
                    volumeGroupNameProvider,
                    feedName,
                    type,
                    attributeMap);
            consumer.accept(streamHandler);
            streamHandler.close();
        } catch (final RuntimeException e) {
            if (streamHandler != null) {
                streamHandler.error();
            }
            throw e;
        }
    }
}
