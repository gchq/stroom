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

import com.google.common.collect.Maps;
import org.springframework.util.StringUtils;
import stroom.task.server.TaskContext;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomFileNameUtil;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomStreamHandler;
import stroom.util.zip.StroomZipEntry;
import stroom.util.zip.StroomZipFile;
import stroom.util.zip.StroomZipFileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
public abstract class StroomZipRepositoryProcessor {
    public final static int DEFAULT_MAX_AGGREGATION = 10000;
    public final static int DEFAULT_MAX_FILE_SCAN = 10000;
    private final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");
    private final StroomLogger LOGGER = StroomLogger.getLogger(StroomZipRepositoryProcessor.class);

    //used for checking if the task has been terminated
    private final TaskContext taskContext;

    //The executor used to execute the sub-tasks
    private final Executor executor;
    /**
     * The max number of parts to send in a zip file
     */
    private int maxAggregation = DEFAULT_MAX_AGGREGATION;
    /**
     * The max number of files to scan before giving up on this iteration
     */
    private int maxFileScan = DEFAULT_MAX_FILE_SCAN;
    /**
     * The max size of the stream before giving up on this iteration
     */
    private Long maxStreamSize = DEFAULT_MAX_STREAM_SIZE;

    public StroomZipRepositoryProcessor(final Executor executor, final TaskContext taskContext) {
        this.taskContext = taskContext;
        this.executor = executor;
    }

    public abstract void processFeedFiles(final StroomZipRepository stroomZipRepository,
                                          final String feed,
                                          final List<File> fileList);

    public abstract byte[] getReadBuffer();

    /**
     * Process a Stroom zip repository,
     *
     * @param stroomZipRepository The Stroom zip repository to process.
     * @return True is there are more files to process, i.e. we reached our max
     * file scan limit.
     */
    public boolean process(final StroomZipRepository stroomZipRepository) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("process() - Scanning " + stroomZipRepository.getRootDir());
        }
        // Scan all of the zip files in the repository so that we can map zip files to feeds.
        final Iterable<File> zipFiles = stroomZipRepository.getZipFiles();

        if (!zipFiles.iterator().hasNext()) {
            //no files so we are all done
            return true;
        }

        //build the map of feed -> files
        //only scan a limited number of files
        Map<String, List<File>> feedToFilesMap = StreamSupport.stream(zipFiles.spliterator(), true)
                .limit(maxFileScan)
                .filter(file -> !taskContext.isTerminated()) //do no more work if we are terminated
                .map(file -> fileScan(stroomZipRepository, file))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(
                                Entry::getValue,
                                Collectors.toList())));

        if (LOGGER.isDebugEnabled()) {
            int fileCount = feedToFilesMap.values().stream()
                    .mapToInt(List::size)
                    .sum();
            LOGGER.debug("Found %s feeds across %s files", feedToFilesMap.keySet().size(), fileCount);
        }

        final Comparator<File> fileComparator = (f1, f2) -> {
            if (f1 == null || f2 == null || f1.getName() == null || f2.getName() == null) {
                return 0;
            }
            return f1.getName().compareTo(f2.getName());
        };

        //spawn a task for each feed->files entry to load the data into a stream
        CompletableFuture[] processFeedFilesFutures = feedToFilesMap.entrySet().stream()
                .filter(entry -> !taskContext.isTerminated()) //do no more work if we are terminated
                .map(entry -> {
                    final String feedName = entry.getKey();
                    final List<File> fileList = new ArrayList<>(entry.getValue());

                    // Sort the map so the items are processed in order
                    fileList.sort(fileComparator);

                    //get the future for loading a list of files into a feed
                    return createProcessFeedFilesTask(stroomZipRepository, feedName, fileList);
                })
                .toArray(CompletableFuture[]::new);

        try {
            //wait for all the sub-tasks to complete
            CompletableFuture.allOf(processFeedFilesFutures).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Proxy aggregation thread interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(String.format("Error waiting for %s proxy aggregation jobs to complete",
                    processFeedFilesFutures.length), e);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("process() - Completed");
        }

        return false;
    }

    private CompletableFuture<Void> createProcessFeedFilesTask(final StroomZipRepository stroomZipRepository,
                                                               final String feed,
                                                               final List<File> fileList) {
        return CompletableFuture.runAsync(
                () -> {
                    if (!taskContext.isTerminated()) {
                        taskContext.setName("Process feed files");
                        taskContext.info(String.format("Processing %s files for feed %s", fileList.size(), feed));
                        processFeedFiles(stroomZipRepository, feed, fileList);
                    } else {
                        LOGGER.info("run() - Quit Feed Aggregation %s", feed);
                    }
                },
                executor);
    }

    /**
     * Peek at the stream to get the header file feed
     */
    private Optional<Entry<String, File>> fileScan(final StroomZipRepository stroomZipRepository, final File file) {

        //only a single thread is working on this file so we don't need any thread safety

        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            final Set<String> baseNameSet = stroomZipFile.getStroomZipNameSet().getBaseNameSet();

            if (baseNameSet.isEmpty()) {
                stroomZipRepository.addErrorMessage(stroomZipFile, "Unable to find any entry??", true);
                return Optional.empty();
            }

            final String anyBaseName = baseNameSet.iterator().next();

            final InputStream anyHeaderStream = stroomZipFile.getInputStream(anyBaseName, StroomZipFileType.Meta);

            if (anyHeaderStream == null) {
                stroomZipRepository.addErrorMessage(stroomZipFile, "Unable to find header??", true);
                return Optional.empty();
            }

            final HeaderMap headerMap = new HeaderMap();
            headerMap.read(anyHeaderStream, false);

            final String feed = headerMap.get(StroomHeaderArguments.FEED);

            if (!StringUtils.hasText(feed)) {
                stroomZipRepository.addErrorMessage(stroomZipFile, "Unable to find feed in header??", true);
                return Optional.empty();
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("fileScan() - " + file + " belongs to feed " + feed);
                }
            }

            return Optional.of(Maps.immutableEntry(feed, file));

        } catch (final IOException ex) {
            // Unable to open file ... must be bad.
            stroomZipRepository.addErrorMessage(stroomZipFile, ex.getMessage(), true);
            LOGGER.error("fileScan()", ex);
            return Optional.empty();

        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
        }
    }

    public Long processFeedFile(final List<? extends StroomStreamHandler> stroomStreamHandlerList,
                                final StroomZipRepository stroomZipRepository,
                                final File file,
                                final StreamProgressMonitor streamProgress,
                                final long startSequence) throws IOException {
        long entrySequence = startSequence;
        StroomZipFile stroomZipFile = null;
        boolean bad = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFile() - " + file);
        }

        try {
            stroomZipFile = new StroomZipFile(file);

            for (final String sourceName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
                bad = false;

                final String targetName = StroomFileNameUtil.getFilePathForId(entrySequence++);

                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Meta));
                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Context));
                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Data));
            }
        } catch (final IOException io) {
            stroomZipRepository.addErrorMessage(stroomZipFile, io.getMessage(), bad);
            throw io;
        } finally {
            CloseableUtil.close(stroomZipFile);
        }
        return entrySequence;
    }

    public void setMaxStreamSizeString(final String maxStreamSizeString) {
        this.maxStreamSize = ModelStringUtil.parseIECByteSizeString(maxStreamSizeString);
    }

    public Long getMaxStreamSize() {
        return maxStreamSize;
    }

    public void setMaxStreamSize(final Long maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
    }

    protected void sendEntry(final List<? extends StroomStreamHandler> requestHandlerList,
                             final StroomZipFile stroomZipFile,
                             final String sourceName,
                             final StreamProgressMonitor streamProgress,
                             final StroomZipEntry targetEntry) throws IOException {

        final InputStream inputStream = stroomZipFile.getInputStream(sourceName, targetEntry.getStroomZipFileType());
        sendEntry(requestHandlerList, inputStream, streamProgress, targetEntry);
    }

    public void sendEntry(final List<? extends StroomStreamHandler> stroomStreamHandlerList,
                          final InputStream inputStream,
                          final StreamProgressMonitor streamProgress,
                          final StroomZipEntry targetEntry) throws IOException {

        if (inputStream != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("sendEntry() - " + targetEntry);
            }
            final byte[] data = getReadBuffer();
            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                stroomStreamHandler.handleEntryStart(targetEntry);
            }
            int read;
            long totalRead = 0;
            while ((read = inputStream.read(data)) != -1) {
                totalRead += read;
                streamProgress.progress(read);
                for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                    stroomStreamHandler.handleEntryData(data, 0, read);
                }
            }
            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                stroomStreamHandler.handleEntryEnd();
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("sendEntry() - " + targetEntry + " " + ModelStringUtil.formatIECByteSizeString(totalRead));
            }
            if (totalRead == 0) {
                LOGGER.warn("sendEntry() - " + targetEntry + " IS BLANK");
            }
            LOGGER.debug("sendEntry() - %s size is %s", targetEntry, totalRead);

        }
    }

    protected void deleteFiles(final StroomZipRepository stroomZipRepository, final List<File> fileList) {
        for (final File file : fileList) {
            stroomZipRepository.delete(new StroomZipFile(file));
        }
    }

    public int getMaxAggregation() {
        return maxAggregation;
    }

    public void setMaxAggregation(final int maxAggregation) {
        this.maxAggregation = maxAggregation;
    }

    public int getMaxFileScan() {
        return maxFileScan;
    }

    public void setMaxFileScan(final int maxFileScan) {
        this.maxFileScan = maxFileScan;
    }
}
