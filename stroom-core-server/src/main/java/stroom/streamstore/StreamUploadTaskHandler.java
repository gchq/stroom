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
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.feed.FeedService;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.Feed;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.streamstore.fs.serializable.NestedStreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamTargetStroomStreamHandler;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskContext;
import stroom.task.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Monitor;
import stroom.util.shared.VoidResult;
import stroom.util.task.MonitorImpl;
import stroom.util.thread.BufferFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

@TaskHandlerBean(task = StreamUploadTask.class)
class StreamUploadTaskHandler extends AbstractTaskHandler<StreamUploadTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamUploadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String FILE_SEPERATOR = ".";
    private static final String GZ = "GZ";

    private final TaskContext taskContext;
    private final StreamStore streamStore;
    private final StreamTypeService streamTypeService;
    private final FeedService feedService;
    private final MetaDataStatistic metaDataStatistics;

    @Inject
    StreamUploadTaskHandler(final TaskContext taskContext,
                            final StreamStore streamStore,
                            @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                            @Named("cachedFeedService") final FeedService feedService,
                            final MetaDataStatistic metaDataStatistics) {
        this.taskContext = taskContext;
        this.streamStore = streamStore;
        this.streamTypeService = streamTypeService;
        this.feedService = feedService;
        this.metaDataStatistics = metaDataStatistics;
    }

    @Override
    public VoidResult exec(final StreamUploadTask task) {
        taskContext.info(task.getFile().toString());
        uploadData(task);
        return VoidResult.INSTANCE;
    }

    private void uploadData(final StreamUploadTask task) throws RuntimeException {
        if (task.getFeed() == null) {
            throw new EntityServiceException("Feed not set!");
        }
        if (task.getStreamType() == null) {
            throw new EntityServiceException("Stream Type not set!");
        }
        if (task.getFileName() == null) {
            throw new EntityServiceException("File not set!");
        }

        final String name = task.getFileName().toUpperCase();

        final MetaMap metaMap = new MetaMap();
        if (task.getMetaData() != null && task.getMetaData().trim().length() > 0) {
            try {
                metaMap.read(task.getMetaData().getBytes(StreamUtil.DEFAULT_CHARSET));
            } catch (final IOException e) {
                LOGGER.error("uploadData()", e);
            }
        }

        if (task.getEffectiveMs() != null) {
            metaMap.put(StroomHeaderArguments.EFFECTIVE_TIME, DateUtil.createNormalDateTimeString(task.getEffectiveMs()));
        }
        metaMap.put(StroomHeaderArguments.REMOTE_FILE, task.getFileName());
        metaMap.put(StroomHeaderArguments.FEED, task.getFeed().getName());
        metaMap.put(StroomHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString(System.currentTimeMillis()));
        metaMap.put(StroomHeaderArguments.USER_AGENT, "STROOM-UI");

        if (name.endsWith(FILE_SEPERATOR + StroomHeaderArguments.COMPRESSION_ZIP)) {
            metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
            uploadZipFile(taskContext, task, metaMap);
        } else {
            if (name.endsWith(FILE_SEPERATOR + StroomHeaderArguments.COMPRESSION_GZIP)) {
                metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_GZIP);
            }
            if (name.endsWith(FILE_SEPERATOR + GZ)) {
                metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_GZIP);
            }
            uploadStreamFile(task, task, metaMap);
        }
    }

    private void uploadZipFile(final TaskContext taskContext,
                               final StreamUploadTask streamUploadTask,
                               final MetaMap metaMap) {
        StroomZipFile stroomZipFile = null;
        try {
            taskContext.info("Zip");

            stroomZipFile = new StroomZipFile(streamUploadTask.getFile());

            final List<List<String>> groupedFileLists = stroomZipFile.getStroomZipNameSet()
                    .getBaseNameGroupedList(AGGREGATION_DELIMITER);

            for (int i = 0; i < groupedFileLists.size(); i++) {
                taskContext.info("Zip {}/{}", i, groupedFileLists.size());

                uploadData(stroomZipFile, streamUploadTask, metaMap, groupedFileLists.get(i));

            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
            taskContext.info("done");
        }
    }

    private void uploadStreamFile(final StreamUploadTask task,
                                  final StreamUploadTask streamUploadTask, final MetaMap metaMap) {
        try {
            final StreamType streamType = streamTypeService.loadByName(task.getStreamType().getName());
            final Feed feed = feedService.loadByUuid(task.getFeed().getUuid());
            final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                    .buildSingleHandlerList(streamStore, feedService, metaDataStatistics, feed, streamType);
            final byte[] buffer = BufferFactory.create();
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlerList,
                    buffer, "Upload");
            try (final InputStream inputStream = Files.newInputStream(streamUploadTask.getFile())) {
                stroomStreamProcessor.process(inputStream, "Upload");
                stroomStreamProcessor.closeHandlers();
            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void uploadData(final StroomZipFile stroomZipFile, final StreamUploadTask task, final MetaMap metaMap,
                            final List<String> fileList) throws IOException {
        StreamTarget streamTarget = null;

        try {
            final Long effectiveMs = task.getEffectiveMs();
            final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor(taskContext,
                    "Read");

            final StreamType streamType = streamTypeService.loadByName(task.getStreamType().getName());
            final Feed feed = feedService.loadByUuid(task.getFeed().getUuid());
            final Stream stream = Stream.createStream(streamType, feed, effectiveMs);

            streamTarget = streamStore.openStreamTarget(stream);

            final NestedStreamTarget rawNestedStreamTarget = new NestedStreamTarget(streamTarget, true);

            int count = 0;
            final int maxCount = fileList.size();
            for (final String inputBase : fileList) {
                count++;
                taskContext.info("{}/{}", count, maxCount);
                streamContents(stroomZipFile, metaMap, rawNestedStreamTarget, inputBase, StroomZipFileType.Data,
                        streamProgressMonitor);
                streamContents(stroomZipFile, metaMap, rawNestedStreamTarget, inputBase, StroomZipFileType.Meta,
                        streamProgressMonitor);
                streamContents(stroomZipFile, metaMap, rawNestedStreamTarget, inputBase, StroomZipFileType.Context,
                        streamProgressMonitor);
            }

            rawNestedStreamTarget.close();
            streamStore.closeStreamTarget(streamTarget);

        } catch (final Exception ex) {
            LOGGER.error("importData() - aborting import ", ex);
            streamStore.deleteStreamTarget(streamTarget);
        }
    }

    private void streamContents(final StroomZipFile stroomZipFile, final MetaMap globalMetaMap,
                                final NestedStreamTarget nestedStreamTarget, final String baseName, final StroomZipFileType stroomZipFileType,
                                final StreamProgressMonitor streamProgressMonitor) throws IOException {
        final InputStream sourceStream = stroomZipFile.getInputStream(baseName, stroomZipFileType);
        // Quit if we have nothing to write
        if (sourceStream == null && !StroomZipFileType.Meta.equals(stroomZipFileType)) {
            return;
        }
        if (StroomZipFileType.Data.equals(stroomZipFileType)) {
            nestedStreamTarget.putNextEntry();
            streamToStream(sourceStream, nestedStreamTarget.getOutputStream(), streamProgressMonitor);
            nestedStreamTarget.closeEntry();
        }
        if (StroomZipFileType.Meta.equals(stroomZipFileType)) {
            final MetaMap segmentMetaMap = new MetaMap();
            segmentMetaMap.putAll(globalMetaMap);
            if (sourceStream != null) {
                segmentMetaMap.read(sourceStream, false);
            }
            nestedStreamTarget.putNextEntry(StreamType.META);
            segmentMetaMap.write(nestedStreamTarget.getOutputStream(StreamType.META), false);
            nestedStreamTarget.closeEntry(StreamType.META);
        }
        if (StroomZipFileType.Context.equals(stroomZipFileType)) {
            nestedStreamTarget.putNextEntry(StreamType.CONTEXT);
            streamToStream(sourceStream, nestedStreamTarget.getOutputStream(StreamType.CONTEXT), streamProgressMonitor);
            nestedStreamTarget.closeEntry(StreamType.CONTEXT);
        }
        if (sourceStream != null) {
            sourceStream.close();
        }
    }

    private boolean streamToStream(final InputStream inputStream, final OutputStream outputStream,
                                   final StreamProgressMonitor streamProgressMonitor) throws IOException {
        final byte[] buffer = BufferFactory.create();
        int len;
        while ((len = StreamUtil.eagerRead(inputStream, buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            streamProgressMonitor.progress(len);
        }
        return false;
    }

}
