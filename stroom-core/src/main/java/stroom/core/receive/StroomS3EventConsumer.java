/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.core.receive;


import stroom.aws.s3.shared.S3Location;
import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.S3CreateEvent;
import stroom.receive.common.S3EventConsumer;
import stroom.receive.common.StreamFactory;
import stroom.receive.common.StroomStreamException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StroomS3EventConsumer implements S3EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomS3EventConsumer.class);

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final Store store;
    private final MetaService metaService;
    private final FeedProperties feedProperties;

    @Inject
    public StroomS3EventConsumer(final AttributeMapFilterFactory attributeMapFilterFactory,
                                 final Store store,
                                 final MetaService metaService,
                                 final FeedProperties feedProperties) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.store = store;
        this.metaService = metaService;
        this.feedProperties = feedProperties;
    }

    @Override
    public void accept(final S3CreateEvent s3CreateEvent) {
        LOGGER.debug("accept() - s3CreateEvent: {}", s3CreateEvent);
        final AttributeMap attributeMap = s3CreateEvent.attributeMap();
        final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();

        final boolean canReceive;
        try {
            canReceive = attributeMapFilter.filter(attributeMap);
            LOGGER.debug("handleEvent() - s3CreateEvent: {}, isAllowed: {}", s3CreateEvent, canReceive);
            if (canReceive) {
                receiveEvent(s3CreateEvent.s3Location(), attributeMap);
            } else {
                LOGGER.debug("handleEvent() - Dropping s3CreateEvent: {}", s3CreateEvent);
            }
        } catch (final StroomStreamException e) {
            // TODO rejection has no concept when consuming s3 events as there is nobody to send the rejection to
            LOGGER.debug("handleEvent() - Rejecting s3CreateEvent: {}", s3CreateEvent);
        }
    }

    private void receiveEvent(final S3Location s3Location, final AttributeMap attributeMap) {
        // Get the effective time if one has been provided.
        String typeName = attributeMap.get(StandardHeaderArguments.TYPE);
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
        final Long effectiveMs = StreamFactory.getReferenceEffectiveTime(attributeMap, true);
        LOGGER.debug("receiveEvent() - feedName: '{}', typeName: '{}', effectiveMs: {}, s3Location: {}",
                feedName, typeName, effectiveMs, s3Location);

        if (typeName == null || typeName.isEmpty()) {
            // If no type name is supplied then get the default for the feed.
            typeName = feedProperties.getStreamTypeName(feedName);
        }

        // Validate the data type name.
        if (!metaService.getTypes().contains(typeName)) {
            throw new StroomStreamException(StroomStatusCode.UNEXPECTED_DATA_TYPE, attributeMap);
        }

        final MetaProperties metaProperties = MetaProperties.builder()
                .typeName(typeName)
                .feedName(feedName)
                .effectiveMs(effectiveMs)
                .build();

        // Don't need a target as the data is on S3 and staying put for stroom to read from it.
        store.addExistingS3Source(metaProperties, s3Location);
    }


    // --------------------------------------------------------------------------------


}
