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

package stroom.pathways.impl;

import stroom.pathways.impl.PathwaySerde.StoredNode;
import stroom.pathways.shared.otel.trace.NanoTime;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a hold needs to know to carry on one pathway's history: how that history numbers the pathway's
 * nodes, and how busy each node was when the last reading was taken.
 *
 * <p>Both are read once when a hold first touches a model and kept for the length of it, because both
 * are needed by every change the hold goes on to write and neither moves except by the hold's own
 * hand.
 *
 * <p>The numbering is so that a change can say which node it was made against with a number rather
 * than with that node's uuid and its whole path from the root. A node receives many changes over its
 * life and the two together are most of what a change costs to store. A node is what it is and where
 * it was, both: the model's shape can move around a node, and a change has to say where the node
 * stood when the change was made, so a node whose path has moved is a different entry rather than an
 * edited one.
 *
 * <p>The counts are so that a reading can name only the nodes that have actually moved since the one
 * before it. Most of a pathway is untouched by any one trace, and writing every node's count every
 * time is most of what a reading costs.
 */
final class PathwayHistory {

    private final Map<StoredNode, Long> ids = new HashMap<>();
    private final Map<Long, StoredNode> nodes = new HashMap<>();
    private long nextId;

    // Kept in the order the nodes were first met so that a reading reads the way the model walks.
    private final Map<Long, Count> counts = new LinkedHashMap<>();
    private boolean countsMoved;

    /** Takes a node the pathway already held, as it was numbered then. */
    void put(final long id, final StoredNode node) {
        ids.putIfAbsent(node, id);
        nodes.put(id, node);
        nextId = Math.max(nextId, id + 1);
    }

    Long id(final StoredNode node) {
        return ids.get(node);
    }

    /** Numbers a node met for the first time. The caller writes it; this only remembers it. */
    long add(final StoredNode node) {
        final long id = nextId;
        nextId++;
        ids.put(node, id);
        nodes.put(id, node);
        return id;
    }

    Map<Long, Count> counts() {
        return counts;
    }

    /** Whether any reading this hold took moved a count, so there is a new state worth writing. */
    boolean countsMoved() {
        return countsMoved;
    }

    void countsWritten() {
        countsMoved = false;
    }

    /** Takes a count the last reading left, which is not a move of its own. */
    void seed(final long nodeId, final Count count) {
        counts.put(nodeId, count);
    }

    void count(final long nodeId, final Count count) {
        counts.put(nodeId, count);
        countsMoved = true;
    }

    void uncount(final long nodeId) {
        counts.remove(nodeId);
        countsMoved = true;
    }

    /** How busy one node was when a reading was taken. */
    record Count(long timesUsed, NanoTime lastUsedTime) {

    }
}
