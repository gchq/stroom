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
import stroom.data.zip.StreamProgressMonitor;
import stroom.meta.shared.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.io.BufferFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
public final class ProxyForwardingFileSetProcessor implements FileSetProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyForwardingFileSetProcessor.class);

    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final AtomicLong proxyForwardId = new AtomicLong(0);

    private final StreamHandlerFactory handlerFactory;
    private final ProxyFileHandler proxyFileHandler;

    private volatile String hostName = null;

    ProxyForwardingFileSetProcessor(final StreamHandlerFactory handlerFactory, final BufferFactory bufferFactory) {
        this.handlerFactory = handlerFactory;
        proxyFileHandler = new ProxyFileHandler(bufferFactory);
    }

    @Override
    public void process(final FileSet fileSet) {
        if (fileSet.getFiles().size() > 0) {
            final String feedName = fileSet.getFeed();
            final long thisPostId = proxyForwardId.incrementAndGet();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("processFeedFiles() - proxyForwardId " + thisPostId + " " + feedName + " file count "
                        + fileSet.getFiles().size());
            }

            // Sort the files in the file set so there is some consistency to processing.
            fileSet.getFiles().sort(Comparator.comparing(p -> p.getFileName().toString()));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - " + feedName + " " + fileSet.getFiles());
            }

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
            attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
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
                final StreamProgressMonitor streamProgress = new StreamProgressMonitor("ProxyRepositoryReader " + feedName);

                for (final Path file : fileSet.getFiles()) {
                    // Send no more if told to finish
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                        break;
                    }

                    sequenceId = proxyFileHandler.processFeedFile(handlers, file, streamProgress, sequenceId);
                }

                for (final StreamHandler streamHandler : handlers) {
                    streamHandler.handleFooter();
                }

                // Delete all of the files we have processed and their parent directories if possible.
                cleanup(fileSet.getFiles());

            } catch (final IOException ex) {
                LOGGER.warn("processFeedFiles() - Failed to send to feed " + feedName + " ( " + ex + ")");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("processFeedFiles() - Debug trace " + feedName, ex);
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
                LOGGER.debug(e.getMessage(), e);
            }
        });
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
