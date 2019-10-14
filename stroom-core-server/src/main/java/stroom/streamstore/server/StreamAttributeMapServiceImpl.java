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

package stroom.streamstore.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.Sort.Direction;
import stroom.feed.MetaMap;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.node.shared.Volume;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.policy.server.DataRetentionService;
import stroom.ruleset.shared.DataRetentionPolicy;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.streamstore.server.fs.FileSystemStreamTypeUtil;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.server.StreamProcessorService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Component
public class StreamAttributeMapServiceImpl implements StreamAttributeMapService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapServiceImpl.class);

    private final FeedService feedService;
    private final PipelineService pipelineService;
    private final StreamTypeService streamTypeService;
    private final StreamProcessorService streamProcessorService;
    private final StreamStore streamStore;
    private final Provider<DataRetentionService> dataRetentionServiceProvider;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final StroomEntityManager entityManager;
    private final StreamAttributeKeyService streamAttributeKeyService;
    private final StreamMaintenanceService streamMaintenanceService;
    private final SecurityContext securityContext;

    @Inject
    StreamAttributeMapServiceImpl(@Named("cachedFeedService") final FeedService feedService,
                                  @Named("cachedPipelineService") final PipelineService pipelineService,
                                  @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                  @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                  final StreamStore streamStore,
                                  final Provider<DataRetentionService> dataRetentionServiceProvider,
                                  final ExpressionMatcherFactory expressionMatcherFactory,
                                  final StroomEntityManager entityManager,
                                  final StreamAttributeKeyService streamAttributeKeyService,
                                  final StreamMaintenanceService streamMaintenanceService,
                                  final SecurityContext securityContext) {
        this.feedService = feedService;
        this.pipelineService = pipelineService;
        this.streamTypeService = streamTypeService;
        this.streamProcessorService = streamProcessorService;
        this.streamStore = streamStore;
        this.dataRetentionServiceProvider = dataRetentionServiceProvider;
        this.expressionMatcherFactory = expressionMatcherFactory;
        this.entityManager = entityManager;
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.streamMaintenanceService = streamMaintenanceService;
        this.securityContext = securityContext;
    }

    @Override
    public BaseResultList<StreamAttributeMap> find(final FindStreamAttributeMapCriteria criteria)
            throws RuntimeException {
        BaseResultList<StreamAttributeMap> result;

        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            // Cache Call
            final List<StreamAttributeMap> streamMDList = new ArrayList<>();

            final FindStreamCriteria streamCriteria = new FindStreamCriteria();
            streamCriteria.copyFrom(criteria.getFindStreamCriteria());
            streamCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);

            final boolean includeRelations = streamCriteria.getFetchSet().contains(Stream.ENTITY_TYPE);
            streamCriteria.setFetchSet(new HashSet<>());
            if (includeRelations) {
                streamCriteria.getFetchSet().add(Stream.ENTITY_TYPE);
            }
            streamCriteria.getFetchSet().add(StreamType.ENTITY_TYPE);
            // Share the page criteria
            final BaseResultList<Stream> streamList = streamStore.find(streamCriteria);

            if (streamList.size() > 0) {
                // Query the database for the attribute values
                if (criteria.isUseCache()) {
                    LOGGER.info("Loading attribute map from DB");
                    loadAttributeMapFromDatabase(criteria, streamMDList, streamList);
                } else {
                    LOGGER.info("Loading attribute map from filesystem");
                    loadAttributeMapFromFileSystem(criteria, streamMDList, streamList);
                }

                // We need to decorate streams with retention rules.
                decorate(streamMDList);
            }

            result = new BaseResultList<>(streamMDList, streamList.getPageResponse().getOffset(),
                    streamList.getPageResponse().getTotal(), streamList.getPageResponse().isExact());
        }

        return result;
    }

    private void decorate(final Collection<StreamAttributeMap> streamAttributeMaps) {
        // Create a data retention rule decorator for adding data retention information to returned stream attribute maps.
        StreamAttributeMapRetentionRuleDecorator ruleDecorator = null;

        final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
        if (dataRetentionService != null) {
            // We need to decorate streams with retention rules as a processing user.
            try (final SecurityHelper sh = SecurityHelper.processingUser(securityContext)) {
                // Create a data retention rule decorator for adding data retention information to returned stream attribute maps.
                List<DataRetentionRule> rules = Collections.emptyList();
                final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
                if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
                    rules = dataRetentionPolicy.getRules();
                }
                ruleDecorator = new StreamAttributeMapRetentionRuleDecorator(expressionMatcherFactory, rules);
            }
        }

        if (ruleDecorator != null) {
            // We need to decorate streams with retention rules as a processing user.
            try (final SecurityHelper sh = SecurityHelper.processingUser(securityContext)) {
                // Add additional data retention information.
                streamAttributeMaps.parallelStream().forEach(ruleDecorator::addMatchingRetentionRuleInfo);
            }
        }
    }

    /**
     * Load attributes from database
     */
    private void loadAttributeMapFromDatabase(final FindStreamAttributeMapCriteria criteria,
                                              final List<StreamAttributeMap> streamMDList, final BaseResultList<Stream> streamList) {
        final Map<Long, StreamAttributeMap> streamMap = new HashMap<>();

        // Get a list of valid stream ids.
        final Map<EntityRef, Optional<Object>> localCache = new HashMap<>();
        final StringBuilder streamIds = new StringBuilder();
        for (final Stream stream : streamList) {
            try {
                // Resolve Relations
                resolveRelations(criteria, stream, localCache);

                final StreamAttributeMap streamAttributeMap = new StreamAttributeMap(stream);
                streamMDList.add(streamAttributeMap);
                streamMap.put(stream.getId(), streamAttributeMap);

                streamIds.append(stream.getId());
                streamIds.append(",");

            } catch (final PermissionException e) {
                // The current user might not have permission to see this
                // stream.
                LOGGER.debug(e.getMessage());
            }
        }
        if (streamIds.length() > 0) {
            streamIds.setLength(streamIds.length() - 1);
        }

        if (streamMap.size() == 0) {
            return;
        }

        final List<StreamAttributeKey> allKeys = streamAttributeKeyService.findAll();
        final Map<Long, StreamAttributeKey> keyMap = new HashMap<>();
        for (final StreamAttributeKey key : allKeys) {
            keyMap.put(key.getId(), key);
        }

        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(StreamAttributeValue.STREAM_ID);
        sql.append(", ");
        sql.append(StreamAttributeValue.STREAM_ATTRIBUTE_KEY_ID);
        sql.append(", ");
        sql.append(StreamAttributeValue.VALUE_NUMBER);
        sql.append(", ");
        sql.append(StreamAttributeValue.VALUE_STRING);
        sql.append(" FROM ");
        sql.append(StreamAttributeValue.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(StreamAttributeValue.STREAM_ID);
        sql.append(" in (");
        sql.append(streamIds.toString());
        sql.append(")");

        // Status is a mandatory search

        @SuppressWarnings("unchecked") final List<Object[]> list = entityManager.executeNativeQueryResultList(sql);

        for (final Object[] row : list) {
            final long streamId = ((Number) row[0]).longValue();
            final String key = String.valueOf(row[1]);
            String value = String.valueOf(row[2]);
            if (row[3] != null) {
                value = String.valueOf(row[3]);
            }

            final StreamAttributeKey streamAttributeKey = keyMap.get(Long.parseLong(key));
            if (streamAttributeKey != null) {
                streamMap.get(streamId).addAttribute(streamAttributeKey, value);
            }
        }
    }

    private void resolveRelations(final FindStreamAttributeMapCriteria criteria, final Stream stream, final Map<EntityRef, Optional<Object>> localCache) throws PermissionException {
        if (stream.getFeed() != null && criteria.getFetchSet().contains(Feed.ENTITY_TYPE)) {
            final EntityRef ref = new EntityRef(Feed.ENTITY_TYPE, stream.getFeed().getId());
            final Feed feed = getCached(localCache, ref, key -> feedService.loadById(key.id), false);
            stream.setFeed(feed);
        }

        if (stream.getStreamType() != null && criteria.getFetchSet().contains(StreamType.ENTITY_TYPE)) {
            final EntityRef ref = new EntityRef(StreamType.ENTITY_TYPE, stream.getStreamType().getId());
            final StreamType streamType = getCached(localCache, ref, key -> streamTypeService.loadById(key.id), true);
            stream.setStreamType(streamType);
        }

        if (stream.getStreamProcessor() != null && criteria.getFetchSet().contains(StreamProcessor.ENTITY_TYPE)) {
            final EntityRef ref = new EntityRef(StreamProcessor.ENTITY_TYPE, stream.getStreamProcessor().getId());
            final StreamProcessor streamProcessor = getCached(localCache, ref, key -> streamProcessorService.loadByIdInsecure(key.id), true);
            stream.setStreamProcessor(streamProcessor);

            if (streamProcessor != null) {
                final EntityRef pipelineRef = new EntityRef(PipelineEntity.ENTITY_TYPE, streamProcessor.getPipeline().getId());
                final PipelineEntity pipelineEntity = getCached(localCache, pipelineRef, key -> pipelineService.loadById(key.id), true);
                streamProcessor.setPipeline(pipelineEntity);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getCached(final Map<EntityRef, Optional<Object>> localCache,
                            final EntityRef ref,
                            final Function<EntityRef, Object> service,
                            final boolean asProcessingUser) {
        final Optional<Object> optional = localCache.computeIfAbsent(ref, key -> {
            try {
                if (asProcessingUser) {
                    try (final SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
                        return Optional.ofNullable(service.apply(key));
                    }
                } else {
                    return Optional.ofNullable(service.apply(key));
                }
            } catch (final RuntimeException e) {
                return Optional.of(e);
            }
        });
        if (optional.isPresent()) {
            final Object o = optional.get();
            if (o instanceof RuntimeException) {
                throw (RuntimeException) o;
            } else {
                return (T) o;
            }
        }

        return null;
    }

    private void loadAttributeMapFromFileSystem(final FindStreamAttributeMapCriteria criteria,
                                                final List<StreamAttributeMap> streamMDList, final BaseResultList<Stream> streamList) {
        final List<StreamAttributeKey> allKeys = streamAttributeKeyService.findAll();
        final Map<String, StreamAttributeKey> keyMap = new HashMap<>();
        for (final StreamAttributeKey key : allKeys) {
            keyMap.put(key.getName(), key);
        }

        final Map<Stream, StreamAttributeMap> streamMap = new HashMap<>();

        final FindStreamVolumeCriteria findStreamVolumeCriteria = new FindStreamVolumeCriteria();
        for (final Stream stream : streamList) {
            findStreamVolumeCriteria.obtainStreamIdSet().add(stream);
        }
        findStreamVolumeCriteria.getFetchSet().add(Stream.ENTITY_TYPE);
        final BaseResultList<StreamVolume> volumeList = streamMaintenanceService.find(findStreamVolumeCriteria);

        final Map<EntityRef, Optional<Object>> localCache = new HashMap<>();
        for (final StreamVolume streamVolume : volumeList) {
            StreamAttributeMap streamAttributeMap = streamMap.get(streamVolume.getStream());
            if (streamAttributeMap == null) {
                try {
                    final Stream stream = streamVolume.getStream();
                    // Resolve Relations
                    resolveRelations(criteria, stream, localCache);

                    streamAttributeMap = new StreamAttributeMap(stream);
                    streamMDList.add(streamAttributeMap);
                    streamMap.put(stream, streamAttributeMap);
                } catch (final PermissionException e) {
                    // The current user might not have permission to see this
                    // stream.
                    LOGGER.debug(e.getMessage());
                }
            }

            if (streamAttributeMap != null) {
                final Path manifest = FileSystemStreamTypeUtil.createChildStreamFile(streamVolume, StreamType.MANIFEST);

                if (Files.isRegularFile(manifest)) {
                    final MetaMap metaMap = new MetaMap();
                    try {
                        metaMap.read(Files.newInputStream(manifest), true);
                    } catch (final IOException ioException) {
                        LOGGER.error("loadAttributeMapFromFileSystem() {}", manifest, ioException);
                    }

                    for (final String name : metaMap.keySet()) {
                        final StreamAttributeKey key = keyMap.get(name);
                        final String value = metaMap.get(name);
                        if (key == null) {
                            streamAttributeMap.addAttribute(name, value);
                        } else {
                            streamAttributeMap.addAttribute(key, value);
                        }
                    }
                }
                if (criteria.getFetchSet().contains(Volume.ENTITY_TYPE)) {
                    try {
                        final Path rootFile = FileSystemStreamTypeUtil.createRootStreamFile(streamVolume.getVolume(),
                                streamVolume.getStream(), streamVolume.getStream().getStreamType());

                        final List<Path> allFiles = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile);
                        streamAttributeMap.setFileNameList(new ArrayList<>());
                        streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(rootFile));
                        for (final Path file : allFiles) {
                            streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(file));
                        }
                    } catch (final Exception e) {
                        LOGGER.error("loadAttributeMapFromFileSystem() ", e);
                    }
                }
            }
        }
    }

    @Override
    public FindStreamAttributeMapCriteria createCriteria() {
        return new FindStreamAttributeMapCriteria();
    }

    private static class EntityRef {
        private final String type;
        private final long id;

        private EntityRef(final String type, final long id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final EntityRef entityRef = (EntityRef) o;
            return id == entityRef.id &&
                    Objects.equals(type, entityRef.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }
    }
}
