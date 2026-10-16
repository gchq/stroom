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

package stroom.planb.impl.fs;

import stroom.planb.impl.PlanBPaths;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The local working area for building dated archive deltas: lends out a local directory to build them
 * in, then publishes each of them to its bucket on the shared store.
 *
 * <p>Knows nothing about what a delta holds. It owns two things a caller should not have to restate:
 * where the local directory lives and when it is removed, and what a failed push means for the shard.
 */
@Singleton
public class LocalArchive {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalArchive.class);

    private final SharedFileStorePublisher publisher;
    private final Path archiveLocalDir;

    @Inject
    public LocalArchive(final SharedFileStorePublisher publisher,
                         final PlanBPaths planBPaths) {
        this.publisher = publisher;
        this.archiveLocalDir = planBPaths.getArchiveLocalDir();
    }

    /**
     * Runs {@code work} against a fresh local directory, removing it afterwards.
     *
     * <p>Placed under the configured Plan B root rather than the JVM system temp dir: these are LMDB envs
     * holding a run's archived data, and system temp is often small or tmpfs (i.e. RAM). Cleanup failures
     * are logged rather than thrown, so they cannot mask a failure from the work itself.
     */
    public boolean withLocalDir(final MergeContext ctx,
                                  final LocalDirWork work) throws IOException {
        final Path localArchiveBase = Files.createDirectories(
                archiveLocalDir.resolve("delta_" + UUID.randomUUID()));
        try {
            return work.run(localArchiveBase);
        } finally {
            try {
                FileUtil.deleteDir(localArchiveBase);
            } catch (final Exception e) {
                LOGGER.warn("Failed to clean up local archive dir {} for {}: {}",
                        localArchiveBase, ctx.lockName(), e.getMessage());
            }
        }
    }

    /**
     * Pushes every dated delta dir staged under {@code localArchiveBase} to its bucket.
     *
     * <p>The staged entries have already been deleted, but only from the LOCAL holding shard — rethrowing
     * makes the merge skip {@code publisher.push} and discard that local shard, so the shared store keeps
     * its data and the next cycle retries. A partial success
     * (bucket A pushed, bucket B failed) re-pushes A next cycle, which is safe because
     * {@link SharedFileStorePublisher#pushArchive} merges rather than overwrites and each store type
     * recomputes its own derived state from what the bucket ends up holding.
     *
     * @return the number of buckets published, which is what callers must gate on — a record can stage a
     *         delta without any row having been removed from the live store.
     */
    public int pushAll(final MergeContext ctx,
                       final Path localArchiveBase) throws IOException {
        final List<StagedArchive> archiveShards;
        try (final Stream<Path> stream = Files.list(localArchiveBase)) {
            archiveShards = stream
                    .filter(Files::isDirectory)
                    .map(dir -> new StagedArchive(dir.getFileName().toString(), dir))
                    .toList();
        }
        if (archiveShards.isEmpty()) {
            return 0;
        }

        LOGGER.debug("Pushing {} date shard(s) for {}", archiveShards.size(), ctx.lockName());

        IOException firstFailure = null;
        for (final StagedArchive archiveShard : archiveShards) {
            try {
                publisher.pushArchive(ctx.doc(), ctx.shardIndex(), archiveShard);
                LOGGER.debug("Pushed archive shard {} for {}",
                        archiveShard.dateLabel(), ctx.lockName());
            } catch (final IOException e) {
                LOGGER.error("Failed to push archive shard {} for {} — the merged shard will not be " +
                             "published, so the shared store keeps this data and publishing will be " +
                             "retried on the next cycle.",
                        archiveShard.dateLabel(), ctx.lockName(), e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
        return archiveShards.size();
    }

    public interface LocalDirWork {

        boolean run(Path localArchiveBase) throws IOException;
    }
}
