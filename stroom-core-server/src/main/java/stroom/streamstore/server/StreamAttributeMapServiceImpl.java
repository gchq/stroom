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

package stroom.streamstore.server;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.SupportsCriteriaLogging;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PermissionException;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.node.shared.Volume;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.streamstore.server.fs.FileSystemStreamTypeUtil;
import stroom.streamstore.shared.*;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.util.zip.HeaderMap;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Component
public class StreamAttributeMapServiceImpl
        implements StreamAttributeMapService, SupportsCriteriaLogging<FindStreamAttributeMapCriteria> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapServiceImpl.class);

    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "cachedPipelineEntityService")
    private PipelineEntityService pipelineEntityService;
    @Resource(name = "cachedStreamTypeService")
    private StreamTypeService streamTypeService;
    @Resource(name = "cachedStreamProcessorService")
    private StreamProcessorService streamProcessorService;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StroomEntityManager entityManager;

    @Resource
    private StreamAttributeKeyService streamAttributeKeyService;

    @Resource
    private StreamMaintenanceService streamMaintenanceService;

    @Override
    public BaseResultList<StreamAttributeMap> find(final FindStreamAttributeMapCriteria criteria)
            throws RuntimeException {
        // Cache Call
        final List<StreamAttributeMap> streamMDList = new ArrayList<>();

        final FindStreamCriteria streamCriteria = new FindStreamCriteria();
        streamCriteria.copyFrom(criteria.getFindStreamCriteria());
        streamCriteria.setOrderBy(FindStreamCriteria.ORDER_BY_CREATE_MS, OrderByDirection.DESCENDING);

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
                loadAttributeMapFromDatabase(criteria, streamMDList, streamList);
            } else {
                loadAttributeMapFromFileSystem(criteria, streamMDList, streamList);
            }
        }

        return new BaseResultList<>(streamMDList, streamList.getPageResponse().getOffset(),
                streamList.getPageResponse().getTotal(), streamList.getPageResponse().isMore());
    }

    /**
     * Load attributes from database
     */
    private void loadAttributeMapFromDatabase(final FindStreamAttributeMapCriteria criteria,
            final List<StreamAttributeMap> streamMDList, final BaseResultList<Stream> streamList) {
        final Map<Long, StreamAttributeMap> streamMap = new HashMap<>();

        final List<StreamAttributeKey> allKeys = streamAttributeKeyService.findAll();
        final Map<Long, StreamAttributeKey> keyMap = new HashMap<>();
        for (final StreamAttributeKey key : allKeys) {
            keyMap.put(key.getId(), key);
        }

        final SQLBuilder sql = new SQLBuilder();
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
        for (final Stream stream : streamList) {
            try {
                // Resolve Relations
                resolveRelations(criteria, stream);

                final StreamAttributeMap streamAttributeMap = new StreamAttributeMap(stream);
                streamMDList.add(streamAttributeMap);
                streamMap.put(stream.getId(), streamAttributeMap);

                sql.append(stream.getId());
                sql.append(",");

            } catch (final PermissionException e) {
                // The current user might not have permission to see this
                // stream.
                LOGGER.debug(e.getMessage());
            }
        }
        sql.setLength(sql.length() - 1);
        sql.append(")");

        // Status is a mandatory search

        @SuppressWarnings("unchecked")
        final List<Object[]> list = entityManager.executeNativeQueryResultList(sql);

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

    private void resolveRelations(final FindStreamAttributeMapCriteria criteria, final Stream stream) {
        if (criteria.getFetchSet().contains(Feed.ENTITY_TYPE)) {
            stream.setFeed(feedService.load(stream.getFeed()));
        }

        if (criteria.getFetchSet().contains(StreamType.ENTITY_TYPE)) {
            stream.setStreamType(streamTypeService.load(stream.getStreamType()));
        }

        if (criteria.getFetchSet().contains(StreamProcessor.ENTITY_TYPE)) {
            // We will try and load the stream processor but will ignore
            // permission failures as we don't mind users seeing streams even if
            // they do not have visibility of the processor that created the
            // stream.
            try {
                stream.setStreamProcessor(streamProcessorService.load(stream.getStreamProcessor()));
            } catch (final PermissionException e) {
                stream.setStreamProcessor(null);

                // The current user might not have permission to see this stream
                // processor.
                LOGGER.debug(e.getMessage());
            }
        }

        if (stream.getStreamProcessor() != null && criteria.getFetchSet().contains(PipelineEntity.ENTITY_TYPE)) {
            // We will try and load the pipeline but will ignore permission
            // failures as we don't mind users seeing streams even if they do
            // not have visibility of the pipeline that created the stream.
            try {
                stream.getStreamProcessor()
                        .setPipeline(pipelineEntityService.load(stream.getStreamProcessor().getPipeline()));
            } catch (final PermissionException e) {
                stream.getStreamProcessor().setPipeline(null);

                // The current user might not have permission to see this
                // pipeline.
                LOGGER.debug(e.getMessage());
            }
        }
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

        for (final StreamVolume streamVolume : volumeList) {
            StreamAttributeMap streamAttributeMap = streamMap.get(streamVolume.getStream());
            if (streamAttributeMap == null) {
                final Stream stream = streamVolume.getStream();
                // Resolve Relations
                resolveRelations(criteria, stream);

                streamAttributeMap = new StreamAttributeMap(stream);
                streamMDList.add(streamAttributeMap);
                streamMap.put(stream, streamAttributeMap);
            }

            final File manifest = FileSystemStreamTypeUtil.createChildStreamFile(streamVolume, StreamType.MANIFEST);

            if (manifest.isFile()) {
                final HeaderMap headerMap = new HeaderMap();
                try {
                    headerMap.read(new FileInputStream(manifest), true);
                } catch (final IOException ioException) {
                    LOGGER.error("loadAttributeMapFromFileSystem() %s", manifest, ioException);
                }

                for (final String name : headerMap.keySet()) {
                    final StreamAttributeKey key = keyMap.get(name);
                    final String value = headerMap.get(name);
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
