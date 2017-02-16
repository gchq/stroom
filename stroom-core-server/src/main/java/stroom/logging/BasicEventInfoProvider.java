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

package stroom.logging;

import event.logging.BaseObject;
import event.logging.Group;
import event.logging.Groups;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.Document;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.HasFolder;
import stroom.entity.shared.NamedEntity;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamTypeService;

import javax.inject.Inject;
import javax.inject.Named;

@Component
public class BasicEventInfoProvider implements EventInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicEventInfoProvider.class);

    private final StreamTypeService streamTypeService;
    private final FeedService feedService;
    private final FolderService folderService;

    @Inject
    BasicEventInfoProvider(@Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                           @Named("cachedFeedService") final FeedService feedService,
                           @Named("cachedFolderService") final FolderService folderService) {
        this.streamTypeService = streamTypeService;
        this.feedService = feedService;
        this.folderService = folderService;
    }

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        if (obj instanceof BaseEntity) {
            final BaseEntity entity = (BaseEntity) obj;

            if (entity instanceof Folder) {
                final Folder folder = (Folder) entity;
                try {
                    final Group group = new Group();
                    group.setType("Folder");
                    group.setId(getId(folder));
                    group.setName(folder.getName());

                    // Add groups.
                    try {
                        final Folder parentGroup = folder.getFolder();
                        if (parentGroup != null) {
                            final Groups groups = new Groups();
                            group.setGroups(groups);
                            appendGroup(groups, parentGroup);
                        }
                    } catch (final Exception ex) {
                        LOGGER.error(ex, ex);
                    }

                    return group;
                } catch (final Exception ex) {
                    LOGGER.error(ex, ex);
                }
            } else {
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
                        LOGGER.error(ex, ex);
                    }
                } else if (entity instanceof PipelineEntity) {
                    try {
                        final PipelineEntity pipelineEntity = (PipelineEntity) entity;
                        description = pipelineEntity.getDescription();
                    } catch (final Exception ex) {
                        LOGGER.error(ex, ex);
                    }
                }

                final Object object = new Object();
                object.setType(type);
                object.setId(id);
                object.setName(name);
                object.setDescription(description);

                // Add groups.
                if (entity instanceof HasFolder) {
                    try {
                        final HasFolder hasFolder = (HasFolder) entity;
                        final Folder folder = hasFolder.getFolder();
                        final Groups groups = new Groups();
                        object.setGroups(groups);
                        appendGroup(groups, folder);
                    } catch (final Exception ex) {
                        LOGGER.error(ex, ex);
                    }
                }

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
                        LOGGER.error(ex, ex);
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
                        LOGGER.error(ex, ex);
                    }
                }

                return object;
            }
        }

        return null;
    }

    private void appendGroup(final Groups groups, final Folder folder) {
        try {
            if (folder != null) {
                final Folder loaded = folderService.load(folder);

                final Group group = new Group();
                group.setType("Folder");
                group.setId(getId(loaded));
                group.setName(loaded.getName());
                groups.getGroup().add(group);
            }
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
        }
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
