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

package stroom.search.impl;

import stroom.index.impl.IndexShardResponsibility;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/// Decides which node reads an index shard for a search.
///
/// A shard on a local volume can only be read by the node that owns it. A shard on a shared volume can be
/// read by any node, so a search does not depend on the node that wrote it:
///
/// * A closed shard goes to whichever running node scores highest on a hash of the shard id and the node
///   name. A shard therefore goes to the same node from one search to the next, which keeps that node's file
///   cache warm, and when a node joins or leaves only the shards it gains or loses move.
/// * A shard that is open for writing goes to its owner while that node is running, because only the writer
///   sees documents that are not yet committed. If the owner is not running the shard goes to another node
///   in the same way as a closed shard, and is read up to its last commit.
final class ShardNodeAssigner {

    private ShardNodeAssigner() {
        // Static methods only.
    }

    /// Chooses the node to read a shard at the start of a search.
    ///
    /// @param indexShard   The shard to read.
    /// @param runningNodes The nodes that are able to search.
    /// @return The node to read the shard, which for a shard on a local volume is its owner whether or not
    /// that node is running. Empty if the shard holds nothing that any running node could read.
    static Optional<String> chooseNode(final IndexShard indexShard, final Set<String> runningNodes) {
        final String ownerNode = indexShard.getNodeName();
        if (!isOnSharedVolume(indexShard) || runningNodes.isEmpty()) {
            return Optional.of(ownerNode);
        } else if (IndexShardStatus.CLOSED.equals(indexShard.getStatus())) {
            return Optional.of(highestScoringNode(indexShard.getId(), runningNodes));
        } else if (runningNodes.contains(ownerNode)) {
            return Optional.of(ownerNode);
        } else if (isNeverCommitted(indexShard)) {
            // Its writer has not committed anything, so there is nothing on disk for another node to read.
            return Optional.empty();
        } else {
            return Optional.of(highestScoringNode(indexShard.getId(), runningNodes));
        }
    }

    /// Chooses another node to read a shard after the node first chosen could not be reached.
    ///
    /// @param indexShard     The shard to read.
    /// @param remainingNodes The nodes that are able to search, without the ones that could not be reached.
    /// @return The outcome for the shard.
    static Reassignment reassign(final IndexShard indexShard, final Set<String> remainingNodes) {
        if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
            // A corrupt shard is never searched, wherever it is.
            return Reassignment.NOT_READABLE;
        } else if (isOnSharedVolume(indexShard)
                   && !IndexShardStatus.CLOSED.equals(indexShard.getStatus())
                   && isNeverCommitted(indexShard)) {
            // Nothing has been committed to the shard, and its writer is the node that could not be reached.
            return Reassignment.NOTHING_TO_READ;
        } else if (!isOnSharedVolume(indexShard) || remainingNodes.isEmpty()) {
            return Reassignment.NOT_READABLE;
        } else {
            return new Reassignment(highestScoringNode(indexShard.getId(), remainingNodes), false);
        }
    }

    /// Picks a node for a shard by rendezvous hashing, i.e. the node with the highest score for the shard.
    ///
    /// @param shardId The id of the shard.
    /// @param nodes   The nodes to choose from, which must not be empty.
    /// @return The chosen node.
    static String highestScoringNode(final long shardId, final Collection<String> nodes) {
        // The same choice as is made of the node that looks after a shard.
        return IndexShardResponsibility.highestScoringNode(shardId, nodes);
    }

    // The writer records the time and document count of each commit, so a shard that has neither has only
    // ever held documents in its writer's memory.
    private static boolean isNeverCommitted(final IndexShard indexShard) {
        return indexShard.getCommitMs() == null && indexShard.getDocumentCount() == 0;
    }

    private static boolean isOnSharedVolume(final IndexShard indexShard) {
        return NullSafe.test(indexShard.getVolume(), volume -> volume.isShared());
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /// What becomes of a shard whose first node could not be reached.
    ///
    /// @param nodeName    The node that will now read the shard, or null if no node will.
    /// @param notReadable True if the shard holds data that no remaining node can read, so results will be
    ///                    missing.
    record Reassignment(String nodeName, boolean notReadable) {

        static final Reassignment NOTHING_TO_READ = new Reassignment(null, false);
        static final Reassignment NOT_READABLE = new Reassignment(null, true);
    }
}
