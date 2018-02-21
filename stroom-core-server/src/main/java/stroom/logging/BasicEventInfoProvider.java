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

package stroom.logging;

import event.logging.BaseObject;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.Document;
import stroom.entity.shared.NamedEntity;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.shared.Stream;

import javax.inject.Inject;
import javax.inject.Named;

public class BasicEventInfoProvider implements EventInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicEventInfoProvider.class);

    private final StreamTypeService streamTypeService;
    private final FeedService feedService;

    @Inject
    BasicEventInfoProvider(@Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                           @Named("cachedFeedService") final FeedService feedService) {
        this.streamTypeService = streamTypeService;
        this.feedService = feedService;
    }

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        if (obj instanceof BaseEntity) {
            final BaseEntity entity = (BaseEntity) obj;

            final String type = getObjectType(entity);
            final String id = getId(entity);
            String name = null;
            String description = null;

            // Add name.
            if (entity instanceof NamedEntity) {
                final NamedEntity namedEntity = (NamedEntity) entity;
                name = namedEntity.getName();
            }

            // Add description.
            if (entity instanceof Feed) {
                try {
                    final Feed feed = (Feed) entity;
                    description = feed.getDescription();
                } catch (final Exception ex) {
                    LOGGER.error("Unable to get feed description!", ex);
                }
            } else if (entity instanceof PipelineEntity) {
                try {
                    final PipelineEntity pipelineEntity = (PipelineEntity) entity;
                    description = pipelineEntity.getDescription();
                } catch (final Exception ex) {
                    LOGGER.error("Unable to get pipeline description!", ex);
                }
            }

            final Object object = new Object();
            object.setType(type);
            object.setId(id);
            object.setName(name);
            object.setDescription(description);

            // Add unknown but useful data items.
            if (entity instanceof Feed) {
                try {
                    final Feed feed = (Feed) entity;
                    object.getData()
                            .add(EventLoggingUtil.createData("FeedStatus", feed.getStatus().getDisplayValue()));
                    // Stream type is now lazy
                    if (feed.getStreamType() != null) {
                        object.getData().add(EventLoggingUtil.createData("StreamType",
                                streamTypeService.load(feed.getStreamType()).getDisplayValue()));
                    }
                    object.getData().add(EventLoggingUtil.createData("DataEncoding", feed.getEncoding()));
                    object.getData().add(EventLoggingUtil.createData("ContextEncoding", feed.getContextEncoding()));
                    object.getData().add(EventLoggingUtil.createData("RetentionDayAge",
                            String.valueOf(feed.getRetentionDayAge())));
                    object.getData()
                            .add(EventLoggingUtil.createData("Reference", Boolean.toString(feed.isReference())));
                } catch (final Exception ex) {
                    LOGGER.error("Unable to add unknown but useful data!", ex);
                }
            } else if (entity instanceof Stream) {
                try {
                    final Stream stream = (Stream) entity;
                    if (stream.getFeed() != null) {
                        EventLoggingUtil.createData("Feed", feedService.load(stream.getFeed()).getName());
                    }
                    // Stream type is now lazy
                    if (stream.getStreamType() != null) {
                        object.getData().add(EventLoggingUtil.createData("StreamType",
                                streamTypeService.load(stream.getStreamType()).getDisplayValue()));
                    }
                } catch (final Exception ex) {
                    LOGGER.error("Unable to configure stream!", ex);
                }
            }

            return object;
        }

        return null;
    }

    private String getId(final BaseEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity instanceof Document) {
            return ((Document) entity).getUuid();
        }

        return String.valueOf(entity.getId());
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof BaseCriteria) {
            final BaseCriteria criteria = (BaseCriteria) object;

            String name = criteria.getClass().getSimpleName();
            final StringBuilder sb = new StringBuilder();
            final char[] chars = name.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                if (Character.isUpperCase(c)) {
                    sb.append(" ");
                }
                sb.append(c);
            }
            name = sb.toString().trim();
            final int start = name.indexOf(" ");
            final int end = name.lastIndexOf(" ");
            if (start != -1 && end != -1) {
                name = name.substring(start + 1, end);
            }

            return name;
        }

        return object.getClass().getSimpleName();
    }

    @Override
    public Class<?> getType() {
        return null;
    }
}
