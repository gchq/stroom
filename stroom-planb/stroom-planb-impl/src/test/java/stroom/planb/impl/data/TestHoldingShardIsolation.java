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
import stroom.planb.impl.PlanBPaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the directory separation that keeps a merge-cycle holding shard and a long-lived query
 * shard off the same local LMDB directory, and therefore off the same {@code lock.mdb}. Sharing one
 * crashes the JVM with SIGSEGV (SEGV_MAPERR).
 *
 * <h2>Why sharing crashes</h2>
 * LMDB's {@code lock.mdb} contains {@code PTHREAD_MUTEX_ROBUST |
 * PTHREAD_PROCESS_SHARED} pthread mutexes. glibc records the mmap'd address
 * of each acquired mutex in the owning thread's {@code robust_list}. When the
 * merge env closes ({@code mdb_env_close} → {@code munmap}), those addresses
 * become dangling. If the <em>same OS thread</em> then opens a new LMDB env
 * that remaps {@code lock.mdb} at a different virtual address, the next
 * {@code pthread_mutex_lock} tries to update the old (now-unmapped) list
 * entry and crashes with {@code SIGSEGV / SEGV_MAPERR}.
 *
 * <h2>What keeps them apart</h2>
 * {@code HoldingAreaMergeStrategy.mergeShard} builds its {@code HoldingShard} under
 * {@code planBPaths.getMergingDir()}, while {@code RestStoreShard} lives under
 * {@code planBPaths.getShardDir()}. The two environments therefore never share a directory and
 * consequently never share {@code lock.mdb}.
 *
 * <p>These tests assert only that those two roots, and the constants behind them, are distinct —
 * they compare paths and do not construct a shard.
 */
class TestHoldingShardIsolation {

    /**
     * The core invariant: the merge base directory ({@code mergingDir}) must
     * be a different path from the long-lived query shard directory ({@code shardDir}).
     *
     * <p>A holding shard resolves to {@code mergingDir/<uuid>_<shardIndex>}; a query shard
     * resolves under {@code shardDir}. They must be distinct to prevent sharing
     * {@code lock.mdb}.
     */
    @Test
    void mergingDir_and_shardDir_areDistinctPaths(@TempDir final Path tempDir) {
        final PlanBPaths planBPaths = new PlanBPaths(tempDir);

        assertThat(planBPaths.getMergingDir())
                .as("mergingDir must be a different path from shardDir")
                .isNotEqualTo(planBPaths.getShardDir());
    }

    /**
     * For a given doc UUID and shard index, the holding shard's path (under {@code mergingDir})
     * must not equal the query shard's path (under {@code shardDir}) — even though both use the
     * same {@code <uuid>_<index>} suffix.
     */
    @Test
    void mergeShardPath_doesNotConflictWithQueryShardPath(@TempDir final Path tempDir) {
        final PlanBPaths planBPaths = new PlanBPaths(tempDir);
        final String docUuid = UUID.randomUUID().toString();
        final String suffix = docUuid + "_" + 0;

        final Path mergeShardPath = planBPaths.getMergingDir().resolve(suffix);
        final Path queryShardPath = planBPaths.getShardDir().resolve(suffix);

        assertThat(mergeShardPath)
                .as("holding shard path must differ from query shard path for the same doc/index")
                .isNotEqualTo(queryShardPath);

        assertThat(mergeShardPath.toAbsolutePath().toString())
                .as("holding shard path must be under mergingDir")
                .startsWith(planBPaths.getMergingDir().toAbsolutePath().toString());

        assertThat(mergeShardPath.toAbsolutePath().toString())
                .as("holding shard path must NOT be under shardDir")
                .doesNotStartWith(planBPaths.getShardDir().toAbsolutePath().toString());
    }

    /**
     * Verifies that {@link PlanBPaths} uses separate directory names for
     * {@code mergingDir} and {@code shardDir} as specified by
     * {@link PlanBConstants}.
     */
    @Test
    void planBPaths_mergingDirAndShardDir_useDistinctConstants(@TempDir final Path tempDir) {
        assertThat(PlanBConstants.MERGING_DIR_NAME)
                .as("MERGING_DIR_NAME must differ from SHARDS_DIR_NAME")
                .isNotEqualTo(PlanBConstants.SHARDS_DIR_NAME);
    }
}
