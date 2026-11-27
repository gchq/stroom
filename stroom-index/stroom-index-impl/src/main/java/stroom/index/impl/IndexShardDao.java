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

import java.util.Collection;
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

    /// Marks a shard that is being opened for writing as open, and records the writer that has it open, which
    /// is what the rows of `index_shard_task` that the writer is given are compared with.
    ///
    /// @param id       The database ID of the shard.
    /// @param writerId The new writer's id.
    /// @return True if the shard was being opened and is now open. False if it has been deleted meanwhile.
    boolean markOpen(long id, String writerId);

    /// Closes a writer, in one transaction: if the writer closed cleanly, every row of `index_shard_task` it
    /// was given is marked *committed* and the purges it did are removed, as closing committed all it had;
    /// then the shard is marked closed, its writer id is cleared and the lease is given up. Nothing is done
    /// unless the shard still has that writer, which is checked with the shard's row locked, so a writer that
    /// has lost its shard can't mark another writer's rows or close the shard under it.
    ///
    /// @param id                  The database ID of the shard.
    /// @param writerId            The writer's id.
    /// @param leaseInstanceId     The process holding the lease the writer wrote under, or null if it had
    ///                            none, as for a shard on a local volume.
    /// @param pruneCommitsDelayMs How long from now the shard's replaced commits must be kept for, or null if
    ///                            the writer left none behind.
    /// @param clean               True if the writer closed cleanly, so its rows and purges were committed.
    /// @return The ids of the tasks whose *written* rows were marked *committed*, which may now be complete,
    /// or empty if the shard no longer had that writer, so nothing was done.
    Optional<Set<Long>> closeWriter(long id,
                                    String writerId,
                                    String leaseInstanceId,
                                    Long pruneCommitsDelayMs,
                                    boolean clean);

    /// Claims the lease on a shard on a shared volume and marks the shard as opening, in one update, so
    /// that of two nodes that try to open a shard for writing only one can succeed.
    ///
    /// @param id              The database ID of the shard.
    /// @param nodeName        The name of the node claiming the lease, which becomes the shard's node.
    /// @param instanceId      The id of the process claiming the lease.
    /// @param leaseDurationMs How long the lease lasts if it is not renewed.
    /// @return True if the lease was claimed, false if the shard is not closed or is leased already.
    boolean claimLease(long id, String nodeName, String instanceId, long leaseDurationMs);

    /// Extends the leases a process holds on the given shards that have not yet expired. A lease that has
    /// expired is left alone, as its holder must stop writing before it can have the shard again, and so is
    /// one the process holds on a shard that is not given, which it has stopped relying on and must lapse.
    ///
    /// @param instanceId      The id of the process holding the leases.
    /// @param shardIds        The ids of the shards whose leases the process still relies on.
    /// @param leaseDurationMs How long the leases last from now if they are not renewed again.
    /// @return The number of leases extended.
    int renewLeases(String instanceId, Collection<Long> shardIds, long leaseDurationMs);

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
    boolean releaseLease(long id, String instanceId, Long pruneCommitsDelayMs);

    /// Takes from their holders the leases that expired more than a grace period ago, which is how the
    /// shards of a node that has died, or can no longer reach the database, become usable again. Each
    /// shard is left closed and unleased. A shard that has been deleted or marked corrupt keeps that
    /// status. One update, so a holder that renews
    /// at the same moment either keeps its lease or loses it, never both.
    ///
    /// @param graceMs                   How long a lease must have been expired for. Treated as zero if
    ///                                  negative, so that a lease which has not expired is never taken.
    /// @param replacedCommitRetentionMs How long from now any commits the old writer had replaced must be
    ///                                  kept for before they are pruned.
    /// @return The number of shards taken.
    int releaseLapsedLeases(long graceMs, long replacedCommitRetentionMs);

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

    /// Marks a shard as deleted, unless another process holds the lease on it. That process may be writing
    /// to the shard, and would not know it had been deleted, so documents it went on to add would be lost
    /// when the shard's files were removed.
    ///
    /// @param id         The database ID of the shard.
    /// @param instanceId The id of the process asking, which may hold the lease itself.
    /// @return True if the shard has been marked as deleted.
    boolean logicalDelete(long id, String instanceId);

    /// Says whether any process holds a lease on a shard, whether or not the lease has expired.
    ///
    /// @param id The database ID of the shard.
    /// @return True if the shard is leased.
    boolean isLeased(long id);

    /// Marks a shard that was open as closed. A shard that is leased is left alone, as only the process
    /// holding the lease may close it, with [#releaseLease(long, String, Long)].
    ///
    /// @param id The database ID of the shard.
    void reset(Long id);

    /// Marks a shard that an earlier process on this node left open as closed, and clears the lease that
    /// process held on it. For use on startup. Does nothing if the shard has since
    /// been taken from this node and leased by another.
    ///
    /// @param id                  The database ID of the shard.
    /// @param nodeName            The name of this node. Only a lease held under its name is cleared.
    /// @param instanceId          The id of the process that is starting, whose own leases are left alone.
    /// @param pruneCommitsDelayMs How long from now any commits the earlier process had replaced must be
    ///                            kept for, or null if it can have left none behind.
    void resetAfterRestart(Long id, String nodeName, String instanceId, Long pruneCommitsDelayMs);

    /// Clears the leases that an earlier process on this node held on shards that are deleted or corrupt,
    /// keeping their status. Nothing else would clear them, and a leased shard's files are never deleted. For
    /// use on startup.
    ///
    /// @param nodeName   The name of this node. Only leases held under its name are cleared.
    /// @param instanceId The id of the process that is starting, whose own leases are left alone.
    /// @return The number of leases cleared.
    int clearLeasesOfUnusableShardsAfterRestart(String nodeName, String instanceId);

    /// Records the details of a shard's contents after a commit, if the writer that committed may still
    /// change the shard's row: only while its process holds the shard's lease, or, for a shard on a local
    /// volume, while nobody does.
    ///
    /// @param id               The database ID of the shard.
    /// @param documentCount    The number of documents.
    /// @param commitDurationMs How long the commit took.
    /// @param commitMs         When the commit finished.
    /// @param fileSize         The size of the shard's files.
    /// @param leaseInstanceId  The process holding the lease the writer wrote under, or null if it had none.
    void update(Long id,
                Integer documentCount,
                Long commitDurationMs,
                Long commitMs,
                Long fileSize,
                String leaseInstanceId);
}
