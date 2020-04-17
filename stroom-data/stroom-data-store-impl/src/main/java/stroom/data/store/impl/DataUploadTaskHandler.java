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

package stroom.data.store.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.receive.common.StreamTargetStroomStreamHandler;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.io.BufferFactory;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DataUploadTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataUploadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String FILE_SEPERATOR = ".";
    private static final String GZ = "GZ";

    private final TaskContextFactory taskContextFactory;
    private final Store streamStore;
    private final FeedProperties feedProperties;
    private final MetaStatistics metaStatistics;
    private final SecurityContext securityContext;
    private final BufferFactory bufferFactory;

    @Inject
    DataUploadTaskHandler(final TaskContextFactory taskContextFactory,
                          final Store streamStore,
                          final FeedProperties feedProperties,
                          final MetaStatistics metaStatistics,
                          final SecurityContext securityContext,
                          final BufferFactory bufferFactory) {
        this.taskContextFactory = taskContextFactory;
        this.streamStore = streamStore;
        this.feedProperties = feedProperties;
        this.metaStatistics = metaStatistics;
        this.securityContext = securityContext;
        this.bufferFactory = bufferFactory;
    }

    public void uploadData(final String fileName,
                           final Path file,
                           final String feedName,
                           final String streamTypeName,
                           final Long effectiveMs,
                           final String metaData) {
        taskContextFactory.context("Download Data", taskContext ->
                uploadData(taskContext, fileName, file, feedName, streamTypeName, effectiveMs, metaData)).run();
    }

    private void uploadData(final TaskContext taskContext,
                           final String fileName,
                           final Path file,
                           final String feedName,
                           final String streamTypeName,
                           final Long effectiveMs,
                           final String metaData) {
        securityContext.secure(() -> {
            taskContext.info(file::toString);
            if (feedName == null) {
                throw new EntityServiceException("Feed not set!");
            }
            if (streamTypeName == null) {
                throw new EntityServiceException("Stream Type not set!");
            }
            if (fileName == null) {
                throw new EntityServiceException("File not set!");
            }

            final String name = fileName.toUpperCase();

            final AttributeMap attributeMap = new AttributeMap();
            if (metaData != null && metaData.trim().length() > 0) {
                try {
                    AttributeMapUtil.read(metaData.getBytes(StreamUtil.DEFAULT_CHARSET), attributeMap);
                } catch (final IOException e) {
                    LOGGER.error("uploadData()", e);
                }
            }

            if (effectiveMs != null) {
                attributeMap.put(StandardHeaderArguments.EFFECTIVE_TIME, DateUtil.createNormalDateTimeString(effectiveMs));
            }
            attributeMap.put(StandardHeaderArguments.REMOTE_FILE, fileName);
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            attributeMap.put(StandardHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString(System.currentTimeMillis()));
            attributeMap.put(StandardHeaderArguments.USER_AGENT, "STROOM-UI");

            if (name.endsWith(FILE_SEPERATOR + StandardHeaderArguments.COMPRESSION_ZIP)) {
                attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
                uploadZipFile(taskContext, file, feedName, streamTypeName, effectiveMs, attributeMap);
            } else {
                if (name.endsWith(FILE_SEPERATOR + StandardHeaderArguments.COMPRESSION_GZIP)) {
                    attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);
                }
                if (name.endsWith(FILE_SEPERATOR + GZ)) {
                    attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);
                }
                uploadStreamFile(feedName, streamTypeName, file, attributeMap);
            }
        });
    }

    private void uploadZipFile(final TaskContext taskContext,
                               final Path file,
                               final String feedName,
                               final String streamTypeName,
                               final Long effectiveMs,
                               final AttributeMap attributeMap) {
        StroomZipFile stroomZipFile = null;
        try {
            taskContext.info(() -> "Zip");

            stroomZipFile = new StroomZipFile(file);

            final List<List<String>> groupedFileLists = stroomZipFile.getStroomZipNameSet()
                    .getBaseNameGroupedList(AGGREGATION_DELIMITER);

            for (int i = 0; i < groupedFileLists.size(); i++) {
                final int pos = i;
                taskContext.info(() -> "Zip " + pos + "/" + groupedFileLists.size());

                uploadData(taskContext, stroomZipFile, feedName, streamTypeName, effectiveMs, attributeMap, groupedFileLists.get(i));

            }
        } catch (final RuntimeException | IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
            taskContext.info(() -> "done");
        }
    }

    private void uploadStreamFile(final String feedName,
                                  final String streamTypeName,
                                  final Path file,
                                  final AttributeMap attributeMap) {
        try {
            final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                    .buildSingleHandlerList(streamStore, feedProperties, metaStatistics, feedName, streamTypeName);
            final byte[] buffer = bufferFactory.create();
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, handlerList,
                    buffer, "Upload");
            try (final InputStream inputStream = Files.newInputStream(file)) {
                stroomStreamProcessor.process(inputStream, "Upload");
                stroomStreamProcessor.closeHandlers();
            }
        } catch (final RuntimeException | IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    private void uploadData(final TaskContext taskContext,
                            final StroomZipFile stroomZipFile,
                            final String feedName,
                            final String streamTypeName,
                            final Long effectiveMs,
                            final AttributeMap attributeMap,
                            final List<String> fileList) {
        final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor(taskContext,
                "Read");

        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(streamTypeName)
                .effectiveMs(effectiveMs)
                .build();

        Target targetRef = null;
        try (final Target target = streamStore.openTarget(metaProperties)) {
            targetRef = target;

            int count = 0;
            final int maxCount = fileList.size();
            for (final String inputBase : fileList) {
                count++;
                final int pos = count;
                taskContext.info(() -> pos + "/" + maxCount);
                try (final OutputStreamProvider outputStreamProvider = target.next()) {
                    streamContents(stroomZipFile, attributeMap, outputStreamProvider, inputBase, StroomZipFileType.Data,
                            streamProgressMonitor);
                    streamContents(stroomZipFile, attributeMap, outputStreamProvider, inputBase, StroomZipFileType.Meta,
                            streamProgressMonitor);
                    streamContents(stroomZipFile, attributeMap, outputStreamProvider, inputBase, StroomZipFileType.Context,
                            streamProgressMonitor);
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error("importData() - aborting import ", e);
            streamStore.deleteTarget(targetRef);
        }
    }

    private void streamContents(final StroomZipFile stroomZipFile,
                                final AttributeMap globalAttributeMap,
                                final OutputStreamProvider outputStreamProvider,
                                final String baseName,
                                final StroomZipFileType stroomZipFileType,
                                final StreamProgressMonitor streamProgressMonitor) throws IOException {
        try (final InputStream sourceStream = stroomZipFile.getInputStream(baseName, stroomZipFileType)) {
            // Quit if we have nothing to write
            if (sourceStream == null && !StroomZipFileType.Meta.equals(stroomZipFileType)) {
                return;
            }
            if (StroomZipFileType.Data.equals(stroomZipFileType)) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    streamToStream(sourceStream, outputStream, streamProgressMonitor);
                }
            }
            if (StroomZipFileType.Meta.equals(stroomZipFileType)) {
                final AttributeMap segmentAttributeMap = new AttributeMap();
                segmentAttributeMap.putAll(globalAttributeMap);
                if (sourceStream != null) {
                    AttributeMapUtil.read(sourceStream, segmentAttributeMap);
                }
                try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.META)) {
                    AttributeMapUtil.write(segmentAttributeMap, outputStream);
                }
            }
            if (StroomZipFileType.Context.equals(stroomZipFileType)) {
                try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    streamToStream(sourceStream, outputStream, streamProgressMonitor);
                }
            }
        }
    }

    private boolean streamToStream(final InputStream inputStream,
                                   final OutputStream outputStream,
                                   final StreamProgressMonitor streamProgressMonitor) throws IOException {
        final byte[] buffer = bufferFactory.create();
        int len;
        while ((len = StreamUtil.eagerRead(inputStream, buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            streamProgressMonitor.progress(len);
        }
        return false;
    }

}
