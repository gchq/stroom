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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.data.shard.AbstractStoreShard;
import stroom.planb.shared.HasHoldingAreaSettings;
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * A throwaway local copy of one shared shard, used to merge a cycle's batches into it and publish the
 * result. Created inside the per-shard cluster lock, disposed at the end of the cycle, and its directory
 * deleted with it.
 *
 * <p>It copies the shared {@code data.mdb} once, at construction, and never looks at it again. There is no
 * version check because the directory is created fresh each cycle, so there is never a local copy that
 * could already be current, and no protection against a concurrent republish because the cluster lock
 * makes this instance the only writer. Contrast {@link ArchiveStoreShard}, which is read by query
 * threads holding no lock and therefore re-checks its bucket's version on every access.
 *
 * <p>Opening is deferred until the copy is in place, so the environment is opened once rather than being
 * created empty and immediately replaced.
 */
public class MergeShard extends AbstractStoreShard {

    public MergeShard(final ByteBuffers byteBuffers,
                      final ByteBufferFactory byteBufferFactory,
                      final Provider<PlanBConfig> configProvider,
                      final PlanBDocument doc,
                      final int shardIndex,
                      final Path localDir,
                      final Path sharedShardDir) throws IOException {
        super(byteBuffers, byteBufferFactory, configProvider, doc, shardIndex, localDir, false);

        // Copy whatever data file is there, without consulting .version. A shard nothing has merged yet
        // has no data file, and opening then creates an empty environment to merge into.
        final Path sharedDataFile = sharedShardDir.resolve(PlanBConstants.DATA_FILE_NAME);
        if (Files.exists(sharedDataFile)) {
            // Replace rather than create: an interrupted cycle can leave a partial file behind, and
            // failing on it would stop this shard merging until the process restarts.
            Files.copy(sharedDataFile, localDir.resolve(PlanBConstants.DATA_FILE_NAME),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        open();
    }

    /**
     * Matches how the shared copy was built: a holding shard is read by key, never listed or sorted, so
     * building its secondary indexes here would publish indexes nothing reads.
     */
    @Override
    protected boolean withSecondaryIndexes() {
        return false;
    }

    /**
     * Moves entries into local per-bucket directories, then deletes them from this copy. The caller
     * publishes those directories to the shared store and removes them afterwards.
     *
     * @param doc            the document carrying the holding area settings
     * @param archiveBaseDir local base dir; bucket subdirs are created underneath
     * @return count of entries moved out (0 if there was nothing to move)
     */
    public long runArchival(final PlanBDocument doc,
                            final Path archiveBaseDir) throws IOException {
        // Present for any doc whose writes pass through a holding shard, which this shard by definition
        // does. Throw rather than return 0: silently not publishing would leave data only in the holding
        // area, which queries never read.
        final HoldingAreaSettings holdingArea =
                HasHoldingAreaSettings.holdingAreaSettings(doc.getSettings())
                        .orElseThrow(() -> new IllegalStateException(
                                "No holding area settings for " + doc.getName()));

        final Instant archiveBefore =
                SimpleDurationUtil.minus(Instant.now(), holdingArea.getCompletionGrace());

        Files.createDirectories(archiveBaseDir);

        final long count;
        try {
            writeLock.lockInterruptibly();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        try {
            count = db.runArchival(archiveBefore, archiveBaseDir);
            if (count > 0) {
                lastWriteTime = Instant.now();
            }
        } finally {
            writeLock.unlock();
        }

        return count;
    }
}
