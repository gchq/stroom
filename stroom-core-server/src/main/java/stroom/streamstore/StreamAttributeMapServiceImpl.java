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

package stroom.streamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.DictionaryStore;
import stroom.docref.DocRef;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.Sort.Direction;
import stroom.entity.util.SqlBuilder;
import stroom.feed.MetaMap;
import stroom.node.shared.Volume;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.policy.DataRetentionService;
import stroom.ruleset.shared.DataRetentionPolicy;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.security.Security;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.fs.FileSystemStreamTypeUtil;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class StreamAttributeMapServiceImpl implements StreamAttributeMapService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapServiceImpl.class);

    private final PipelineStore pipelineStore;
    private final StreamTypeService streamTypeService;
    private final StreamProcessorService streamProcessorService;
    private final StreamStore streamStore;
    private final Provider<DataRetentionService> dataRetentionServiceProvider;
    private final DictionaryStore dictionaryStore;
    private final StroomEntityManager entityManager;
    private final StreamAttributeKeyService streamAttributeKeyService;
    private final StreamMaintenanceService streamMaintenanceService;
    private final Security security;

    @Inject
    StreamAttributeMapServiceImpl(@Named("cachedPipelineStore") final PipelineStore pipelineStore,
                                  @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                  @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                  final StreamStore streamStore,
                                  final Provider<DataRetentionService> dataRetentionServiceProvider,
                                  final DictionaryStore dictionaryStore,
                                  final StroomEntityManager entityManager,
                                  final StreamAttributeKeyService streamAttributeKeyService,
                                  final StreamMaintenanceService streamMaintenanceService,
                                  final Security security) {
        this.pipelineStore = pipelineStore;
        this.streamTypeService = streamTypeService;
        this.streamProcessorService = streamProcessorService;
        this.streamStore = streamStore;
        this.dataRetentionServiceProvider = dataRetentionServiceProvider;
        this.dictionaryStore = dictionaryStore;
        this.entityManager = entityManager;
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.streamMaintenanceService = streamMaintenanceService;
        this.security = security;
    }

    @Override
    public BaseResultList<StreamAttributeMap> find(final FindStreamAttributeMapCriteria criteria) {
        return security.useAsReadResult(() -> {
            // Cache Call
            final List<StreamAttributeMap> streamMDList = new ArrayList<>();

            final FindStreamCriteria streamCriteria = new FindStreamCriteria();
            streamCriteria.copyFrom(criteria.getFindStreamCriteria());
            streamCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);

            final boolean includeRelations = streamCriteria.getFetchSet().contains(StreamEntity.ENTITY_TYPE);
            streamCriteria.setFetchSet(new HashSet<>());
            if (includeRelations) {
                streamCriteria.getFetchSet().add(StreamEntity.ENTITY_TYPE);
            }
            streamCriteria.getFetchSet().add(StreamType.ENTITY_TYPE);
            // Share the page criteria
            final BaseResultList<StreamEntity> streamList = streamStore.find(streamCriteria);

            if (streamList.size() > 0) {
                // We need to decorate streams with retention rules as a processing user.
                security.asProcessingUser(() -> {
                    // Create a data retention rule decorator for adding data retention information to returned stream attribute maps.
                    List<DataRetentionRule> rules = Collections.emptyList();

                    final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
                    if (dataRetentionService != null) {
                        final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
                        if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
                            rules = dataRetentionPolicy.getRules();
                        }
                        final StreamAttributeMapRetentionRuleDecorator ruleDecorator = new StreamAttributeMapRetentionRuleDecorator(dictionaryStore, rules);

                        // Query the database for the attribute values
                        if (criteria.isUseCache()) {
                            LOGGER.info("Loading attribute map from DB");
                            loadAttributeMapFromDatabase(criteria, streamMDList, streamList, ruleDecorator);
                        } else {
                            LOGGER.info("Loading attribute map from filesystem");
                            loadAttributeMapFromFileSystem(criteria, streamMDList, streamList, ruleDecorator);
                        }
                    }
                });
            }

            return new BaseResultList<>(streamMDList, streamList.getPageResponse().getOffset(),
                    streamList.getPageResponse().getTotal(), streamList.getPageResponse().isExact());
        });
    }

    /**
     * Load attributes from database
     */
    private void loadAttributeMapFromDatabase(final FindStreamAttributeMapCriteria criteria,
                                              final List<StreamAttributeMap> streamMDList, final BaseResultList<StreamEntity> streamList, final StreamAttributeMapRetentionRuleDecorator ruleDecorator) {
        final Map<Long, StreamAttributeMap> streamMap = new HashMap<>();

        // Get a list of valid stream ids.
        final Map<EntityRef, Optional<Object>> entityCache = new HashMap<>();
        final Map<DocRef, Optional<Object>> uuidCache = new HashMap<>();
        final StringBuilder streamIds = new StringBuilder();
        for (final StreamEntity stream : streamList) {
            try {
                // Resolve Relations
                resolveRelations(criteria, stream, entityCache, uuidCache);

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

        // Add additional data retention information.
        streamMap.values().parallelStream().forEach(ruleDecorator::addMatchingRetentionRuleInfo);
    }

    private void resolveRelations(final FindStreamAttributeMapCriteria criteria, final StreamEntity stream, final Map<EntityRef, Optional<Object>> entityCache, final Map<DocRef, Optional<Object>> uuidCache) throws PermissionException {
//        if (stream.getFeed() != null && criteria.getFetchSet().contains(FeedDoc.DOCUMENT_TYPE)) {
//            final EntityRef ref = new EntityRef(Feed.ENTITY_TYPE, stream.getFeed().getId());
//            entityCache.computeIfAbsent(ref, key -> safeOptional(() -> feedService.loadById(key.id))).ifPresent(obj -> stream.setFeed((Feed) obj));
//        }
//
//        if (stream.getStreamType() != null && criteria.getFetchSet().contains(StreamType.ENTITY_TYPE)) {
//            final EntityRef ref = new EntityRef(StreamType.ENTITY_TYPE, stream.getStreamType().getId());
//            entityCache.computeIfAbsent(ref, key -> safeOptional(() -> streamTypeService.loadById(key.id))).ifPresent(obj -> stream.setStreamType((StreamType) obj));
//        }

        if (stream.getStreamProcessor() != null && criteria.getFetchSet().contains(StreamProcessor.ENTITY_TYPE)) {
            // We will try and load the stream processor but will ignore
            // permission failures as we don't mind users seeing streams even if
            // they do not have visibility of the processor that created the
            // stream.
            final EntityRef ref = new EntityRef(StreamProcessor.ENTITY_TYPE, stream.getStreamProcessor().getId());
            final Optional<Object> optional = entityCache.computeIfAbsent(ref, key -> {
                final Optional<Object> optionalStreamProcessor = safeOptional(() -> streamProcessorService.loadByIdInsecure(key.id));
                if (criteria.getFetchSet().contains(PipelineDoc.DOCUMENT_TYPE)) {
                    optionalStreamProcessor.ifPresent(proc -> {
                        final StreamProcessor streamProcessor = (StreamProcessor) proc;
                        final String pipelineUuid = streamProcessor.getPipelineUuid();
                        if (pipelineUuid != null) {
                            final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid);

                            // We will try and load the pipeline but will ignore permission
                            // failures as we don't mind users seeing streams even if they do
                            // not have visibility of the pipeline that created the stream.
                            uuidCache.computeIfAbsent(pipelineRef, innerKey -> safeOptional(() ->
                                    pipelineStore.readDocument(innerKey))).ifPresent(obj ->
                                    streamProcessor.setPipelineName(((PipelineDoc) obj).getName()));
                        }
                    });
                }
                return optionalStreamProcessor;
            });
            optional.ifPresent(obj -> stream.setStreamProcessor((StreamProcessor) obj));
        }
    }

    private static <T> Optional<T> safeOptional(final Supplier<T> supplier) {
        Optional<T> optional = Optional.empty();
        try {
            optional = Optional.ofNullable(supplier.get());
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage());
        }
        return optional;
    }

    private void loadAttributeMapFromFileSystem(final FindStreamAttributeMapCriteria criteria,
                                                final List<StreamAttributeMap> streamMDList, final BaseResultList<StreamEntity> streamList, final StreamAttributeMapRetentionRuleDecorator ruleDecorator) {
        final List<StreamAttributeKey> allKeys = streamAttributeKeyService.findAll();
        final Map<String, StreamAttributeKey> keyMap = new HashMap<>();
        for (final StreamAttributeKey key : allKeys) {
            keyMap.put(key.getName(), key);
        }

        final Map<StreamEntity, StreamAttributeMap> streamMap = new HashMap<>();

        final FindStreamVolumeCriteria findStreamVolumeCriteria = new FindStreamVolumeCriteria();
        for (final StreamEntity stream : streamList) {
            findStreamVolumeCriteria.obtainStreamIdSet().add(stream);
        }
        findStreamVolumeCriteria.getFetchSet().add(StreamEntity.ENTITY_TYPE);
        final BaseResultList<StreamVolume> volumeList = streamMaintenanceService.find(findStreamVolumeCriteria);

        final Map<EntityRef, Optional<Object>> entityCache = new HashMap<>();
        final Map<DocRef, Optional<Object>> uuidCache = new HashMap<>();
        for (final StreamVolume streamVolume : volumeList) {
            StreamAttributeMap streamAttributeMap = streamMap.get(streamVolume.getStream());
            if (streamAttributeMap == null) {
                try {
                    final StreamEntity stream = streamVolume.getStream();
                    // Resolve Relations
                    resolveRelations(criteria, stream, entityCache, uuidCache);

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
                    } catch (final RuntimeException e) {
                        LOGGER.error("loadAttributeMapFromFileSystem() ", e);
                    }
                }

                // Add additional data retention information.
                ruleDecorator.addMatchingRetentionRuleInfo(streamAttributeMap);
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
