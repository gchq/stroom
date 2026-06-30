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
import stroom.planb.impl.db.StatePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the SIGSEGV (SEGV_MAPERR) crash caused by the merge
 * StoreShard and the long-lived query StoreShard sharing the same local LMDB
 * directory (and therefore the same {@code lock.mdb}).
 *
 * <h2>Root cause</h2>
 * LMDB's {@code lock.mdb} contains {@code PTHREAD_MUTEX_ROBUST |
 * PTHREAD_PROCESS_SHARED} pthread mutexes. glibc records the mmap'd address
 * of each acquired mutex in the owning thread's {@code robust_list}. When the
 * merge env closes ({@code mdb_env_close} → {@code munmap}), those addresses
 * become dangling. If the <em>same OS thread</em> then opens a new LMDB env
 * that remaps {@code lock.mdb} at a different virtual address, the next
 * {@code pthread_mutex_lock} tries to update the old (now-unmapped) list
 * entry and crashes with {@code SIGSEGV / SEGV_MAPERR}.
 *
 * <h2>Fix</h2>
 * {@link ShardManager#createStoreShard} now uses {@code statePaths.getMergingDir()}
 * as the base for the merge shard's working directory rather than
 * {@code statePaths.getShardDir()}. The two environments therefore never
 * share a directory and consequently never share {@code lock.mdb}.
 */
class TestMergeShardIsolation {

    /**
     * The core invariant: the merge base directory ({@code mergingDir}) must
     * be a different path from the long-lived query shard directory ({@code shardDir}).
     *
     * <p>{@link ShardManager#createStoreShard} passes {@code statePaths.getMergingDir()}
     * as {@code shardBaseDir} to the {@link StoreShard} 7-arg constructor. The resulting
     * shard dir is {@code mergingDir/<uuid>_<shardIndex>}.  The query shard (created via
     * the public 6-arg constructor) resolves to {@code shardDir/<uuid>_<shardIndex>}.
     * They must be distinct to prevent sharing {@code lock.mdb}.
     */
    @Test
    void mergingDir_and_shardDir_areDistinctPaths(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);

        assertThat(statePaths.getMergingDir())
                .as("mergingDir must be a different path from shardDir")
                .isNotEqualTo(statePaths.getShardDir());
    }

    /**
     * For a given doc UUID and shard index, the path computed by
     * {@link ShardManager#createStoreShard} (using {@code mergingDir}) must
     * not equal the path that the query {@link StoreShard} (using {@code shardDir})
     * would compute — even though both use the same {@code <uuid>_<index>} suffix.
     */
    @Test
    void mergeShardPath_doesNotConflictWithQueryShardPath(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final String docUuid = UUID.randomUUID().toString();
        final String suffix = docUuid + "_" + 0;

        final Path mergeShardPath = statePaths.getMergingDir().resolve(suffix);
        final Path queryShardPath = statePaths.getShardDir().resolve(suffix);

        assertThat(mergeShardPath)
                .as("merge shard path must differ from query shard path for the same doc/index")
                .isNotEqualTo(queryShardPath);

        assertThat(mergeShardPath.toAbsolutePath().toString())
                .as("merge shard path must be under mergingDir")
                .startsWith(statePaths.getMergingDir().toAbsolutePath().toString());

        assertThat(mergeShardPath.toAbsolutePath().toString())
                .as("merge shard path must NOT be under shardDir")
                .doesNotStartWith(statePaths.getShardDir().toAbsolutePath().toString());
    }

    /**
     * Verifies that {@link StatePaths} uses separate directory names for
     * {@code mergingDir} and {@code shardDir} as specified by
     * {@link PlanBConstants}.
     */
    @Test
    void statePaths_mergingDirAndShardDir_useDistinctConstants(@TempDir final Path tempDir) {
        assertThat(PlanBConstants.MERGING_DIR_NAME)
                .as("MERGING_DIR_NAME must differ from SHARDS_DIR_NAME")
                .isNotEqualTo(PlanBConstants.SHARDS_DIR_NAME);
    }
}
