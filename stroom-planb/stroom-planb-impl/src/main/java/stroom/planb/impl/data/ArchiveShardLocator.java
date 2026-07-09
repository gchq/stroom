/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.data;

import stroom.planb.impl.PlanBConstants;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers archive shard directories on the shared file store that overlap
 * a given resolved time filter.
 */
@Singleton
public class ArchiveShardLocator {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ArchiveShardLocator.class);

    @Inject
    public ArchiveShardLocator() {
    }

    /**
     * Lists archive shard directories for the given shard index that:
     * <ol>
     *   <li>Have a {@code .complete} sentinel (are fully written and safe to read)</li>
     *   <li>Cover a time bucket that overlaps {@code [filterFromMs, filterToMs]}</li>
     * </ol>
     *
     * <p>Returns an empty list if no shared path is configured or no archive
     * directory exists yet.
     *
     * @param doc         the PlanB document whose archives to search
     * @param shardIndex  the shard index to look under
     * @param filterFromMs inclusive start of the query time filter (epoch ms)
     * @param filterToMs   inclusive end of the query time filter (epoch ms)
     * @return matching archive shard refs, sorted oldest-first by date label
     */
    public List<ArchiveShardRef> findRelevantShards(final PlanBDocument doc,
                                                    final int shardIndex,
                                                    final long filterFromMs,
                                                    final long filterToMs) {
        final String sharedPath = doc.getSharedPath();
        if (sharedPath == null) {
            return List.of();
        }

        final Path archiveBase = Path.of(sharedPath)
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(shardIndex));

        if (!Files.isDirectory(archiveBase)) {
            return List.of();
        }

        try (final Stream<Path> stream = Files.list(archiveBase)) {
            return stream
                    .filter(ArchiveShardLocator::isCompleteArchive)
                    .flatMap(dir -> toArchiveShardRef(dir, filterFromMs, filterToMs))
                    .sorted(Comparator.comparing(ArchiveShardRef::dateLabel))
                    .toList();
        } catch (final IOException e) {
            LOGGER.warn(() -> "Failed to list archive shards under " + archiveBase
                    + ": " + e.getMessage());
            return List.of();
        }
    }

    private static Stream<ArchiveShardRef> toArchiveShardRef(final Path dir,
                                                              final long filterFromMs,
                                                              final long filterToMs) {
        final String label = dir.getFileName().toString();
        // Skip temp directories left over from interrupted writes.
        if (label.startsWith(PlanBConstants.TMP_DIR_PREFIX)) {
            return Stream.empty();
        }
        final ArchivalGranularity granularity = ArchivalGranularityUtil.detect(label);
        if (granularity == null) {
            return Stream.empty();
        }
        final Instant end   = ArchivalGranularityUtil.bucketEnd(granularity, label);
        final Instant start = ArchivalGranularityUtil.bucketStart(granularity, label);
        if (end == null || start == null) {
            return Stream.empty();
        }
        if (!ArchivalGranularityUtil.overlaps(start, end, filterFromMs, filterToMs)) {
            return Stream.empty();
        }
        return Stream.of(new ArchiveShardRef(label, dir, granularity));
    }

    private static boolean isCompleteArchive(final Path dir) {
        return Files.isDirectory(dir)
                && Files.exists(dir.resolve(PlanBConstants.COMPLETE_FILE_NAME));
    }
}
