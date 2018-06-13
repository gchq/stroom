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
import stroom.docstore.shared.Doc;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.Document;
import stroom.entity.shared.NamedEntity;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.streamstore.meta.api.Stream;

class BasicEventInfoProvider implements EventInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicEventInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        if (obj instanceof Stream) {
            final Stream stream = (Stream) obj;

            final Object object = new Object();
            object.setType("Stream");
            object.setId(String.valueOf(stream.getId()));
            if (stream.getFeedName() != null) {
                EventLoggingUtil.createData("Feed", stream.getFeedName());
            }
            if (stream.getStreamTypeName() != null) {
                object.getData().add(EventLoggingUtil.createData("StreamType", stream.getStreamTypeName()));
            }
            return object;

        } else if (obj instanceof BaseEntity) {
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

            final Object object = new Object();
            object.setType(type);
            object.setId(id);
            object.setName(name);
            object.setDescription(description);

            return object;

        } else if (obj instanceof Doc) {
            final Doc doc = (Doc) obj;

            String description = null;

            // Add name.
            if (doc instanceof PipelineDoc) {
                try {
                    final PipelineDoc pipelineDoc = (PipelineDoc) doc;
                    description = pipelineDoc.getDescription();
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to get pipeline description!", e);
                }
            } else if (doc instanceof FeedDoc) {
                try {
                    final FeedDoc feed = (FeedDoc) doc;
                    description = feed.getDescription();
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to get feed description!", e);
                }
            }

            final Object object = new Object();
            object.setType(doc.getType());
            object.setId(doc.getUuid());
            object.setName(doc.getName());
            object.setDescription(description);

            if (doc instanceof FeedDoc) {
                try {
                    final FeedDoc feed = (FeedDoc) doc;
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
            for (final char c : chars) {
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
