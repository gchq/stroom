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

package stroom.planb.impl.data;

import stroom.planb.impl.dao.Db;
import stroom.planb.shared.PlanBDoc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

public interface Shard {

    String SNAPSHOT_INFO_FILE_NAME = "snapshot.txt";

    /**
     * Merge data from the source dir into this shard.
     *
     * @param sourceDir The source dir to merge data from.
     */
    void merge(Path sourceDir);

    /**
     * Delete old data in the shard.
     */
    long deleteOldData(PlanBDoc doc);

    /**
     * Condense data in the shard.
     */
    long condense(PlanBDoc doc);

    /**
     * Compact data in the shard.
     */
    void compact();

    /**
     * Delete old merge status records. Only shards for additive stores (histogram and metric) track merge
     * status; the default is a no-op. The caller must ensure that no replayable copy of any merge source
     * still exists before pruning. See docs/merge-idempotency-design.md.
     *
     * @return The number of records deleted.
     */
    default long deleteOldMergeStatus(final Instant deleteBefore) {
        return 0;
    }

    /**
     * Determine if we are allowed to create a snapshot or if the snapshot we have is already the latest.
     *
     * @param request The request to create a snapshot.
     */
    void checkSnapshotStatus(SnapshotRequest request);

    /**
     * Create a snapshot ready to be streamed to a requesting node.
     */
    void createSnapshot();

    /**
     * Get data from this shard.
     *
     * @param function
     * @param <R>
     * @return
     */
    <R> R get(Function<Db<?, ?>, R> function);

    /**
     * Delete the DB if the associated doc has been deleted.
     */
    boolean delete();

    /**
     * Get the Plan B doc associated with this shard.
     *
     * @return The Plan B doc associated with this shard.
     */
    PlanBDoc getDoc();

    /**
     * Get information about the environment and associated databases as a JSON string.
     *
     * @return Information about the environment and associated databases as a JSON string.
     */
    String getInfo();

    /**
     * @return true if the shard is idle and can be safely evicted from the shard map.
     */
    boolean isIdle();

    /**
     * Close the shard's underlying database, closing its LMDB environment. A {@link StoreShard}
     * waits for in-flight readers and writers to finish before closing, and its data remains on
     * disk to be reopened by a newly created shard. A {@link SnapshotShard} discards its local
     * copy; if readers are in flight the actual close/delete is deferred until the last one
     * finishes, so the env may still be open when this method returns. The shard must not be
     * used after closing — obtain a fresh one from the {@code ShardManager}.
     */
    void close();
}
