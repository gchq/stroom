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
 */

package stroom.streamstore.server;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.server.SupportsCriteriaLogging;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.Sort.Direction;
import stroom.feed.MetaMap;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.node.shared.Volume;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.policy.server.DataRetentionService;
import stroom.policy.shared.DataRetentionPolicy;
import stroom.policy.shared.DataRetentionRule;
import stroom.security.SecurityContext;
import stroom.streamstore.server.fs.FileSystemStreamTypeUtil;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeKeyService;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamAttributeMapService;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamTypeService;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class StreamAttributeMapServiceImpl
        implements StreamAttributeMapService, SupportsCriteriaLogging<FindStreamAttributeMapCriteria> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapServiceImpl.class);

    private final FeedService feedService;
    private final PipelineEntityService pipelineEntityService;
    private final StreamTypeService streamTypeService;
    private final StreamProcessorService streamProcessorService;
    private final StreamStore streamStore;
    private final Provider<DataRetentionService> dataRetentionServiceProvider;
    private final DictionaryService dictionaryService;
    private final StroomEntityManager entityManager;
    private final StreamAttributeKeyService streamAttributeKeyService;
    private final StreamMaintenanceService streamMaintenanceService;
    private final SecurityContext securityContext;

    @Inject
    StreamAttributeMapServiceImpl(@Named("cachedFeedService") final FeedService feedService,
                                  @Named("cachedPipelineEntityService") final PipelineEntityService pipelineEntityService,
                                  @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                  @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                  final StreamStore streamStore,
                                  final Provider<DataRetentionService> dataRetentionServiceProvider,
                                  final DictionaryService dictionaryService,
                                  final StroomEntityManager entityManager,
                                  final StreamAttributeKeyService streamAttributeKeyService,
                                  final StreamMaintenanceService streamMaintenanceService,
                                  final SecurityContext securityContext) {
        this.feedService = feedService;
        this.pipelineEntityService = pipelineEntityService;
        this.streamTypeService = streamTypeService;
        this.streamProcessorService = streamProcessorService;
        this.streamStore = streamStore;
        this.dataRetentionServiceProvider = dataRetentionServiceProvider;
        this.dictionaryService = dictionaryService;
        this.entityManager = entityManager;
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.streamMaintenanceService = streamMaintenanceService;
        this.securityContext = securityContext;
    }

    @Override
    public BaseResultList<StreamAttributeMap> find(final FindStreamAttributeMapCriteria criteria)
            throws RuntimeException {
        BaseResultList<StreamAttributeMap> result;

        securityContext.elevatePermissions();
        try {
            // Cache Call
            final List<StreamAttributeMap> streamMDList = new ArrayList<>();

            final FindStreamCriteria streamCriteria = new FindStreamCriteria();
            streamCriteria.copyFrom(criteria.getFindStreamCriteria());
            streamCriteria.setSort(FindStreamCriteria.FIELD_CREATE_MS, Direction.DESCENDING, false);

            final boolean includeRelations = streamCriteria.getFetchSet().contains(Stream.ENTITY_TYPE);
            streamCriteria.setFetchSet(new HashSet<>());
            if (includeRelations) {
                streamCriteria.getFetchSet().add(Stream.ENTITY_TYPE);
            }
            streamCriteria.getFetchSet().add(StreamType.ENTITY_TYPE);
            // Share the page criteria
            final BaseResultList<Stream> streamList = streamStore.find(streamCriteria);

            if (streamList.size() > 0) {
                // Create a data retention rule decorator for adding data retention information to returned stream attribute maps.
                List<DataRetentionRule> rules = Collections.emptyList();

                final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
                if (dataRetentionService != null) {
                    final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
                    if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
                        rules = dataRetentionPolicy.getRules();
                    }
                    final StreamAttributeMapRetentionRuleDecorator ruleDecorator = new StreamAttributeMapRetentionRuleDecorator(dictionaryService, rules);

                    // Query the database for the attribute values
                    if (criteria.isUseCache()) {
                        LOGGER.info("Loading attribute map from DB");
                        loadAttributeMapFromDatabase(criteria, streamMDList, streamList, ruleDecorator);
                    } else {
                        LOGGER.info("Loading attribute map from filesystem");
                        loadAttributeMapFromFileSystem(criteria, streamMDList, streamList, ruleDecorator);
                    }
                }
            }

            result = new BaseResultList<>(streamMDList, streamList.getPageResponse().getOffset(),
                    streamList.getPageResponse().getTotal(), streamList.getPageResponse().isMore());
        } finally {
            securityContext.restorePermissions();
        }

        return result;
    }

    /**
     * Load attributes from database
     */
    private void loadAttributeMapFromDatabase(final FindStreamAttributeMapCriteria criteria,
                                              final List<StreamAttributeMap> streamMDList, final BaseResultList<Stream> streamList, final StreamAttributeMapRetentionRuleDecorator ruleDecorator) {
        final Map<Long, StreamAttributeMap> streamMap = new HashMap<>();

        // Get a list of valid stream ids.
        final StringBuilder streamIds = new StringBuilder();
        for (final Stream stream : streamList) {
            try {
                // Resolve Relations
                resolveRelations(criteria, stream);

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

    private void resolveRelations(final FindStreamAttributeMapCriteria criteria, final Stream stream) throws PermissionException {
        if (criteria.getFetchSet().contains(Feed.ENTITY_TYPE) && stream.getFeed() != null) {
            stream.setFeed(feedService.loadById(stream.getFeed().getId()));
        }

        if (criteria.getFetchSet().contains(StreamType.ENTITY_TYPE) && stream.getStreamType() != null) {
            stream.setStreamType(streamTypeService.loadById(stream.getStreamType().getId()));
        }

        StreamProcessor streamProcessor = null;
        if (criteria.getFetchSet().contains(StreamProcessor.ENTITY_TYPE) && stream.getStreamProcessor() != null) {
            // We will try and load the stream processor but will ignore
            // permission failures as we don't mind users seeing streams even if
            // they do not have visibility of the processor that created the
            // stream.
            try {
                streamProcessor = streamProcessorService.loadByIdInsecure(stream.getStreamProcessor().getId());
                stream.setStreamProcessor(streamProcessor);
            } catch (final PermissionException e) {
                // The current user might not have permission to see this stream
                // processor.
                LOGGER.debug(e.getMessage());
            }
        }

        if (streamProcessor != null && criteria.getFetchSet().contains(PipelineEntity.ENTITY_TYPE)) {
            // We will try and load the pipeline but will ignore permission
            // failures as we don't mind users seeing streams even if they do
            // not have visibility of the pipeline that created the stream.
            try {
                streamProcessor.setPipeline(pipelineEntityService.load(streamProcessor.getPipeline()));
            } catch (final PermissionException e) {
                streamProcessor.setPipeline(null);

                // The current user might not have permission to see this
                // pipeline.
                LOGGER.debug(e.getMessage());
                throw e;
            }
        }
    }

    private void loadAttributeMapFromFileSystem(final FindStreamAttributeMapCriteria criteria,
                                                final List<StreamAttributeMap> streamMDList, final BaseResultList<Stream> streamList, final StreamAttributeMapRetentionRuleDecorator ruleDecorator) {
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

        for (final StreamVolume streamVolume : volumeList) {
            StreamAttributeMap streamAttributeMap = streamMap.get(streamVolume.getStream());
            if (streamAttributeMap == null) {
                try {
                    final Stream stream = streamVolume.getStream();
                    // Resolve Relations
                    resolveRelations(criteria, stream);

                    streamAttributeMap = new StreamAttributeMap(stream);
                    streamMDList.add(streamAttributeMap);
                    streamMap.put(stream, streamAttributeMap);
                } catch (final PermissionException e) {
                    // The current user might not have permission to see this
                    // stream.
                    LOGGER.debug(e.getMessage());
                }
            }

            final File manifest = FileSystemStreamTypeUtil.createChildStreamFile(streamVolume, StreamType.MANIFEST);

            if (manifest.isFile()) {
                final MetaMap metaMap = new MetaMap();
                try {
                    metaMap.read(new FileInputStream(manifest), true);
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
                    final File rootFile = FileSystemStreamTypeUtil.createRootStreamFile(streamVolume.getVolume(),
                            streamVolume.getStream(), streamVolume.getStream().getStreamType());

                    final List<File> allFiles = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile);
                    streamAttributeMap.setFileNameList(new ArrayList<>());
                    streamAttributeMap.getFileNameList().add(rootFile.getPath());
                    for (final File file : allFiles) {
                        streamAttributeMap.getFileNameList().add(file.getPath());
                    }
                } catch (final Exception e) {
                    LOGGER.error("loadAttributeMapFromFileSystem() ", e);
                }
            }

            // Add additional data retention information.
            ruleDecorator.addMatchingRetentionRuleInfo(streamAttributeMap);
        }
    }

    @Override
    public FindStreamAttributeMapCriteria createCriteria() {
        return new FindStreamAttributeMapCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamAttributeMapCriteria criteria) {
        streamStore.appendCriteria(items, criteria.getFindStreamCriteria());
    }
}
