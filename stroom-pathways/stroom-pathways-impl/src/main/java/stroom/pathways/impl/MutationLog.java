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

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.impl.PathwayHistory.Count;
import stroom.pathways.impl.PathwaySerde.StoredChange;
import stroom.pathways.impl.PathwaySerde.StoredCount;
import stroom.pathways.impl.PathwaySerde.StoredNode;
import stroom.pathways.impl.PathwaySerde.StoredTrace;
import stroom.pathways.impl.PathwaySerde.StoredUsage;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.NodeUsage;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.pathways.shared.pathway.PathwayUsage;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.dao.trace.PathwaysDb.SimpleDb;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The record of every change made to a model, numbered in the order the changes were made.
 *
 * <p>A model has one history and everything that changes it writes here: a trace that widened a
 * constraint, and the narrowing that puts one back to what has been seen lately. Numbering them
 * in a single run is what lets a replay walk the model forwards or backwards through both.
 */
@Singleton
public class MutationLog {

    private static final byte CHANGE_MARKER = 0;
    private static final byte USAGE_MARKER = 1;
    private static final byte NODE_MARKER = 2;
    private static final byte TRACE_MARKER = 3;
    private static final byte COUNTS_MARKER = 4;

    private final ByteBuffers byteBuffers;
    private final PathwaySerde pathwaySerde;

    @Inject
    public MutationLog(final ByteBuffers byteBuffers, final PathwaySerde pathwaySerde) {
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
    }

    /**
     * Adds changes to a model's history, numbered on from whatever it already holds, and records how
     * busy every node was once they had been made.
     *
     * <p>Not private because the narrowing appends through it too: a model has one history,
     * and a change made by a trace and a change made by holding the model to its window have to be
     * numbered in the same run or a replay cannot walk them.
     */
    void append(final LmdbWriter writer,
                final PathwaysDb pathwaysDb,
                final byte[] pathwayKey,
                final List<PathwayMutation> mutations,
                final PathNode root,
                final int source,
                final PathwayHistory history) {
        if (mutations.isEmpty()) {
            return;
        }

        final SimpleDb db = pathwaysDb.getMutations();
        final long first = lastSequence(writer, db, pathwayKey) + 1;

        // What made this run of changes, written once however many it made. Every change here came
        // from one trace, or from one run of the narrowing, so the trace it came from and when it
        // arrived are the same for all of them. The span is not here: it moves from node to node
        // within a trace, so it stays with the change that names the node.
        final PathwayMutation firstMutation = mutations.getFirst();
        byteBuffers.useBytes(traceKey(pathwayKey, first), (Consumer<ByteBuffer>) keyByteBuffer ->
                pathwaySerde.writeTrace(new StoredTrace(firstMutation.getTraceId(),
                                firstMutation.getTime(),
                                firstMutation.getTraceTime(),
                                source),
                        valueByteBuffer -> db.insert(writer, keyByteBuffer, valueByteBuffer)));

        long sequence = first - 1;
        for (final PathwayMutation mutation : mutations) {
            sequence++;
            final long nodeId = idOf(writer, db, pathwayKey, history,
                    new StoredNode(mutation.getNodeUuid(), mutation.getNodePath()));
            final StoredChange change = new StoredChange(sequence,
                    first,
                    nodeId,
                    mutation.getSpanId(),
                    mutation.getConstraint(),
                    mutation.getType(),
                    mutation.isOptional(),
                    mutation.getOldValue(),
                    mutation.getNewValue());
            byteBuffers.useBytes(mutationKey(pathwayKey, sequence), (Consumer<ByteBuffer>) keyByteBuffer ->
                    pathwaySerde.writeMutation(change, valueByteBuffer ->
                            db.insert(writer, keyByteBuffer, valueByteBuffer)));
        }

        writeUsage(writer, db, pathwayKey, sequence, firstMutation.getTime(), root, history);
    }

    /**
     * The numbering this pathway's changes refer to its nodes by, as it stands. Read once for each
     * model a hold touches and added to as that hold meets nodes it has not seen, so the numbering
     * carries on from where the model left it rather than starting again.
     */
    PathwayHistory history(final LmdbWriter writer,
                           final PathwaysDb pathwaysDb,
                           final byte[] pathwayKey) {
        final PathwayHistory history = new PathwayHistory();
        final SimpleDb db = pathwaysDb.getMutations();
        db.iteratePrefix(writer.getWriteTxn(), prefixOf(pathwayKey, NODE_MARKER), (key, value) ->
                history.put(idOfKey(key), pathwaySerde.readNode(value)));
        byteBuffers.useBytes(countsKey(pathwayKey), (Consumer<ByteBuffer>) keyByteBuffer ->
                db.get(writer.getWriteTxn(), keyByteBuffer, value -> {
                    if (value != null) {
                        pathwaySerde.readCounts(value).forEach(count ->
                                history.seed(count.nodeId(),
                                        new Count(count.timesUsed(), count.lastUsedTime())));
                    }
                    return null;
                }));
        return history;
    }

    /**
     * How busy the pathway's nodes are as the last reading left them, written once at the end of a
     * hold rather than with each reading. A reading says only what has moved, so the next one has to
     * know where things stood — and reading that back from the readings would mean replaying the whole
     * history to add one line to it. This is written over each time and is not part of the history.
     */
    void writeCounts(final LmdbWriter writer,
                     final PathwaysDb pathwaysDb,
                     final byte[] pathwayKey,
                     final PathwayHistory history) {
        if (!history.countsMoved()) {
            return;
        }
        final List<StoredCount> counts = new ArrayList<>();
        history.counts().forEach((id, count) ->
                counts.add(new StoredCount(id, count.timesUsed(), false, count.lastUsedTime())));
        byteBuffers.useBytes(countsKey(pathwayKey), (Consumer<ByteBuffer>) keyByteBuffer ->
                pathwaySerde.writeCounts(counts, valueByteBuffer ->
                        pathwaysDb.getMutations().insert(writer, keyByteBuffer, valueByteBuffer)));
        history.countsWritten();
    }

    private long idOf(final LmdbWriter writer,
                      final SimpleDb db,
                      final byte[] pathwayKey,
                      final PathwayHistory history,
                      final StoredNode node) {
        final Long known = history.id(node);
        if (known != null) {
            return known;
        }
        final long id = history.add(node);
        byteBuffers.useBytes(nodeKey(pathwayKey, id), (Consumer<ByteBuffer>) keyByteBuffer ->
                pathwaySerde.writeNode(node, valueByteBuffer ->
                        db.insert(writer, keyByteBuffer, valueByteBuffer)));
        return id;
    }

    /**
     * One pathway's changes, in the order they were made, with the nodes and traces they refer to by
     * number put back. The numbering is read first so that a change can be handed back whole, which
     * is what everything above here expects to be given.
     */
    List<PathwayMutation> read(final SimpleDb db, final byte[] pathwayKey) {
        final Map<Long, StoredNode> nodes = new HashMap<>();
        db.iteratePrefix(prefixOf(pathwayKey, NODE_MARKER), (key, value) ->
                nodes.put(idOfKey(key), pathwaySerde.readNode(value)));

        final Map<Long, StoredTrace> traces = new HashMap<>();
        db.iteratePrefix(prefixOf(pathwayKey, TRACE_MARKER), (key, value) ->
                traces.put(idOfKey(key), pathwaySerde.readTrace(value)));

        final List<PathwayMutation> mutations = new ArrayList<>();
        db.iteratePrefix(prefixOf(pathwayKey, CHANGE_MARKER), (key, value) ->
                mutations.add(resolve(pathwaySerde.readMutation(value), nodes, traces)));
        return mutations;
    }

    private static PathwayMutation resolve(final StoredChange change,
                                           final Map<Long, StoredNode> nodes,
                                           final Map<Long, StoredTrace> traces) {
        final StoredNode node = nodes.get(change.nodeId());
        final StoredTrace trace = traces.get(change.traceSequence());
        return new PathwayMutation(change.sequence(),
                trace == null
                        ? null
                        : trace.time(),
                trace == null
                        ? null
                        : trace.traceTime(),
                trace == null
                        ? -1
                        : trace.source(),
                trace == null
                        ? null
                        : trace.traceId(),
                change.spanId(),
                node == null
                        ? List.of()
                        : node.path(),
                node == null
                        ? null
                        : node.uuid(),
                change.constraint(),
                change.type(),
                change.optional(),
                change.oldValue(),
                change.newValue());
    }

    // What this trace changed about how busy the model's nodes are: the ones it ran, and the ones it
    // no longer holds. The rest of the model stands exactly as the reading before this one left it, so
    // naming it again would be most of what a reading cost. Taken once for the trace, not once for
    // each change it made — a trace that moved nine constraints leaves nine changes and one reading.
    //
    // Taken at all because nothing else records it: how often a node has been used is what the model
    // holds now, and a trace that teaches the model nothing changes it without leaving any trace of
    // having done so. Without these a replay can say what the model allowed at a point but not how
    // busy it was.
    //
    // Numbered with the last change the trace made, which is how winding back to any one of that
    // trace's changes finds this reading: it is the first at or after the change picked. Not the last
    // one before it — that is the state before this trace ran, and a change of this trace being on
    // show means it has. A count moves once for the whole trace, so there is no state between one of
    // its changes and the next for a reading to describe.
    private void writeUsage(final LmdbWriter writer,
                            final SimpleDb db,
                            final byte[] pathwayKey,
                            final long sequence,
                            final NanoTime time,
                            final PathNode root,
                            final PathwayHistory history) {
        final List<StoredCount> moved = new ArrayList<>();
        final Set<Long> present = new HashSet<>();
        collectUsage(writer, db, pathwayKey, root, history, time, moved, present);

        // A node the model no longer holds, said so that a reading rebuilt by carrying the one before
        // it forward does not carry a node that has gone.
        final List<Long> gone = new ArrayList<>();
        history.counts().keySet().forEach(id -> {
            if (!present.contains(id)) {
                gone.add(id);
            }
        });
        gone.forEach(history::uncount);

        byteBuffers.useBytes(usageKey(pathwayKey, sequence), (Consumer<ByteBuffer>) keyByteBuffer ->
                pathwaySerde.writeUsage(new StoredUsage(sequence, time, moved, gone), valueByteBuffer ->
                        db.insert(writer, keyByteBuffer, valueByteBuffer)));
    }

    private void collectUsage(final LmdbWriter writer,
                              final SimpleDb db,
                              final byte[] pathwayKey,
                              final PathNode node,
                              final PathwayHistory history,
                              final NanoTime time,
                              final List<StoredCount> moved,
                              final Set<Long> present) {
        if (node == null) {
            return;
        }
        final long id = idOf(writer, db, pathwayKey, history,
                new StoredNode(node.getUuid(), node.getNodePath()));
        present.add(id);

        final Count was = history.counts().get(id);
        final long timesUsed = node.getTimesUsed();
        final NanoTime lastUsedTime = node.getLastUsedTime();
        if (was == null
            || was.timesUsed() != timesUsed
            || !Objects.equals(was.lastUsedTime(), lastUsedTime)) {
            moved.add(new StoredCount(id, timesUsed, Objects.equals(lastUsedTime, time), lastUsedTime));
            history.count(id, new Count(timesUsed, lastUsedTime));
        }

        NullSafe.list(node.getChildren()).forEach(child ->
                collectUsage(writer, db, pathwayKey, child, history, time, moved, present));
    }

    /**
     * One pathway's readings, in the order they were taken, each put back together whole. A reading
     * says only what moved since the one before it, so they are rebuilt by carrying each forward into
     * the next — which is what reading them in key order amounts to.
     */
    List<PathwayUsage> readUsage(final SimpleDb db, final byte[] pathwayKey) {
        final Map<Long, StoredNode> nodes = new HashMap<>();
        db.iteratePrefix(prefixOf(pathwayKey, NODE_MARKER), (key, value) ->
                nodes.put(idOfKey(key), pathwaySerde.readNode(value)));

        final List<PathwayUsage> readings = new ArrayList<>();
        // Insertion ordered, so a reading names its nodes in the order the model first met them
        // rather than in whatever order a hash gives back.
        final Map<Long, NodeUsage> running = new LinkedHashMap<>();
        db.iteratePrefix(prefixOf(pathwayKey, USAGE_MARKER), (key, value) -> {
            final StoredUsage stored = pathwaySerde.readUsage(value);
            stored.gone().forEach(running::remove);
            stored.moved().forEach(count -> {
                final StoredNode node = nodes.get(count.nodeId());
                running.put(count.nodeId(), new NodeUsage(node == null
                        ? null
                        : node.uuid(),
                        count.timesUsed(),
                        count.usedNow()
                                ? stored.time()
                                : count.lastUsedTime()));
            });
            readings.add(new PathwayUsage(stored.sequence(), new ArrayList<>(running.values())));
        });
        return readings;
    }

    // Where this pathway's history got to, taken from the last key rather than kept anywhere, so
    // nothing has to stay in step with it. Read on the batch's own transaction, so it counts on from
    // what earlier traces in the same batch wrote rather than starting them all again from what was
    // last committed.
    private static long lastSequence(final LmdbWriter writer,
                                     final SimpleDb db,
                                     final byte[] pathwayKey) {
        return db.lastPrefixed(writer.getWriteTxn(), prefixOf(pathwayKey, CHANGE_MARKER), key -> key == null
                ? 0L
                : key.getLong(key.limit() - Long.BYTES));
    }

    // Pathway first, so one model's changes are a single run of keys, then where each sits in that
    // history. Big endian because LMDB orders keys by their bytes, and that is the order a replay
    // walks them in.
    // Alongside the changes but under a marker of their own, so that reading a pathway's changes does
    // not walk over these as well.
    private static byte[] usageKey(final byte[] pathwayKey, final long sequence) {
        return markedKey(pathwayKey, USAGE_MARKER, sequence);
    }

    private static byte[] mutationKey(final byte[] pathwayKey, final long sequence) {
        return markedKey(pathwayKey, CHANGE_MARKER, sequence);
    }

    // What a run of changes came from, numbered with the first change it made, which is the number
    // those changes point back at.
    private static byte[] traceKey(final byte[] pathwayKey, final long sequence) {
        return markedKey(pathwayKey, TRACE_MARKER, sequence);
    }

    private static byte[] nodeKey(final byte[] pathwayKey, final long id) {
        return markedKey(pathwayKey, NODE_MARKER, id);
    }

    // One row per pathway rather than one per reading, so it is numbered zero and written over.
    private static byte[] countsKey(final byte[] pathwayKey) {
        return markedKey(pathwayKey, COUNTS_MARKER, 0L);
    }

    private static byte[] markedKey(final byte[] pathwayKey, final byte marker, final long number) {
        final ByteBuffer buffer = ByteBuffer.allocate(pathwayKey.length + 1 + Long.BYTES);
        buffer.put(pathwayKey);
        buffer.put(marker);
        buffer.putLong(number);
        return buffer.array();
    }

    /**
     * Everything this table holds about one pathway: its changes, its usage readings, and the nodes
     * and traces those refer to. Done here because the markers that tell them apart are this class's
     * own, and a caller that knew only some of them would leave the rest behind as rows nothing can
     * reach.
     *
     * @return how many rows went.
     */
    int removeAll(final LmdbWriter writer, final SimpleDb db, final byte[] pathwayKey) {
        int removed = 0;
        for (final byte marker :
                new byte[]{CHANGE_MARKER, USAGE_MARKER, NODE_MARKER, TRACE_MARKER, COUNTS_MARKER}) {
            removed += db.deletePrefixed(writer, prefixOf(pathwayKey, marker));
        }
        return removed;
    }

    private static ByteBuffer prefixOf(final byte[] pathwayKey, final byte marker) {
        final ByteBuffer prefix = ByteBuffer.allocateDirect(pathwayKey.length + 1);
        prefix.put(pathwayKey).put(marker).flip();
        return prefix;
    }

    private static long idOfKey(final ByteBuffer key) {
        return key.getLong(key.limit() - Long.BYTES);
    }
}
