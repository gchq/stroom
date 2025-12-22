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

package stroom.feed.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.feed.shared.FeedDoc;

import event.logging.BaseObject;
import event.logging.OtherObject;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FeedDocObjectInfoProvider implements ObjectInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedDocObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object obj) {
        final FeedDoc feed = (FeedDoc) obj;

        String description = null;

        // Add name.
        try {
            description = feed.getDescription();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to get feed description!", e);
        }

        final OtherObject.Builder<Void> builder = OtherObject.builder()
                .withType(feed.getType())
                .withId(feed.getUuid())
                .withName(feed.getName())
                .withDescription(description);

        try {
            builder.addData(EventLoggingUtil.createData("FeedStatus", feed.getStatus().getDisplayValue()));
            // Stream type is now lazy
            if (feed.getStreamType() != null) {
                builder.addData(EventLoggingUtil.createData("StreamType", feed.getStreamType()));
            }
            builder.addData(EventLoggingUtil.createData("DataEncoding", feed.getEncoding()));
            builder.addData(EventLoggingUtil.createData("ContextEncoding", feed.getContextEncoding()));
            builder.addData(EventLoggingUtil.createData("Reference", Boolean.toString(feed.isReference())));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
