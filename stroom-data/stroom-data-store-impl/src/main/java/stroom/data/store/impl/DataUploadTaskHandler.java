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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

public class DataUploadTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataUploadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String FILE_SEPARATOR = ".";
    private static final String GZ = "GZ";

    private final TaskContextFactory taskContextFactory;
    private final Store streamStore;
    private final SecurityContext securityContext;
    private final StreamTargetStreamHandlers streamHandlers;

    @Inject
    DataUploadTaskHandler(final TaskContextFactory taskContextFactory,
                          final Store streamStore,
                          final SecurityContext securityContext,
                          final StreamTargetStreamHandlers streamHandlers) {
        this.taskContextFactory = taskContextFactory;
        this.streamStore = streamStore;
        this.securityContext = securityContext;
        this.streamHandlers = streamHandlers;
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
                            final String typeName,
                            final Long effectiveMs,
                            final String metaData) {
        securityContext.secure(() -> {
            taskContext.info(file::toString);
            if (feedName == null) {
                throw new EntityServiceException("Feed not set!");
            }
            if (typeName == null) {
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
                attributeMap.put(StandardHeaderArguments.EFFECTIVE_TIME,
                        DateUtil.createNormalDateTimeString(effectiveMs));
            }
            attributeMap.put(StandardHeaderArguments.REMOTE_FILE, fileName);
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            attributeMap.put(StandardHeaderArguments.TYPE, typeName);
            attributeMap.put(StandardHeaderArguments.RECEIVED_TIME,
                    DateUtil.createNormalDateTimeString(System.currentTimeMillis()));
            attributeMap.put(StandardHeaderArguments.USER_AGENT, "STROOM-UI");

            if (name.endsWith(FILE_SEPARATOR + StandardHeaderArguments.COMPRESSION_ZIP)) {
                attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
                uploadZipFile(taskContext, file, feedName, typeName, effectiveMs, attributeMap);
            } else {
                if (name.endsWith(FILE_SEPARATOR + StandardHeaderArguments.COMPRESSION_GZIP)) {
                    attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);
                }
                if (name.endsWith(FILE_SEPARATOR + GZ)) {
                    attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);
                }
                uploadStreamFile(file, feedName, typeName, attributeMap);
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

                uploadData(taskContext,
                        stroomZipFile,
                        feedName,
                        streamTypeName,
                        effectiveMs,
                        attributeMap,
                        groupedFileLists.get(i));

            }
        } catch (final RuntimeException | IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
            taskContext.info(() -> "done");
        }
    }

    private void uploadStreamFile(final Path file,
                                  final String feedName,
                                  final String typeName,
                                  final AttributeMap attributeMap) {
        try (final InputStream inputStream = Files.newInputStream(file)) {
            streamHandlers.handle(feedName, typeName, attributeMap, handler -> {
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap,
                        handler);
                stroomStreamProcessor.process(inputStream, "Upload");
            });
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

        final MetaProperties metaProperties = MetaProperties.builder()
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
                    streamContents(stroomZipFile, attributeMap, outputStreamProvider, inputBase, StroomZipFileType.DATA,
                            streamProgressMonitor);
                    streamContents(stroomZipFile, attributeMap, outputStreamProvider, inputBase, StroomZipFileType.META,
                            streamProgressMonitor);
                    streamContents(stroomZipFile,
                            attributeMap,
                            outputStreamProvider,
                            inputBase,
                            StroomZipFileType.CONTEXT,
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
            if (sourceStream == null && !StroomZipFileType.META.equals(stroomZipFileType)) {
                return;
            }
            if (StroomZipFileType.DATA.equals(stroomZipFileType)) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    streamToStream(sourceStream, outputStream, streamProgressMonitor);
                }
            }
            if (StroomZipFileType.META.equals(stroomZipFileType)) {
                final AttributeMap segmentAttributeMap = new AttributeMap();
                segmentAttributeMap.putAll(globalAttributeMap);
                if (sourceStream != null) {
                    AttributeMapUtil.read(sourceStream, segmentAttributeMap);
                }
                try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.META)) {
                    AttributeMapUtil.write(segmentAttributeMap, outputStream);
                }
            }
            if (StroomZipFileType.CONTEXT.equals(stroomZipFileType)) {
                try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    streamToStream(sourceStream, outputStream, streamProgressMonitor);
                }
            }
        }
    }

    private void streamToStream(final InputStream inputStream,
                                final OutputStream outputStream,
                                final StreamProgressMonitor streamProgressMonitor) {
        StreamUtil.streamToStream(
                inputStream,
                outputStream,
                new byte[StreamUtil.BUFFER_SIZE],
                streamProgressMonitor::progress);
    }
}
