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

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.node.api.NodeInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/// Decides which node looks after an index shard, i.e. checks its retention, deletes its files and acts on
/// requests to flush or delete it.
///
/// A shard on a local volume is looked after by the node that owns it, as no other node can reach its
/// files. A shard on a shared volume has no owner. While it is open for writing it is looked after by the
/// node that holds its lease, if that node is running. Otherwise it is looked after by whichever running
/// node scores highest on a hash of the shard id and the node name, so that exactly one node takes it on and
/// a shard written by a node that has gone for good is not left behind.
///
/// Nodes can briefly disagree about which of them are running, so an action taken on this basis must be
/// harmless if two nodes take it, or none does until the next time.
public class IndexShardResponsibility {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardResponsibility.class);

    private static final Set<IndexShardStatus> OPEN_STATUSES = EnumSet.of(
            IndexShardStatus.OPENING,
            IndexShardStatus.OPEN,
            IndexShardStatus.CLOSING);

    private final NodeInfo nodeInfo;
    private final TargetNodeSetFactory targetNodeSetFactory;

    @Inject
    IndexShardResponsibility(final NodeInfo nodeInfo,
                             final TargetNodeSetFactory targetNodeSetFactory) {
        this.nodeInfo = nodeInfo;
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    /// Makes a test of whether this node looks after a shard, using the nodes that are running now. Make
    /// one for each batch of shards, so that they are all judged against the same set of nodes.
    ///
    /// @return The test.
    public Predicate<IndexShard> createTest() {
        final String thisNodeName = nodeInfo.getThisNodeName();
        final Set<String> runningNodes = getRunningNodes();
        return indexShard -> isResponsible(indexShard, thisNodeName, runningNodes);
    }

    /// Says whether a node looks after a shard.
    ///
    /// @param indexShard   The shard.
    /// @param nodeName     The node in question.
    /// @param runningNodes The nodes that are running, or empty if that is not known.
    /// @return True if the node looks after the shard.
    static boolean isResponsible(final IndexShard indexShard,
                                 final String nodeName,
                                 final Set<String> runningNodes) {
        final String shardNode = indexShard.getNodeName();
        if (!NullSafe.test(indexShard.getVolume(), IndexVolume::isShared) || runningNodes.isEmpty()) {
            return nodeName.equals(shardNode);
        } else if (OPEN_STATUSES.contains(indexShard.getStatus()) && runningNodes.contains(shardNode)) {
            // Its writer is on the node that holds its lease.
            return nodeName.equals(shardNode);
        } else {
            return nodeName.equals(highestScoringNode(indexShard.getId(), runningNodes));
        }
    }

    /// Picks a node for a shard by rendezvous hashing, i.e. the node with the highest score for the shard.
    /// A shard goes to the same node each time, and when a node joins or leaves only the shards it gains or
    /// loses move.
    ///
    /// @param shardId The id of the shard.
    /// @param nodes   The nodes to choose from, which must not be empty.
    /// @return The chosen node.
    public static String highestScoringNode(final long shardId, final Collection<String> nodes) {
        String best = null;
        long bestScore = 0;
        for (final String node : nodes) {
            final long score = score(shardId, node);
            // The name breaks a tie so that the choice never depends on the order of the nodes.
            if (best == null || score > bestScore || (score == bestScore && node.compareTo(best) > 0)) {
                best = node;
                bestScore = score;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("No nodes to choose from");
        }
        return best;
    }

    // Every node must compute the same score, so this uses String.hashCode, which the language defines, and
    // then mixes the bits well so that the scores of similar ids and names are unrelated.
    private static long score(final long shardId, final String node) {
        long hash = shardId * 0x9E3779B97F4A7C15L + node.hashCode();
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return hash ^ (hash >>> 31);
    }

    private Set<String> getRunningNodes() {
        try {
            return targetNodeSetFactory.getEnabledActiveTargetNodeSet();
        } catch (final NullClusterStateException | NodeNotFoundException e) {
            // With no known nodes every shard stays with its own node, as it did before shards were shared.
            LOGGER.debug(e::getMessage, e);
            return Set.of();
        }
    }
}
