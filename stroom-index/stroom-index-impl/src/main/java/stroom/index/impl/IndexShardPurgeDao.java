/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl;

import java.util.List;
import java.util.Optional;

/// Reads and writes `index_shard_purge`, the streams waiting to be deleted from index shards. A purge is
/// removed once a writer has deleted the stream and committed, by [IndexShardTaskDao#markCommitted].
public interface IndexShardPurgeDao {

    /// Gets the purge of a stream from a shard.
    ///
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream.
    /// @return The purge, if there is one.
    Optional<IndexShardPurge> fetch(long indexShardId, long streamId);

    /// Gets every purge of a shard.
    ///
    /// @param indexShardId The id of the shard.
    /// @return The purges, in order of stream.
    List<IndexShardPurge> findByShard(long indexShardId);

    /// Asks for a stream to be deleted from a shard. A purge that has been deleted but not committed is made
    /// to wait again, so that it is done again.
    ///
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream.
    void request(long indexShardId, long streamId);

    /// Records that a writer has deleted a stream, if the purge is still as it was when read and is waiting.
    ///
    /// @param expected The purge as it was read.
    /// @param writerId The writer that deleted the stream.
    /// @return The purge as changed, or empty if it had changed since it was read or has gone.
    Optional<IndexShardPurge> markDeleted(IndexShardPurge expected, String writerId);

    /// Makes the purges whose writer is no longer the shard's wait again, as that writer's deletes were never
    /// committed.
    ///
    /// @return The number of purges made to wait again.
    int resetLost();

    /// Finds the shards that have purges waiting and that nobody has open, for a sweep to purge. Deleted and
    /// corrupt shards are left out.
    ///
    /// @param limit The most shards to return.
    /// @return The ids of the shards.
    List<Long> findShardsToSweep(int limit);

    /// Removes the purges that can never be done or need not be: those of shards that are deleted or
    /// corrupt, and those of shards that nobody has open and that have never been committed to, as they hold
    /// nothing.
    ///
    /// @return The number of purges removed.
    int deleteForUnusableShards();

    /// Gets when the oldest purge was asked for.
    ///
    /// @return The time in database time, or empty if there are no purges.
    Optional<Long> findOldestCreateTimeMs();

    /// Counts the purges.
    ///
    /// @return The number of purges.
    long count();
}
