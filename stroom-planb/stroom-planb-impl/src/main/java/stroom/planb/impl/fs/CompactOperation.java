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

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.db.PlanBEnv.Usage;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * {@link SharedFileStoreOperation} that rebuilds a shard's {@code data.mdb} to return free pages to
 * the OS.
 *
 * <p>Retention and archival delete entries, but LMDB only moves the pages they freed onto its
 * freelist — the file never shrinks, and a shard whose map is fully allocated therefore stays
 * unable to accept a merge even after most of its data has gone. Compaction is what breaks that,
 * so it runs after retention (priority 100) and archival (priority 200) have done the deleting.</p>
 *
 * <p>Compaction copies every live page, so it is gated on there being a worthwhile amount to
 * reclaim and rate-limited by a {@code .compaction.last} marker in the canonical shared shard
 * directory.</p>
 */
public class CompactOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompactOperation.class);

    /** Don't bother with small shards — the free pages they hold are not worth a rewrite. */
    private static final long MIN_SIZE_BYTES = 256L * 1024L * 1024L;

    /** Fraction of the allocated file that must be free pages before a rewrite pays for itself. */
    private static final double MIN_RECLAIMABLE_FRACTION = 0.25;

    private static final Duration MIN_INTERVAL = Duration.ofHours(6);

    // Runs after retention (100) and archival (200), whose deletes create the free pages to reclaim.
    @Override
    public int priority() {
        return 300;
    }

    @Override
    public boolean isDue(final PlanBDocument doc,
                         final Path sharedShardsDocDir,
                         final int shardIndex) {
        final Path shardDir = sharedShardsDocDir.resolve(PlanBConstants.formatShardIndex(shardIndex));
        try {
            if (Files.size(shardDir.resolve(PlanBConstants.DATA_FILE_NAME)) < MIN_SIZE_BYTES) {
                return false;
            }
        } catch (final NoSuchFileException e) {
            return false;
        } catch (final IOException e) {
            LOGGER.debug(() -> LogUtil.message("Could not size shard data file in {}", shardDir), e);
            return false;
        }

        final Path lastFile = lastRunFile(sharedShardsDocDir, shardIndex);
        try {
            final Instant lastRun = Instant.parse(
                    Files.readString(lastFile, StandardCharsets.UTF_8).trim());
            return Instant.now().isAfter(lastRun.plus(MIN_INTERVAL));
        } catch (final NoSuchFileException e) {
            return true; // never run -- due immediately
        } catch (final Exception e) {
            LOGGER.warn("Could not read compaction last-run file {}, treating as due: {}",
                    lastFile, e.getMessage());
            return true;
        }
    }

    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        if (!isDue(ctx.doc(), ctx.sharedShardsDocDir(), ctx.shardIndex())) {
            return false;
        }

        writeLastRun(ctx.sharedShardsDocDir(), ctx.shardIndex());

        final Usage usage = ctx.shard().getUsage();
        final long liveBytes = ctx.shard().getLiveBytes();
        final long reclaimableBytes = Math.max(0, usage.usedBytes() - liveBytes);
        final double reclaimableFraction = usage.usedBytes() == 0
                ? 0
                : (double) reclaimableBytes / usage.usedBytes();

        if (reclaimableFraction < MIN_RECLAIMABLE_FRACTION) {
            LOGGER.debug(() -> LogUtil.message(
                    "Not compacting {}, only {} of {} is reclaimable",
                    ctx.lockName(),
                    ModelStringUtil.formatIECByteSizeString(reclaimableBytes),
                    ModelStringUtil.formatIECByteSizeString(usage.usedBytes())));
            return false;
        }

        LOGGER.info(() -> LogUtil.message(
                "Compacting {} to reclaim up to {} of {} allocated",
                ctx.lockName(),
                ModelStringUtil.formatIECByteSizeString(reclaimableBytes),
                ModelStringUtil.formatIECByteSizeString(usage.usedBytes())));
        ctx.shard().compact();
        return true;
    }

    private void writeLastRun(final Path sharedShardsDocDir, final int shardIndex) {
        final Path lastFile = lastRunFile(sharedShardsDocDir, shardIndex);
        try {
            Files.createDirectories(lastFile.getParent());
            Files.writeString(lastFile, Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.error("Failed to write compaction last-run file: {}", lastFile, e);
        }
    }

    private static Path lastRunFile(final Path sharedShardsDocDir, final int shardIndex) {
        return sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(shardIndex))
                .resolve(PlanBConstants.COMPACTION_LAST_FILE_NAME);
    }
}
