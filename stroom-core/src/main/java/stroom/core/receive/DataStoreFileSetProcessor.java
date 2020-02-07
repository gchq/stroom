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

package stroom.core.receive;

import stroom.data.store.api.Store;
import stroom.data.zip.StreamProgressMonitor;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.proxy.repo.ErrorFileUtil;
import stroom.proxy.repo.FileSet;
import stroom.proxy.repo.FileSetProcessor;
import stroom.proxy.repo.ProxyFileHandler;
import stroom.receive.common.StreamTargetStroomStreamHandler;
import stroom.task.api.TaskContext;
import stroom.util.io.BufferFactory;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class that reads a nested directory tree of stroom zip files.
 * <p>
 * so changes to the way files are stored in the zip repository
 * may have an impact on Stroom while it is using stroom.util.zip as opposed
 * to stroom-proxy-zip.  Need to pull all the zip repository stuff out
 * into its own repo with its own lifecycle and a clearly defined API,
 * then both stroom-proxy and stroom can use it.
 */
public final class DataStoreFileSetProcessor implements FileSetProcessor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataStoreFileSetProcessor.class);

    private final Store store;
    private final FeedProperties feedProperties;
    private final MetaStatistics metaStatistics;
    private final boolean aggregate = true;
    private final TaskContext taskContext;
    private final ProxyFileHandler proxyFileHandler;

    @Inject
    DataStoreFileSetProcessor(final Store store,
                              final FeedProperties feedProperties,
                              final MetaStatistics metaStatistics,
                              final TaskContext taskContext,
                              final BufferFactory bufferFactory) {
        this.store = store;
        this.feedProperties = feedProperties;
        this.metaStatistics = metaStatistics;
        this.taskContext = taskContext;

        proxyFileHandler = new ProxyFileHandler(bufferFactory);
    }

    @Override
    public void process(final FileSet fileSet) {
        if (fileSet.getFiles().size() > 0) {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final String feedName = fileSet.getFeed();
            taskContext.setName("Processing set - " + feedName);
            LOGGER.info(LambdaLogUtil.message("processFeedFiles() - Started {} ({} Files)", feedName, fileSet.getFiles().size()));

            // Sort the files in the file set so there is some consistency to processing.
            fileSet.getFiles().sort(Comparator.comparing(p -> p.getFileName().toString()));
            LOGGER.debug(LambdaLogUtil.message("process() - {} {}", feedName, fileSet.getFiles()));

            // We don't want to aggregate reference feeds.
            final boolean oneByOne = feedProperties.isReference(feedName) || !aggregate;

            List<StreamTargetStroomStreamHandler> handlers = openStreamHandlers(feedName);
            List<Path> deleteFileList = new ArrayList<>();

            long sequence = 1;
            long count = 0;

            final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor("ProxyAggregationTask");

            for (final Path file : fileSet.getFiles()) {
                count++;
                final long c = count;
                taskContext.info(() -> "File " + c + " of " + fileSet.getFiles().size());

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                try {
                    if (sequence > 1 && oneByOne) {
                        // Close off this unit
                        handlers = closeStreamHandlers(handlers);

                        // Delete the done files
                        cleanup(deleteFileList);

                        // Start new batch
                        deleteFileList = new ArrayList<>();
                        handlers = openStreamHandlers(feedName);
                        sequence = 1;
                    }

                    sequence = proxyFileHandler.processFeedFile(handlers, file, streamProgressMonitor, sequence);
                    deleteFileList.add(file);

                } catch (final IOException | RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    handlers = closeDeleteStreamHandlers(handlers);
                }
            }
            closeStreamHandlers(handlers);
            cleanup(deleteFileList);
            LOGGER.info(LambdaLogUtil.message("processFeedFiles() - Completed {} in {}", feedName, logExecutionTime));
        }
    }

    private List<StreamTargetStroomStreamHandler> openStreamHandlers(final String feedName) {
        // We don't want to aggregate reference feeds.
        final boolean oneByOne = feedProperties.isReference(feedName) || !aggregate;

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(store,
                feedProperties, metaStatistics, feedName, feedProperties.getStreamTypeName(feedName));

        streamTargetStroomStreamHandler.setOneByOne(oneByOne);

        final AttributeMap globalMetaMap = new AttributeMap();
        globalMetaMap.put(StandardHeaderArguments.FEED, feedName);

        streamTargetStroomStreamHandler.handleHeader(globalMetaMap);

        final List<StreamTargetStroomStreamHandler> list = new ArrayList<>();
        list.add(streamTargetStroomStreamHandler);

        return list;
    }

    private List<StreamTargetStroomStreamHandler> closeStreamHandlers(final List<StreamTargetStroomStreamHandler> handlers) {
        if (handlers != null) {
            handlers.forEach(StreamTargetStroomStreamHandler::close);
        }
        return null;
    }

    private List<StreamTargetStroomStreamHandler> closeDeleteStreamHandlers(
            final List<StreamTargetStroomStreamHandler> handlers) {
        if (handlers != null) {
            handlers.forEach(StreamTargetStroomStreamHandler::closeDelete);
        }
        return null;
    }

    private void cleanup(final List<Path> deleteList) {
        for (final Path file : deleteList) {
            ErrorFileUtil.deleteFileAndErrors(file);
        }

        // Delete any parent directories if we can.
        final Set<Path> parentDirs = deleteList.stream().map(Path::getParent).collect(Collectors.toSet());
        parentDirs.forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
            }
        });
    }
}
