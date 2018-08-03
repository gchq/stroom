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
import stroom.data.meta.api.AttributeMap;
import stroom.datafeed.BufferFactory;
import stroom.feed.StroomHeaderArguments;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.data.store.StreamProgressMonitor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
public final class ProxyFileProcessorImpl implements ProxyFileProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFileProcessorImpl.class);

    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final AtomicLong proxyForwardId = new AtomicLong(0);

    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final StreamHandlerFactory handlerFactory;
    private final AtomicBoolean finish;
    private final ProxyFileHandler proxyFileHandler;

    private volatile String hostName = null;

    public ProxyFileProcessorImpl(final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
                                  final StreamHandlerFactory handlerFactory,
                                  final AtomicBoolean finish,
                                  final BufferFactory bufferFactory) {
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.handlerFactory = handlerFactory;
        this.finish = finish;
        proxyFileHandler = new ProxyFileHandler(bufferFactory);
    }

    /**
     * Send a load of files for the same feed
     */
    @Override
    public void processFeedFiles(final StroomZipRepository stroomZipRepository, final String feed,
                                 final List<Path> fileList) {
        final long thisPostId = proxyForwardId.incrementAndGet();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFiles() - proxyForwardId " + thisPostId + " " + feed + " file count "
                    + fileList.size());
        }

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StroomHeaderArguments.FEED, feed);
        attributeMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
        attributeMap.put(StroomHeaderArguments.RECEIVED_PATH, getHostName());
        if (LOGGER.isDebugEnabled()) {
            attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final List<StreamHandler> handlers = handlerFactory.addSendHandlers(new ArrayList<>());

        try {
            // Start the post
            for (final StreamHandler streamHandler : handlers) {
                streamHandler.setAttributeMap(attributeMap);
                streamHandler.handleHeader();
            }

            long sequenceId = 1;
            long batch = 1;

            final StreamProgressMonitor streamProgress = new StreamProgressMonitor("ProxyRepositoryReader " + feed);
            final List<Path> deleteList = new ArrayList<>();

            Long nextBatchBreak = proxyRepositoryReaderConfig.getMaxStreamSize();

            for (final Path file : fileList) {
                // Send no more if told to finish
                if (finish.get()) {
                    LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                    break;
                }
                if (sequenceId > proxyRepositoryReaderConfig.getMaxAggregation()
                        || (streamProgress.getTotalBytes() > nextBatchBreak)) {
                    batch++;
                    LOGGER.info("processFeedFiles() - Starting new batch {} as sequence {} > {} or size {} > {}", batch,
                            sequenceId, proxyRepositoryReaderConfig.getMaxAggregation(), streamProgress.getTotalBytes(), nextBatchBreak);

                    sequenceId = 1;
                    nextBatchBreak = streamProgress.getTotalBytes() + proxyRepositoryReaderConfig.getMaxStreamSize();

                    // Start a new batch
                    for (final StreamHandler streamHandler : handlers) {
                        streamHandler.handleFooter();
                    }
                    proxyFileHandler.deleteFiles(stroomZipRepository, deleteList);
                    deleteList.clear();

                    // Start the post
                    for (final StreamHandler streamHandler : handlers) {
                        streamHandler.setAttributeMap(attributeMap);
                        streamHandler.handleHeader();
                    }
                }

                sequenceId = proxyFileHandler.processFeedFile(handlers, stroomZipRepository, file, streamProgress, sequenceId);

                deleteList.add(file);

            }
            for (final StreamHandler streamHandler : handlers) {
                streamHandler.handleFooter();
            }

            proxyFileHandler.deleteFiles(stroomZipRepository, deleteList);

        } catch (final IOException ex) {
            LOGGER.warn("processFeedFiles() - Failed to send to feed " + feed + " ( " + String.valueOf(ex) + ")");
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
            } catch (final UnknownHostException e) {
                hostName = "Unknown";
            }
        }
        return hostName;
    }
}
