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

package stroom.feed.impl;

import event.logging.BaseObject;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.feed.shared.FeedDoc;

class FeedDocObjectInfoProvider implements ObjectInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedDocObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final FeedDoc feed = (FeedDoc) obj;

        String description = null;

        // Add name.
        try {
            description = feed.getDescription();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to get feed description!", e);
        }

        final Object object = new Object();
        object.setType(feed.getType());
        object.setId(feed.getUuid());
        object.setName(feed.getName());
        object.setDescription(description);

        try {
            object.getData()
                    .add(EventLoggingUtil.createData("FeedStatus", feed.getStatus().getDisplayValue()));
            // Stream type is now lazy
            if (feed.getStreamType() != null) {
                object.getData().add(EventLoggingUtil.createData("StreamType", feed.getStreamType()));
            }
            object.getData().add(EventLoggingUtil.createData("DataEncoding", feed.getEncoding()));
            object.getData().add(EventLoggingUtil.createData("ContextEncoding", feed.getContextEncoding()));
            object.getData().add(EventLoggingUtil.createData("RetentionDayAge",
                    String.valueOf(feed.getRetentionDayAge())));
            object.getData()
                    .add(EventLoggingUtil.createData("Reference", Boolean.toString(feed.isReference())));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
