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

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.task.server.TaskContext;
import stroom.util.io.StreamProgressMonitor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class that reads a nested directory tree of stroom zip files.
 * <p>
 * <p>
 * TODO - This class is extended in ProxyAggregationExecutor in Stroom
 * so changes to the way files are stored in the zip repository
 * may have an impact on Stroom while it is using stroom.util.zip as opposed
 * to stroom-proxy-zip.  Need to pull all the zip repository stuff out
 * into its own repo with its own lifecycle and a clearly defined API,
 * then both stroom-proxy and stroom can use it.
 */
public final class FileSetProcessorImpl implements FileSetProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSetProcessorImpl.class);

    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final AtomicLong proxyForwardId = new AtomicLong(0);

    private final StreamHandlerFactory handlerFactory;
    private final TaskContext taskContext;
    private final ProxyFileHandler proxyFileHandler = new ProxyFileHandler();

    private volatile String hostName = null;

    public FileSetProcessorImpl(final StreamHandlerFactory handlerFactory,
                                final TaskContext taskContext) {
        this.handlerFactory = handlerFactory;
        this.taskContext = taskContext;
    }

    @Override
    public void process(final StroomZipRepository stroomZipRepository, final FileSet fileSet) {
        final String feed = fileSet.getFeed();
        final long thisPostId = proxyForwardId.incrementAndGet();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFiles() - proxyForwardId " + thisPostId + " " + feed + " file count "
                    + fileSet.getFiles().size());
        }

        final MetaMap metaMap = new MetaMap();
        metaMap.put(StroomHeaderArguments.FEED, feed);
        metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
        metaMap.put(StroomHeaderArguments.RECEIVED_PATH, getHostName());
        if (LOGGER.isDebugEnabled()) {
            metaMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final List<StreamHandler> handlers = handlerFactory.addSendHandlers(new ArrayList<>());

        try {
            // Start the post
            for (final StreamHandler streamHandler : handlers) {
                streamHandler.setMetaMap(metaMap);
                streamHandler.handleHeader();
            }

            long sequenceId = 1;
            final StreamProgressMonitor streamProgress = new StreamProgressMonitor("ProxyRepositoryReader " + feed);
            final List<Path> deleteList = new ArrayList<>();

            for (final Path file : fileSet.getFiles()) {
                // Send no more if told to finish
                if (taskContext.isTerminated()) {
                    LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                    break;
                }

                sequenceId = proxyFileHandler.processFeedFile(handlers, stroomZipRepository, file, streamProgress, sequenceId);

                deleteList.add(file);

            }
            for (final StreamHandler streamHandler : handlers) {
                streamHandler.handleFooter();
            }

            proxyFileHandler.deleteFiles(stroomZipRepository, deleteList);

        } catch (final IOException ex) {
            LOGGER.warn("processFeedFiles() - Failed to send to feed " + feed + " ( " + ex + ")");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("processFeedFiles() - Debug trace " + feed, ex);
            }
            for (final StreamHandler streamHandler : handlers) {
                try {
                    streamHandler.handleError();
                } catch (final IOException ioEx) {
                    LOGGER.error("fileSend()", ioEx);
                }
            }
        }
    }

    private String getHostName() {
        if (hostName == null) {
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (final Exception ex) {
                hostName = "Unknown";
            }
        }
        return hostName;
    }
}
