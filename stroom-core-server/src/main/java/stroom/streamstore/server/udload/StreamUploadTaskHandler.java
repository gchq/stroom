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

package stroom.streamstore.server.udload;

import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.FolderService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.statistic.server.MetaDataStatistic;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.NestedStreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamTypeService;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.io.StreamUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Monitor;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.MonitorImpl;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.*;
import stroom.util.zip.StroomStreamProcessor;
import org.springframework.context.annotation.Scope;

import javax.annotation.Resource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@TaskHandlerBean(task = StreamUploadTask.class)
@Scope(value = StroomScope.TASK)
public class StreamUploadTaskHandler extends AbstractTaskHandler<StreamUploadTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StreamUploadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String FILE_SEPERATOR = ".";
    private static final String GZ = "GZ";

    @Resource
    private TaskMonitor taskMonitor;
    @Resource
    private StreamStore streamStore;
    @Resource(name = "cachedStreamTypeService")
    private StreamTypeService streamTypeService;
    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "cachedFolderService")
    private FolderService folderService;
    // @Resource
    private MetaDataStatistic metaDataStatistics;

    @Resource(name = "prototypeThreadLocalBuffer")
    private ThreadLocalBuffer readThreadLocalBuffer;

    @Override
    public VoidResult exec(final StreamUploadTask task) {
        taskMonitor.info(task.getFile().toString());
        uploadData(task);
        return VoidResult.INSTANCE;
    }

    private void uploadData(final StreamUploadTask task) throws RuntimeException {
        final Monitor progressMonitor = new MonitorImpl(taskMonitor);

        final String name = task.getFileName().toUpperCase();

        final HeaderMap metaMap = new HeaderMap();
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
            uploadZipFile(task, progressMonitor, task, metaMap);
        } else {
            if (name.endsWith(FILE_SEPERATOR + StroomHeaderArguments.COMPRESSION_GZIP)) {
                metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_GZIP);
            }
            if (name.endsWith(FILE_SEPERATOR + GZ)) {
                metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_GZIP);
            }
            uploadStreamFile(task, progressMonitor, task, metaMap);
        }
    }

    private void uploadZipFile(final StreamUploadTask task, final Monitor progressMonitor,
            final StreamUploadTask streamUploadTask, final HeaderMap headerMap) {
        StroomZipFile stroomZipFile = null;
        try {
            progressMonitor.info("Zip");

            stroomZipFile = new StroomZipFile(streamUploadTask.getFile());

            final List<List<String>> groupedFileLists = stroomZipFile.getStroomZipNameSet()
                    .getBaseNameGroupedList(AGGREGATION_DELIMITER);

            for (int i = 0; i < groupedFileLists.size(); i++) {
                progressMonitor.info("Zip %s/%s", i, groupedFileLists.size());

                uploadData(stroomZipFile, streamUploadTask, headerMap, groupedFileLists.get(i));

            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        } finally {
            CloseableUtil.closeLogAndIngoreException(stroomZipFile);
            taskMonitor.info("done");
        }
    }

    private void uploadStreamFile(final StreamUploadTask task, final Monitor progressMonitor,
            final StreamUploadTask streamUploadTask, final HeaderMap headerMap) {
        try {
            final StreamType streamType = streamTypeService.loadByName(task.getStreamType().getName());
            final Feed feed = feedService.loadByUuid(task.getFeed().getUuid());
            final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                    .buildSingleHandlerList(streamStore, feedService, metaDataStatistics, feed, streamType);
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(headerMap, handlerList,
                    readThreadLocalBuffer.getBuffer(), "Upload");
            try (FileInputStream inputStream = new FileInputStream(streamUploadTask.getFile())) {
                stroomStreamProcessor.process(inputStream, "Upload");
                stroomStreamProcessor.closeHandlers();
            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void uploadData(final StroomZipFile stroomZipFile, final StreamUploadTask task, final HeaderMap metaMap,
                            final List<String> fileList) throws IOException {
        final Monitor zipPartTaskMonitor = new MonitorImpl(taskMonitor);
        final Monitor streamReadTaskMonitor = new MonitorImpl(taskMonitor);
        StreamTarget streamTarget = null;

        try {
            final Long effectiveMs = task.getEffectiveMs();
            final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor(streamReadTaskMonitor,
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
                zipPartTaskMonitor.info("%s/%s", count, maxCount);
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

    private void streamContents(final StroomZipFile stroomZipFile, final HeaderMap globalMetaMap,
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
            final HeaderMap segmentMetaMap = new HeaderMap();
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
        final byte[] buffer = readThreadLocalBuffer.getBuffer();
        int len;
        while ((len = StreamUtil.eagerRead(inputStream, buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            streamProgressMonitor.progress(len);
        }
        return false;
    }

}
