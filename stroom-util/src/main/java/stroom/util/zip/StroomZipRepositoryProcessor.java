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

package stroom.util.zip;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import org.springframework.util.StringUtils;
import stroom.util.io.CloseableUtil;
import stroom.util.io.InitialByteArrayOutputStream;
import stroom.util.io.InitialByteArrayOutputStream.BufferPos;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.HasTerminate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.task.TaskScopeContextHolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
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
    public final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");
    private final StroomLogger LOGGER = StroomLogger.getLogger(StroomZipRepositoryProcessor.class);
    /**
     * Flag set to stop things
     */
//    private final Monitor monitor;

    //used for checking if the task has been terminated
    private final HasTerminate hasTerminate;

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

    public StroomZipRepositoryProcessor(
//            final Monitor monitor,
            final Executor executor,
            final HasTerminate hasTerminate) {

//        this.monitor = monitor;
        this.hasTerminate = hasTerminate;
        this.executor = executor;
    }

    public abstract void processFeedFiles(StroomZipRepository stroomZipRepository, String feed, List<File> fileList);

    public abstract byte[] getReadBuffer();

//    public abstract void startExecutor();

//    public abstract void stopExecutor(boolean now);

//    public abstract void waitForComplete();

//    public abstract void execute(String message, Runnable runnable);

    /**
     * Process a Stroom zip repository,
     *
     * @param stroomZipRepository The Stroom zip repository to process.
     * @return True is there are more files to process, i.e. we reached our max
     * file scan limit.
     */
    public boolean process(final StroomZipRepository stroomZipRepository) {
        boolean completedAllFiles = true;
        final Map<String, List<File>> feedToFileMap = new ConcurrentHashMap<>();

        TaskScopeContextHolder.addContext();
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - Scanning " + stroomZipRepository.getRootDir());
            }
            // Do the threaded work
//            startExecutor();

            // Scan all of the zip files in the repository so that we can map
            // zip files to feeds.
            final List<CompletableFuture<Map<String, List<File>>>> fileScanFutures = new ArrayList<>();

            final Iterable<File> zipFiles = stroomZipRepository.getZipFiles();

            Map<String, List<File>> feedToFilesMap = StreamSupport.stream(zipFiles.spliterator(), false)
                    .limit(maxFileScan)
                    .map(file -> CompletableFuture.supplyAsync(
                            createJobFileScan(stroomZipRepository, file),
                            executor))
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.groupingBy(
                            Map.Entry::getKey,
                            Collectors.mapping(
                                    Entry::getValue,
                                    Collectors.toList())));

//            int scanCount = 0;
//            for (final File file : zipFiles) {
//                if (file != null) {
//                    scanCount++;
//
//                    // Quit once we have hit the max
//                    if (scanCount > maxFileScan) {
//                        completedAllFiles = false;
//                        if (LOGGER.isDebugEnabled()) {
//                            LOGGER.debug("process() - Hit scan limit of " + maxFileScan);
//                        }
//                        break;
//                    }
//
//                    LOGGER.debug("Creating fileScanFuture for file", file);
//                    CompletableFuture<Map<String, File>> fileScanFuture = ;
//
//                    fileScanFutures.add(fileScanFuture);
////                    execute(file.getAbsolutePath(),
////                            createJobFileScan(stroomZipRepository, file, feedToFileMap));
//                }
//            }

            //wait for all the file scan jobs to complete
//            CompletableFuture
//                    .allOf(fileScanFutures.toArray(new CompletableFuture[fileScanFutures.size()]))
//                    .get();


//            waitForComplete();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - Scanned.  Found Feeds " + feedToFileMap.keySet());
            }

            List<CompletableFuture<Void>> loadZipFileFutures = feedToFileMap.entrySet().stream()
                    .map(entry -> {
                        final String feedName = entry.getKey();
                        final List<File> fileList = new ArrayList<>(entry.getValue());

                        // Sort the map so the items are processed in order
                        fileList.sort((f1, f2) -> {
                            if (f1 == null || f2 == null || f1.getName() == null || f2.getName() == null) {
                                return 0;
                            }

                            return f1.getName().compareTo(f2.getName());
                        });
                        return CompletableFuture.runAsync(
                                createJobProcessFeedFiles(stroomZipRepository, feedName, fileList));
                    })
                    .collect(Collectors.toList());

            //wait for all the files to be loaded
            CompletableFuture.allOf(loadZipFileFutures.toArray(new CompletableFuture[loadZipFileFutures.size()]));

            // Now set the batches together
//            final Iterator<Entry<String, List<File>>> iter = feedToFileMap.entrySet().iterator();
//            while (iter.hasNext() && !hasTerminate.isTerminated()) {
//                final Entry<String, List<File>> entry = iter.next();
//                final String feedName = entry.getKey();
//
//                //copy the items to a new list to avoid carrying any unwanted references
//                final List<File> fileList = new ArrayList<>(entry.getValue());
//
//                // Sort the map so the items are processed in order
//                fileList.sort((f1, f2) -> {
//                    if (f1 == null || f2 == null || f1.getName() == null || f2.getName() == null) {
//                        return 0;
//                    }
//
//                    return f1.getName().compareTo(f2.getName());
//                });
//
//                final StringBuilder msg = new StringBuilder()
//                        .append(feedName)
//                        .append(" ")
//                        .append(ModelStringUtil.formatCsv(fileList.size()))
//                        .append(" files (")
//                        .append(fileList.get(0))
//                        .append("...")
//                        .append(fileList.get(fileList.size() - 1))
//                        .append(")");
//
//
//                execute(msg.toString(), createJobProcessFeedFiles(stroomZipRepository, feedName, fileList));
//            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - Completed");
            }
        } finally {
//            TaskScopeContextHolder.removeContext();
//            stopExecutor(false);
        }

        return completedAllFiles;
    }

    private Supplier<Optional<Map.Entry<String, File>>> createJobFileScan(final StroomZipRepository stroomZipRepository,
                                                                          final File file) {
        return () -> {
            if (!hasTerminate.isTerminated()) {
                if (file != null) {
                    return fileScan(stroomZipRepository, file);
                } else {
                    return Optional.empty();
                }
            } else {
                LOGGER.info("run() - Quit File Scan %s", file);
                return Optional.empty();
            }
        };
    }

    private Runnable createJobProcessFeedFiles(final StroomZipRepository stroomZipRepository,
                                               final String feed,
                                               final List<File> fileList) {
        return () -> {
            if (!hasTerminate.isTerminated()) {
                processFeedFiles(stroomZipRepository, feed, fileList);
            } else {
                LOGGER.info("run() - Quit Feed Aggregation %s", feed);
            }
        };
    }

    /**
     * Peek at the stream to get the header file feed
     */
//    private Map<String, List<File>> fileScan(final StroomZipRepository stroomZipRepository,
    private Optional<Entry<String, File>> fileScan(final StroomZipRepository stroomZipRepository,
                                                   final File file) {

        //only a single thread is working on this file so we don't need any thread safety
//        Map<String, List<File>> feedToFileMap = new HashMap<>();
        ListMultimap<String, File> feedToFileMap = ArrayListMultimap.create();

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

            //add the file into the map, creating the list if needs be
//            feedToFileMap
//                    .computeIfAbsent(feed, k -> new ArrayList<>())
//                    .add(file);

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
                                final StroomZipRepository stroomZipRepository, final File file,
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

    protected void sendEntry(final List<? extends StroomStreamHandler> requestHandlerList, final StroomZipFile stroomZipFile,
                             final String sourceName, final StreamProgressMonitor streamProgress,
                             final StroomZipEntry targetEntry)
            throws IOException {
        final InputStream inputStream = stroomZipFile.getInputStream(sourceName, targetEntry.getStroomZipFileType());
        sendEntry(requestHandlerList, inputStream, streamProgress, targetEntry);
    }

    public void sendEntry(final List<? extends StroomStreamHandler> stroomStreamHandlerList, final InputStream inputStream,
                          final StreamProgressMonitor streamProgress, final StroomZipEntry targetEntry)
            throws IOException {
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

    public void sendEntry(final List<? extends StroomStreamHandler> stroomStreamHandlerList, final HeaderMap headerMap,
                          final StroomZipEntry targetEntry) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("sendEntry() - " + targetEntry);
        }
        final byte[] data = getReadBuffer();
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            if (stroomStreamHandler instanceof StroomHeaderStreamHandler) {
                ((StroomHeaderStreamHandler) stroomStreamHandler).handleHeader(headerMap);
            }
            stroomStreamHandler.handleEntryStart(targetEntry);
        }
        final InitialByteArrayOutputStream initialByteArrayOutputStream = new InitialByteArrayOutputStream(data);
        headerMap.write(initialByteArrayOutputStream, false);
        final BufferPos bufferPos = initialByteArrayOutputStream.getBufferPos();
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            stroomStreamHandler.handleEntryData(bufferPos.getBuffer(), 0, bufferPos.getBufferPos());
        }
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            stroomStreamHandler.handleEntryEnd();
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
