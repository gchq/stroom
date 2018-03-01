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
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.CloseableUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public final class RepositoryProcessor {
    public final static int DEFAULT_MAX_FILE_SCAN = 10000;
    private final Logger LOGGER = LoggerFactory.getLogger(RepositoryProcessor.class);

    private final static String FEED = "Feed";

    private final ProxyFileProcessor feedFileProcessor;

    private final Executor executor;

    /**
     * Flag set to stop things
     */
    private final Monitor monitor;


    /**
     * The max number of files to scan before giving up on this iteration
     */
    private int maxFileScan = DEFAULT_MAX_FILE_SCAN;

    public RepositoryProcessor(final ProxyFileProcessor feedFileProcessor, final Executor executor, final Monitor monitor) {
        this.feedFileProcessor = feedFileProcessor;
        this.executor = executor;
        this.monitor = monitor;
    }

//    public abstract void processFeedFiles(StroomZipRepository stroomZipRepository, String feed, List<Path> fileList);
//
//    public abstract void startExecutor();
//
//    public abstract void stopExecutor(boolean now);
//
//    public abstract void waitForComplete();
//
//    public abstract void execute(String message, Runnable runnable);

//    public FeedPathMap createFeedPathMap(final StroomZipRepository stroomZipRepository) {
//        final Map<String, List<Path>> feedToFileMap = new ConcurrentHashMap<>();
//
//        // Scan all of the zip files in the repository so that we can map
//        // zip files to feeds.
//        final AtomicInteger count = new AtomicInteger();
//        findFeeds(stroomZipRepository.getRootDir(), count, stroomZipRepository, feedToFileMap);
//
//        if (count.get() >= maxFileScan) {
//            completedAllFiles = false;
//            LOGGER.debug("Hit scan limit of {}", maxFileScan);
//        }
//
//        return new F
//    }

    /**
     * Process a Stroom zip repository,
     *
     * @param stroomZipRepository The Stroom zip repository to process.
     * @return True is there are more files to process, i.e. we reached our max
     * file scan limit.
     */
    public final boolean process(final StroomZipRepository stroomZipRepository) {
        boolean completedAllFiles = true;

        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - Scanning " + stroomZipRepository.getRootDir());
            }

            // Scan all of the zip files in the repository so that we can map zip files to feeds.
            final FeedPathMap feedPathMap = createFeedPathMap(stroomZipRepository);
            completedAllFiles = feedPathMap.isCompletedAllFiles();

            processFeeds(stroomZipRepository, feedPathMap);

            LOGGER.debug("Completed");
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return completedAllFiles;
    }

    private void processFeeds(final StroomZipRepository stroomZipRepository, final FeedPathMap feedPathMap) {
        final Set<CompletableFuture> futures = new HashSet<>();

        final Iterator<Entry<String, List<Path>>> iter = feedPathMap.getMap().entrySet().iterator();
        while (iter.hasNext() && !monitor.isTerminated()) {
            final Entry<String, List<Path>> entry = iter.next();
            final String feedName = entry.getKey();
            final List<Path> fileList = entry.getValue();

            final String msg = "" +
                    feedName +
                    " " +
                    ModelStringUtil.formatCsv(fileList.size()) +
                    " files (" +
                    fileList.get(0) +
                    "..." +
                    fileList.get(fileList.size() - 1) +
                    ")";

            final Runnable runnable = () -> {
                if (!monitor.isTerminated()) {
                    monitor.info(msg);
                    feedFileProcessor.processFeedFiles(stroomZipRepository, feedName, fileList);
                } else {
                    LOGGER.info("Quit Feed Aggregation {}", feedName);
                }
            };

            futures.add(CompletableFuture.runAsync(runnable, executor));
        }

        // Wait for all processes to complete.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    }

    private FeedPathMap createFeedPathMap(final StroomZipRepository stroomZipRepository) {
        final Map<String, List<Path>> map = new ConcurrentHashMap<>();

        // Scan all of the zip files in the repository so that we can map
        // zip files to feeds.
        final Set<CompletableFuture> futures = new HashSet<>();
        final boolean completedAllFiles = findFeeds(stroomZipRepository.getRootDir(), stroomZipRepository, map, futures);

        if (!completedAllFiles) {
            LOGGER.debug("Hit scan limit of {}", maxFileScan);
        }

        // Wait for all of the feed name extraction tasks to complete.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();

        LOGGER.debug("Found Feeds {}", map.keySet());

        return new FeedPathMap(completedAllFiles, map);
    }

    private boolean findFeeds(final Path dir, final StroomZipRepository stroomZipRepository, final Map<String, List<Path>> feedPaths, final Set<CompletableFuture> futures) {
        LogExecutionTime logExecutionTime = new LogExecutionTime();
        final List<Path> zipFiles = listPaths(dir);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Listed zip files in {}", logExecutionTime.toString());
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(zipFiles.toString());
        }

        final Iterator<Path> iterator = zipFiles.iterator();
        int count = 0;
        while (iterator.hasNext() && count < maxFileScan) {
            final Path path = iterator.next();
            processPath(path, stroomZipRepository, feedPaths, futures);
            count++;
        }

        // Did we complete all?
        return count < maxFileScan;
    }

    private void processPath(final Path path, final StroomZipRepository stroomZipRepository, final Map<String, List<Path>> feedPaths, final Set<CompletableFuture> futures) {
        final Runnable runnable = () -> {
            if (!monitor.isTerminated()) {
                LOGGER.debug("Processing file: {}", path);
                final String feed = getFeed(stroomZipRepository, path);
                if (feed == null || feed.length() == 0) {
                    addErrorMessage(stroomZipRepository, path, "Unable to find feed in header??", true);

                } else {
                    LOGGER.debug("{} belongs to feed {}", path, feed);
                    // Add the file into the map, creating the list if needs be
                    feedPaths.computeIfAbsent(feed, k -> Collections.synchronizedList(new ArrayList<>())).add(path);
                }
            } else {
                LOGGER.info("Quit processing at: {}", path);
            }
        };

        futures.add(CompletableFuture.runAsync(runnable, executor));
    }

    private List<Path> listPaths(final Path dir) {
        final List<Path> zipFiles = new ArrayList<>();
        try {
            if (dir != null && Files.isDirectory(dir)) {
                try {
                    Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                if (file.toString().endsWith(StroomZipRepository.ZIP_EXTENSION)) {
                                    zipFiles.add(file);
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }

                            if (zipFiles.size() < maxFileScan) {
                                return FileVisitResult.CONTINUE;
                            }

                            return FileVisitResult.TERMINATE;
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        // TODO : DO WE WANT TO SORT THESE FILES?
        zipFiles.sort(Comparator.naturalOrder());

        return zipFiles;
    }

    private String getFeed(final StroomZipRepository stroomZipRepository, final Path path) {
        final MetaMap metaMap = getMetaMap(stroomZipRepository, path);
        return metaMap.get(FEED);
    }

    private MetaMap getMetaMap(final StroomZipRepository stroomZipRepository, final Path path) {
        final MetaMap metaMap = new MetaMap();
        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(path);
            final Set<String> baseNameSet = stroomZipFile.getStroomZipNameSet().getBaseNameSet();
            if (baseNameSet.isEmpty()) {
                stroomZipRepository.addErrorMessage(stroomZipFile, "Unable to find any entry??", true);
            } else {
                final String anyBaseName = baseNameSet.iterator().next();
                final InputStream anyHeaderStream = stroomZipFile.getInputStream(anyBaseName, StroomZipFileType.Meta);

                if (anyHeaderStream == null) {
                    stroomZipRepository.addErrorMessage(stroomZipFile, "Unable to find header??", true);
                } else {
                    metaMap.read(anyHeaderStream, false);
                }
            }
        } catch (final IOException ex) {
            // Unable to open file ... must be bad.
            stroomZipRepository.addErrorMessage(stroomZipFile, ex.getMessage(), true);
            LOGGER.error("getMetaMap", ex);

        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
        }

        return metaMap;
    }

    private void addErrorMessage(final StroomZipRepository stroomZipRepository, final Path path, final String msg, final boolean bad) {
        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(path);
            stroomZipRepository.addErrorMessage(stroomZipFile, msg, bad);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
        }
    }

    public int getMaxFileScan() {
        return maxFileScan;
    }

    public void setMaxFileScan(final int maxFileScan) {
        this.maxFileScan = maxFileScan;
    }
}
