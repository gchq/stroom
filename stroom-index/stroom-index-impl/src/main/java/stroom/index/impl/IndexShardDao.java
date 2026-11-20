/*
 * Copyright 2019 Crown Copyright
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

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IndexShardDao {

    /**
     * Retrieve a specific Shard by it's ID
     *
     * @param id The Database ID of the shard
     * @return The Shard from the database
     */
    Optional<IndexShard> fetch(long id);

    /**
     * Locate shards based on various criteria
     *
     * @param criteria The details of the query
     * @return Index Shards matching the criteria
     */
    ResultPage<IndexShard> find(FindIndexShardCriteria criteria);

    void search(ExpressionCriteria criteria,
                FieldIndex fieldIndex,
                ValuesConsumer consumer);

    /**
     * Create a new Index Shard
     *
     * @param key           The Index Shard details
     * @param indexVolume   The {@link IndexVolume} to use
     * @param ownerNodeName The node that will own the shard
     * @param indexVersion  The version of the index in use
     * @return The newly created Index Shard
     */
    IndexShard create(IndexShardKey key,
                      IndexVolume indexVolume,
                      String ownerNodeName,
                      String indexVersion);

    /**
     * Delete a specific shard, by it's ID
     *
     * @param id The database ID of the shard to delete
     */
    boolean delete(Long id);

    /**
     * Update the status of a shard
     *
     * @param id     The database ID of the shard to update
     * @param status The new status value
     */
    boolean setStatus(Long id, IndexShard.IndexShardStatus status);

    /// Claims the lease on a shard on a shared volume and marks the shard as opening, in one update, so
    /// that of two nodes that try to open a shard for writing only one can succeed.
    ///
    /// @param id              The database ID of the shard.
    /// @param nodeName        The name of the node claiming the lease, which becomes the shard's node.
    /// @param instanceId      The id of the process claiming the lease.
    /// @param leaseDurationMs How long the lease lasts if it is not renewed.
    /// @return True if the lease was claimed, false if the shard is not closed or is leased already.
    boolean claimLease(long id, String nodeName, String instanceId, long leaseDurationMs);

    /// Extends every lease held by a process that has not yet expired. A lease that has expired is left
    /// alone, as its holder must stop writing before it can have the shard again.
    ///
    /// @param instanceId      The id of the process holding the leases.
    /// @param leaseDurationMs How long the leases last from now if they are not renewed again.
    /// @return The number of leases extended.
    int renewLeases(String instanceId, long leaseDurationMs);

    /// Finds the shards a process holds an unexpired lease on.
    ///
    /// @param instanceId The id of the process holding the leases.
    /// @return The database IDs of the shards.
    Set<Long> findLeasedShardIds(String instanceId);

    /// Gives up the lease on a shard, if the process still holds it, and marks the shard as closed if it
    /// was open, in one update. The shard can then be claimed by any node.
    ///
    /// @param id                  The database ID of the shard.
    /// @param instanceId          The id of the process holding the lease.
    /// @param pruneCommitsDelayMs How long from now the shard's replaced commits must be kept for, or null
    ///                            if the writer left none behind.
    /// @return True if the lease was held by the process and has been given up.
    default boolean releaseLease(final long id, final String instanceId, final Long pruneCommitsDelayMs) {
        return releaseLease(id, instanceId, pruneCommitsDelayMs, false);
    }

    /// Gives up the lease on a shard as [#releaseLease(long, String, Long)] does, and can record in the
    /// same update that the writer stopped without a final commit.
    ///
    /// @param id                    The database ID of the shard.
    /// @param instanceId            The id of the process holding the lease.
    /// @param pruneCommitsDelayMs   How long from now the shard's replaced commits must be kept for, or
    ///                              null if the writer left none behind.
    /// @param recoverPendingStreams True if the writer stopped without committing all it had been given,
    ///                              so the streams the shard holds pending rows for must be recovered.
    /// @return True if the lease was held by the process and has been given up.
    boolean releaseLease(long id, String instanceId, Long pruneCommitsDelayMs, boolean recoverPendingStreams);

    /// Takes from their holders the leases that expired more than a grace period ago, which is how the
    /// shards of a node that has died, or can no longer reach the database, become usable again. Each
    /// shard is left closed and unleased, and marked as not closed cleanly so that the streams it holds
    /// pending rows for are recovered before it is next written to. A shard that has been deleted or
    /// marked corrupt keeps that status and is not marked. One update, so a holder that renews
    /// at the same moment either keeps its lease or loses it, never both.
    ///
    /// @param graceMs                   How long a lease must have been expired for. Treated as zero if
    ///                                  negative, so that a lease which has not expired is never taken.
    /// @param replacedCommitRetentionMs How long from now any commits the old writer had replaced must be
    ///                                  kept for before they are pruned.
    /// @return The number of shards taken.
    int releaseLapsedLeases(long graceMs, long replacedCommitRetentionMs);

    /// Says whether a shard was last written to by a writer that stopped without a final commit, so that
    /// the streams it holds pending rows for must be recovered before it is written to again.
    ///
    /// @param id The database ID of the shard.
    /// @return True if the shard's pending streams need recovering.
    boolean needsPendingStreamRecovery(long id);

    /// Records that a shard was last written to by a writer that stopped without a final commit. For a
    /// writer that can't say so as it gives up its lease, because the lease has already gone from the shard.
    /// Does nothing if another process has leased the shard since, as that process recovered the shard's
    /// streams when it opened it and may be part way through writing new ones.
    ///
    /// @param id The database ID of the shard.
    void setNeedsPendingStreamRecovery(long id);

    /// Records that a shard's pending streams have been marked for recovery.
    ///
    /// @param id The database ID of the shard.
    void clearPendingStreamRecovery(long id);

    /// Finds the closed shards whose pending streams need recovering.
    ///
    /// @param limit The most shards to return.
    /// @return The shards, with their volumes.
    List<IndexShard> findClosedShardsNeedingPendingStreamRecovery(int limit);

    /// Finds closed, unleased shards of an index partition that are on shared volumes, which any node may
    /// claim and write to.
    ///
    /// @param indexUuid      The UUID of the index.
    /// @param partitionLabel The label of the partition.
    /// @return The shards.
    List<IndexShard> findUnleasedShardsOnSharedVolumes(String indexUuid, String partitionLabel);

    /// Finds closed, unleased shards on shared volumes whose replaced commits have been kept for long
    /// enough and can now be removed.
    ///
    /// @param limit The most shards to return.
    /// @return The shards.
    List<IndexShard> findShardsWithCommitsToPrune(int limit);

    /// Records that a shard's replaced commits have been removed.
    ///
    /// @param id The database ID of the shard.
    void clearPruneCommitsTime(long id);

    /**
     * Force the status of a shard to be deleted
     */
    void logicalDelete(Long id);

    /// Says whether any process holds a lease on a shard, whether or not the lease has expired.
    ///
    /// @param id The database ID of the shard.
    /// @return True if the shard is leased.
    boolean isLeased(long id);

    /// Marks a shard that was open as closed. A shard that is leased is left alone, as only the process
    /// holding the lease may close it, with [#releaseLease(long, String, Long)].
    ///
    /// @param id The database ID of the shard.
    default void reset(final Long id) {
        reset(id, false);
    }

    /// Marks a shard that was open as closed as [#reset(Long)] does, and can record in the same update
    /// that the writer stopped without a final commit.
    ///
    /// @param id                    The database ID of the shard.
    /// @param recoverPendingStreams True if the streams the shard holds pending rows for must be recovered.
    void reset(Long id, boolean recoverPendingStreams);

    /// Marks a shard that an earlier process on this node left open as closed, and clears the lease that
    /// process held on it. That process did not close the shard, so the streams the shard holds pending
    /// rows for are marked as needing recovery. For use on startup. Does nothing if the shard has since
    /// been taken from this node and leased by another.
    ///
    /// @param id         The database ID of the shard.
    /// @param nodeName   The name of this node. Only a lease held under its name is cleared.
    /// @param instanceId The id of the process that is starting, whose own leases are left alone.
    void resetAfterRestart(Long id, String nodeName, String instanceId);

    /**
     * Update the details of the contents of a shard
     *
     * @param id               The database ID
     * @param documentCount    The number of documents
     * @param commitDurationMs commitDurationMs
     * @param commitMs         commitMs
     * @param fileSize         fileSize
     */
    void update(Long id, Integer documentCount, Long commitDurationMs, Long commitMs, Long fileSize);
}
