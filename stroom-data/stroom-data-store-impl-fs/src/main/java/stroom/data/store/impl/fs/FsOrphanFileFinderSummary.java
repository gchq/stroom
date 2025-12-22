/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.FsPathHelper.DecodedPath;
import stroom.util.io.FileUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class FsOrphanFileFinderSummary {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanFileFinderSummary.class);

    private final Map<SummaryLine, AtomicLong> summaryMap = new HashMap<>();
    private final Map<Path, String> badPaths = new HashMap<>();

    public void addPath(final Path path) {
        LOGGER.trace("addPath called for path {}", path);
        DecodedPath decodedPath;
        try {
            decodedPath = FsPathHelper.decodedPath(path);
        } catch (final Exception e) {
            LOGGER.debug("Unable to decode path {}", path, e);
            handleBadPath(path);
            decodedPath = null;
        }
        if (decodedPath != null) {
            final SummaryLine summaryLine = new SummaryLine(decodedPath);
            final long count = summaryMap
                    .computeIfAbsent(summaryLine, k -> new AtomicLong())
                    .incrementAndGet();
            LOGGER.trace("Incremented count to {} for summaryLine: {}", count, summaryLine);
        }
    }

    private void handleBadPath(final Path path) {
        if (path != null) {
            String message = "";
            // It is possible there are other paths that stroom can't parse so record them.
            if (Files.isDirectory(path)) {
                try {
                    final boolean isEmpty = FileUtil.isEmptyDirectory(path);
                    if (isEmpty) {
                        message = "Empty directory";
                    } else {
                        message = "Non-empty directory";
                    }
                } catch (final IOException e) {
                    LOGGER.debug("Error checking contents of {}", path, e);
                    message = "Directory with unknown contents" + ExceptionStringUtil.getMessage(e);
                }
            } else {
                message = "File";
            }
            badPaths.put(path, message);
        }
    }

    @Override
    public String toString() {
        final StringBuilder summary = new StringBuilder();
        if (summaryMap.size() > 0) {
            summary.append("Summary:\n");

            final List<Entry<SummaryLine, AtomicLong>> sortedEntries = summaryMap.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey(Comparator
                            .comparing(SummaryLine::getType)
                            .thenComparing(SummaryLine::getFeed)
                            .thenComparing(SummaryLine::getDate)))
                    .toList();

            summary.append("\n");
            summary.append(AsciiTable.builder(sortedEntries)
                    .withColumn(Column.of("Type", entry2 -> entry2.getKey().getType()))
                    .withColumn(Column.of("File/Directory", entry2 -> entry2.getKey().isDirectory
                            ? "Dir"
                            : "File"))
                    .withColumn(Column.of("Feed (if present)", entry2 -> entry2.getKey().getFeed()))
                    .withColumn(Column.of("Date", entry2 -> entry2.getKey().getDate()))
                    .withColumn(Column.integer("Orphan Count", entry2 -> entry2.getValue().get()))
                    .build());
            summary.append("\n");
        }

        if (!badPaths.isEmpty()) {
            if (!summaryMap.isEmpty()) {
                summary.append("\n");
            }
            summary.append("Invalid paths (can't be parsed to extract data/type/feed/id/etc.):\n");
            final List<Entry<Path, String>> sortedEntries = badPaths.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .toList();
            summary.append("\n");
            summary.append(AsciiTable.builder(sortedEntries)
                    .withColumn(Column.of("Path", Entry::getKey))
                    .withColumn(Column.of("Info", Entry::getValue))
                    .build());
            summary.append("\n");
        }
        return summary.toString();
    }


    // --------------------------------------------------------------------------------


    private static class SummaryLine {

        private final String type;
        private final String feed;
        private final LocalDate date;
        private final boolean isDirectory;

        public SummaryLine(final DecodedPath decodedPath) {
            this.type = decodedPath.getTypeName();
            this.feed = Objects.requireNonNullElse(decodedPath.getFeedName(), "");
            this.date = decodedPath.getDate();
            this.isDirectory = decodedPath.isDirectory();
        }

        public String getType() {
            return type;
        }

        public String getFeed() {
            return feed;
        }

        public LocalDate getDate() {
            return date;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SummaryLine that = (SummaryLine) o;
            return isDirectory == that.isDirectory && Objects.equals(type,
                    that.type) && Objects.equals(feed, that.feed) && Objects.equals(date, that.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, feed, date, isDirectory);
        }

        @Override
        public String toString() {
            return String.join(":", feed, type, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }
}
