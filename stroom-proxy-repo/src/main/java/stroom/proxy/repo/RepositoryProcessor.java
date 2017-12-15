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
import stroom.util.io.CloseableUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public abstract class RepositoryProcessor {
    public final static int DEFAULT_MAX_FILE_SCAN = 10000;
    private final Logger LOGGER = LoggerFactory.getLogger(RepositoryProcessor.class);

    private final static String FEED = "Feed";

    /**
     * Flag set to stop things
     */
    private final Monitor monitor;
    /**
     * The max number of files to scan before giving up on this iteration
     */
    private int maxFileScan = DEFAULT_MAX_FILE_SCAN;

    public RepositoryProcessor(final Monitor monitor) {
        this.monitor = monitor;
    }

    public abstract void processFeedFiles(StroomZipRepository stroomZipRepository, String feed, List<Path> fileList);

    public abstract void startExecutor();

    public abstract void stopExecutor(boolean now);

    public abstract void waitForComplete();

    public abstract void execute(String message, Runnable runnable);

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

            // Do the threaded work
            startExecutor();

            final Map<String, List<Path>> feedToFileMap = new ConcurrentHashMap<>();

            // Scan all of the zip files in the repository so that we can map
            // zip files to feeds.
            int count = 0;
            try (final Stream<Path> stream = stroomZipRepository.walkZipFiles().sorted(Comparator.naturalOrder())) {
                final Iterator<Path> iter = stream.iterator();
                while (iter.hasNext() && count < maxFileScan) {
                    final Path path = iter.next();
                    final Runnable runnable = () -> {
                        if (!monitor.isTerminated()) {
                            LOGGER.debug("Processing file: {}", path);
                            final String feed = getFeed(stroomZipRepository, path);
                            if (feed == null || feed.length() == 0) {
                                addErrorMessage(stroomZipRepository, path, "Unable to find feed in header??", true);

                            } else {
                                LOGGER.debug("{} belongs to feed {}", path, feed);
                                // Add the file into the map, creating the list if needs be
                                feedToFileMap.computeIfAbsent(feed, k -> Collections.synchronizedList(new ArrayList<>())).add(path);
                            }
                        } else {
                            LOGGER.info("Quit processing at: {}", path);
                        }
                    };

                    execute(path.toAbsolutePath().toString(), runnable);
                    count++;
                }

                if (count > maxFileScan) {
                    completedAllFiles = false;
                    LOGGER.debug("Hit scan limit of {}", maxFileScan);
                }
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Wait for all of the feed name extraction tasks to complete.
            waitForComplete();

            LOGGER.debug("Found Feeds {}", feedToFileMap.keySet());

            // Now set the batches together
            final Iterator<Entry<String, List<Path>>> iter = feedToFileMap.entrySet().iterator();
            while (iter.hasNext() && !monitor.isTerminated()) {
                final Entry<String, List<Path>> entry = iter.next();
                final String feedName = entry.getKey();
                final List<Path> fileList = entry.getValue();

                synchronized (fileList) {
                    // Sort the map so the items are processed in order
                    fileList.sort((Comparator.comparing(path -> path.getFileName().toString())));

                    final String msg = "" +
                            feedName +
                            " " +
                            ModelStringUtil.formatCsv(fileList.size()) +
                            " files (" +
                            fileList.get(0) +
                            "..." +
                            fileList.get(fileList.size() - 1) +
                            ")";

                    execute(msg, createJobProcessFeedFiles(stroomZipRepository, feedName, fileList));
                }
            }

            LOGGER.debug("Completed");
        } finally {
            stopExecutor(false);
        }

        return completedAllFiles;
    }

    private Runnable createJobProcessFeedFiles(final StroomZipRepository stroomZipRepository, final String feed,
                                               final List<Path> fileList) {
        return () -> {
            if (!monitor.isTerminated()) {
                processFeedFiles(stroomZipRepository, feed, fileList);
            } else {
                LOGGER.info("Quit Feed Aggregation {}", feed);
            }
        };
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
