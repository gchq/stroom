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
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AbstractRange;
import stroom.pathways.shared.pathway.AbstractSet;
import stroom.pathways.shared.pathway.AbstractValue;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.DoubleSet;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.LongSet;
import stroom.pathways.shared.pathway.MutationType;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathUse;
import stroom.pathways.shared.pathway.Paths;
import stroom.pathways.shared.pathway.Pathway;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.pathways.shared.pathway.StringSet;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.dao.trace.PathwaysDb.SimpleDb;
import stroom.planb.impl.fs.ShardQueue;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Holds each model to what it has been given lately.
 *
 * <p>A trace only ever widens a model, so an envelope left to itself ends up admitting everything and
 * saying nothing — and a change stops meaning anything, because nothing falls outside it any more.
 * About once a day every model is narrowed back to what has actually been seen within its document's
 * narrowing window, from the day by day account kept beside it as traces were applied.
 *
 * <p>Not a job of its own. Narrowing a shard means holding it, and something already holds a shard to
 * apply traces to it — so this is called from inside that hold when a marker beside the shard says a
 * day has passed. Nothing here finds documents, takes locks or decides when to run.
 *
 * <p>What that restores is the worth of a change. After a narrowing, a constraint widening
 * says something happened that has not happened in the window — which is the thing worth being told
 * about, and what an envelope that only grows can never say.
 */
@Singleton
public class PathwayNarrower {

    private static final String OCCURRENCES = "occurrences";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwayNarrower.class);

    /**
     * Whether a shard whose model was last narrowed at {@code lastRun} is due another narrowing.
     *
     * <p>Once per day, at midnight, rather than a day after the last run: a daily pass that creeps
     * forward by however long it waited to be noticed ends up running at a different time every day,
     * and when each model was last held to its window stops being something anyone can say.
     *
     * <p>The day is the one the window is measured in — see {@link #oldestDayInWindow}, which counts
     * whole days from the epoch — so a model narrowed at any point on a day is not narrowed again
     * until the next one begins.
     *
     * @param lastRun when this shard was last narrowed, or null where it never has been, which is
     *                also what a marker that cannot be read says.
     */
    static boolean narrowingDue(final Instant lastRun, final Instant now) {
        return lastRun == null
               || lastRun.toEpochMilli() / TraceProcessor.MILLIS_PER_DAY
                  < now.toEpochMilli() / TraceProcessor.MILLIS_PER_DAY;
    }

    private final PathwaySerde pathwaySerde;
    private final MutationLog mutationLog;
    private final ByteBuffers byteBuffers;

    @Inject
    public PathwayNarrower(final PathwaySerde pathwaySerde,
                           final MutationLog mutationLog,
                           final ByteBuffers byteBuffers) {
        this.pathwaySerde = pathwaySerde;
        this.mutationLog = mutationLog;
        this.byteBuffers = byteBuffers;
    }

    /**
     * The first day whose values still count. A trace is filed under the day it ran and a whole day
     * is kept or dropped, so a window of seven days reaches back into the day seven days ago and keeps
     * all of it — today and the seven before it.
     *
     * <p>Values are kept a day at a time, so a day is as fine as a window goes. A shorter one is held
     * to a day rather than quietly rounded to whatever day it happens to land in, which for a window of
     * a minute would be yesterday when the pass runs at midnight and today when it runs at noon.
     */
    static int oldestDayInWindow(final PathwaysDoc doc, final Instant now) {
        final Instant aDayAgo = now.minusMillis(TraceProcessor.MILLIS_PER_DAY);
        final Instant asked = SimpleDurationUtil.minus(now, doc.getNarrowingWindow());
        final Instant oldest = asked.isAfter(aDayAgo)
                ? aDayAgo
                : asked;
        return (int) (oldest.toEpochMilli() / TraceProcessor.MILLIS_PER_DAY);
    }

    // Returns whether anything changed, which is what decides whether the shard is worth pushing back.
    // Not private so that what it does to a shard can be tested without a cluster lock and a document
    // store standing behind it.
    boolean narrow(final Path localDir, final int oldestDay, final MessageReceiver messages) {
        boolean changed = false;
        try (final PathwaysDb pathwaysDb = PathwaysDb.create(localDir, byteBuffers, false)) {
            try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                // Read every model first: the same transaction is written to below, and walking a
                // table while writing to it is not something to rely on.
                final List<byte[]> keys = new ArrayList<>();
                final List<Pathway> pathways = new ArrayList<>();
                pathwaysDb.getPathways().iterate(writer.getWriteTxn(), (key, value) -> {
                    keys.add(copyOf(key));
                    pathways.add(pathwaySerde.readPathway(value));
                });

                // Walked once for the whole shard rather than once per model. Keyed by model, so
                // looking one up is a lookup and not another walk — the table holds a row per model
                // per day, and asking it that many times over would grow with the square of how many
                // models a shard holds.
                final Map<String, Window> byPathway = valuesInWindow(writer, pathwaysDb, oldestDay);

                for (int i = 0; i < keys.size(); i++) {
                    final byte[] key = keys.get(i);
                    changed |= narrowPathway(writer, pathwaysDb, key, pathways.get(i),
                            byPathway.getOrDefault(asText(key), Window.NOTHING), oldestDay, messages);
                }

                // Forgetting is still a change to the shard. Were it not said so, a pass that only
                // forgot would report nothing changed, the shard would be thrown away rather than
                // pushed, and the same entries would be forgotten again every night for ever.
                changed |= forgetOldTraces(writer, pathwaysDb, oldestDay) > 0;
                writer.commit();
            }
        }
        return changed;
    }

    private boolean narrowPathway(final LmdbWriter writer,
                                   final PathwaysDb pathwaysDb,
                                   final byte[] pathwayKey,
                                   final Pathway pathway,
                                   final Window window,
                                   final int oldestDay,
                                   final MessageReceiver messages) {
        if (window.values().isEmpty()) {
            // Nothing has been seen in the window. Narrowing to nothing would say this pathway admits
            // nothing, which is not what no evidence means — it is a model nothing has exercised, and
            // retiring what has gone quiet is a separate question from how wide the rest should be.
            //
            // Dropping a day is still a change to the shard. Were it not said so, a pass that only
            // dropped days would report nothing changed, the shard would be thrown away rather than
            // pushed, and the same days would be dropped again on every run for ever.
            return window.dropped() > 0;
        }

        final NanoTime now = NanoTimeUtil.fromInstant(Instant.now());
        final List<PathwayMutation> mutations = new ArrayList<>();
        final PathNode root = narrowNode(pathway.getRoot(), window.values(), now, false, mutations);
        final Paths paths = stillTaken(pathway, oldestDay, now, mutations);
        if (mutations.isEmpty()) {
            return window.dropped() > 0;
        }

        final Pathway narrowed = pathway.copy().root(root).paths(paths).build();
        byteBuffers.useBytes(pathwayKey, (Consumer<ByteBuffer>) keyByteBuffer ->
                pathwaySerde.writePathway(narrowed, 0, valueByteBuffer ->
                        pathwaysDb.getPathways().insert(writer, keyByteBuffer, valueByteBuffer)));

        // In the same history as the changes traces made, and in the same transaction as the model
        // they describe. No trace caused these, so they name no store.
        final PathwayHistory history = mutationLog.history(writer, pathwaysDb, pathwayKey);
        mutationLog.append(writer, pathwaysDb, pathwayKey, mutations, root, -1, history);
        // Written here rather than at the end of the pass: the narrowing takes one model at a time
        // and is finished with each before it moves on.
        mutationLog.writeCounts(writer, pathwaysDb, pathwayKey, history);

        // And said to the document's feed, a line per row, the way a trace says what it widened. A
        // reader watching the feed sees the narrowing put back what the days took out, in the same words.
        mutations.forEach(mutation -> messages.log(Severity.INFO, () -> describe(mutation)));
        return true;
    }

    private static String describe(final PathwayMutation mutation) {
        final List<String> where = NullSafe.list(mutation.getNodePath());
        return switch (mutation.getType()) {
            // What it holds now sits where a widening puts it, so the two read alike, and what it
            // gave up follows in brackets — which is the half worth reading on a narrowing. Bracketed
            // rather than joined by an arrow: a range writes itself as one bound arrow the other, and
            // a second arrow between two of them could not be told from the ones inside them.
            case CONSTRAINT_NARROWED -> "Narrowing constraint: " + where + " "
                                        + mutation.getConstraint() + " " + mutation.getNewValue()
                                        + " (was " + mutation.getOldValue() + ")";
            case NODE_RETIRED -> "Retiring node: " + where;
            case NODE_REVIVED -> "Reviving node: " + where;
            case PATH_DROPPED -> "Dropping path: " + where;
            default -> mutation.getType().getDisplayValue() + ": " + where;
        };
    }

    /**
     * Forgets the traces this shard applied too long ago to be worth remembering.
     *
     * <p>A trace is remembered so that one handed over twice is applied once. What bounds that is the
     * same window the model is held to: a trace older than it cannot teach the model anything that
     * survives, because its observations fall outside the window and the next pass takes back whatever
     * it widened. So a trace that old will not be applied again whether it is remembered or not.
     *
     * <p>An entry carrying no time is forgotten too. Nothing can say how old it is, so nothing can
     * ever decide it has aged out, and keeping it would keep it for ever — which is the one answer
     * that is certainly wrong for a table whose whole purpose is to be bounded.
     *
     * @return how many were forgotten.
     */
    private int forgetOldTraces(final LmdbWriter writer,
                                final PathwaysDb pathwaysDb,
                                final int oldestDay) {
        final long oldest = (long) oldestDay * TraceProcessor.MILLIS_PER_DAY;
        final SimpleDb db = pathwaysDb.getProcessingStatus();
        final List<byte[]> expired = new ArrayList<>();
        db.iterate(writer.getWriteTxn(), (key, value) -> {
            if (value == null
                || value.remaining() < Long.BYTES
                || value.getLong(value.position()) < oldest) {
                expired.add(copyOf(key));
            }
        });

        expired.forEach(key -> byteBuffers.useBytes(key,
                (Consumer<ByteBuffer>) keyByteBuffer -> db.delete(writer, keyByteBuffer)));
        LOGGER.debug(() -> LogUtil.message("Forgot {} applied trace(s) older than day {}",
                expired.size(), oldestDay));
        return expired.size();
    }

    // Everything the window still covers, folded into one account per model, node and constraint. Days
    // that have fallen out of it are deleted on the way past, which is the only thing that bounds what
    // this table holds.
    private Map<String, Window> valuesInWindow(final LmdbWriter writer,
                                               final PathwaysDb pathwaysDb,
                                               final int oldestDay) {
        final SimpleDb db = pathwaysDb.getObservedValues();
        final Map<String, Map<String, Map<String, ConstraintValue>>> byPathway = new LinkedHashMap<>();
        final Map<String, Integer> droppedBy = new LinkedHashMap<>();
        final List<byte[]> expired = new ArrayList<>();

        db.iterate(writer.getWriteTxn(), (key, value) -> {
            final String owner = ownerOf(key);
            if (dayOf(key) < oldestDay) {
                expired.add(copyOf(key));
                droppedBy.merge(owner, 1, Integer::sum);
                return;
            }
            final Map<String, Map<String, ConstraintValue>> window =
                    byPathway.computeIfAbsent(owner, uuid -> new LinkedHashMap<>());
            pathwaySerde.readObservedValues(value).forEach((nodeUuid, constraints) -> {
                final Map<String, ConstraintValue> node =
                        window.computeIfAbsent(nodeUuid, uuid -> new LinkedHashMap<>());
                constraints.forEach((name, seen) -> node.merge(name, seen, ObservedValues::add));
            });
        });

        expired.forEach(key -> byteBuffers.useBytes(key,
                (Consumer<ByteBuffer>) keyByteBuffer -> db.delete(writer, keyByteBuffer)));

        final Map<String, Window> windows = new LinkedHashMap<>();
        byPathway.forEach((owner, values) ->
                windows.put(owner, new Window(values, droppedBy.getOrDefault(owner, 0))));
        droppedBy.forEach((owner, dropped) ->
                windows.computeIfAbsent(owner, key -> new Window(Map.of(), dropped)));
        return windows;
    }

    // The model a row belongs to. A key is that model's key, a nought, then the day, so what leads it
    // is what says whose it is.
    private static String ownerOf(final ByteBuffer key) {
        final byte[] bytes = new byte[key.remaining() - 1 - Integer.BYTES];
        key.duplicate().get(bytes);
        return asText(bytes);
    }

    private static int dayOf(final ByteBuffer key) {
        return key.getInt(key.limit() - Integer.BYTES);
    }

    // Key bytes as something that can be looked up in a map, which raw arrays cannot be.
    private static String asText(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // What the window still covers, and how many days were dropped on the way to working it out.
    private record Window(Map<String, Map<String, ConstraintValue>> values, int dropped) {

        private static final Window NOTHING = new Window(Map.of(), 0);
    }

    // The paths still being taken, with those nothing has taken within the window left out. A pathway
    // gathers one-off shapes faster than anything else: a path taken once and never again stays in the
    // table for good, and the table is how a reader sees what the work does.
    //
    // The shapes those paths were built from are left where they are. They are named by position, and
    // renumbering them would mean rewriting every path that survived to say the same thing.
    private static Paths stillTaken(final Pathway pathway,
                                    final int oldestDay,
                                    final NanoTime now,
                                    final List<PathwayMutation> mutations) {
        final Paths paths = pathway.getPaths();
        if (paths == null) {
            return null;
        }
        final List<PathUse> kept = new ArrayList<>();
        for (final PathUse path : NullSafe.list(paths.getPaths())) {
            if (withinWindow(path.getLastTraceTime(), oldestDay)) {
                kept.add(path);
            } else {
                // No trace caused this, so it names none. The trace that created the path is on the
                // path's own row, and putting it here would have the change counts read it as the
                // work of a trace.
                mutations.add(new PathwayMutation(0L, now, now, -1, null, null,
                        NullSafe.list(pathway.getRoot() == null
                                ? null
                                : pathway.getRoot().getNodePath()),
                        null, null, MutationType.PATH_DROPPED, false, null, null));
            }
        }
        return kept.size() == NullSafe.list(paths.getPaths()).size()
                ? paths
                : new Paths(paths.getNodes(), paths.getSteps(), kept, paths.getSources());
    }

    private static boolean withinWindow(final NanoTime time, final int oldestDay) {
        return time != null && (int) (time.toEpochMillis() / TraceProcessor.MILLIS_PER_DAY) >= oldestDay;
    }

    // Every node of a model, narrowed to what the window still covers. A constraint the window says
    // nothing about is left alone: no values were given to it lately, which is a node that has gone
    // quiet rather than a constraint that should stop allowing what it allows.
    private static PathNode narrowNode(final PathNode node,
                                       final Map<String, Map<String, ConstraintValue>> window,
                                       final NanoTime time,
                                       final boolean parentRetired,
                                       final List<PathwayMutation> mutations) {
        if (node == null) {
            return null;
        }
        final Map<String, ConstraintValue> seen = window.get(node.getUuid());
        final Map<String, Constraint> constraints = new LinkedHashMap<>(NullSafe.map(node.getConstraints()));
        if (seen != null) {
            constraints.replaceAll((name, constraint) -> {
                final ConstraintValue narrowed = narrowedTo(constraint.getValue(), seen.get(name));
                if (narrowed == null) {
                    return constraint;
                }
                mutations.add(new PathwayMutation(0L, time, time, -1, null, null, node.getNodePath(),
                        node.getUuid(), name, MutationType.CONSTRAINT_NARROWED, constraint.isOptional(),
                        constraint.getValue(), narrowed));
                return new Constraint(name, narrowed, constraint.isOptional(), constraint.getTimesUsed(),
                        constraint.getLastUsedTime());
            });
        }

        // Settled before the children are reached, because whether this node is still part of the work
        // decides the same question for everything under it.
        final NanoTime retiredTime = retirement(node, seen, time, parentRetired, mutations);

        final List<PathNode> children = new ArrayList<>();
        NullSafe.list(node.getChildren())
                .forEach(child -> children.add(
                        narrowNode(child, window, time, retiredTime != null, mutations)));

        return node.copy()
                .children(children)
                .constraints(constraints)
                .retiredTime(retiredTime)
                .build();
    }

    // Whether a node is still part of the work, answered from how many times the window saw it run. A
    // trace that reaches a node's parent records how many times it carried the node, nought included,
    // so a node every trace in the window went without is one the work no longer does.
    //
    // A node under one that has itself gone is gone too. Its own count cannot say so: a count is
    // recorded by whatever reached its parent, and within the window nothing did, so it is left with
    // no account of itself at all and would otherwise stay lit beneath a parent that is not.
    //
    // Keeps what it was given where nothing was recorded and the parent is still part of the work:
    // that is a parent nothing reached either, which says nothing about this node one way or the
    // other.
    private static NanoTime retirement(final PathNode node,
                                       final Map<String, ConstraintValue> seen,
                                       final NanoTime time,
                                       final boolean parentRetired,
                                       final List<PathwayMutation> mutations) {
        final ConstraintValue occurrences = seen == null
                ? null
                : seen.get(OCCURRENCES);
        if (occurrences == null && !parentRetired) {
            return node.getRetiredTime();
        }
        final boolean ranAtAll = !parentRetired && occurrences != null && ranAtAll(occurrences);
        if (!ranAtAll && !node.isRetired()) {
            mutations.add(change(node, time, MutationType.NODE_RETIRED));
            return time;
        }
        if (ranAtAll && node.isRetired()) {
            mutations.add(change(node, time, MutationType.NODE_REVIVED));
            return null;
        }
        return node.getRetiredTime();
    }

    // Whether the window ever saw this node run. Anything that is not a count says it did: a value
    // this does not understand is no reason to retire a node.
    private static boolean ranAtAll(final ConstraintValue occurrences) {
        return switch (occurrences) {
            case final IntegerValue value -> value.getValue() > 0;
            case final IntegerRange range -> range.getMax() > 0;
            default -> true;
        };
    }

    private static PathwayMutation change(final PathNode node,
                                          final NanoTime time,
                                          final MutationType type) {
        return new PathwayMutation(0L, time, time, -1, null, null, node.getNodePath(), node.getUuid(),
                null, type, false, null, null);
    }

    // What the model should hold instead, or null where the window gives no reason to change it.
    // Checked rather than assumed: what the window saw is summarised as it arrives and gives up where
    // it cannot summarise, so it can come back saying anything at all — and writing that in would
    // leave the model admitting everything, which is the state this whole pass exists to undo.
    private static ConstraintValue narrowedTo(final ConstraintValue held, final ConstraintValue seen) {
        if (seen == null || held == null || Objects.equals(held, seen) || seen instanceof AnyTypeValue) {
            return null;
        }
        if (held instanceof AnyTypeValue) {
            // Already admits everything, and something made it that way: a value of a type the
            // constraint had not held before, or a name the configuration says not to learn. Putting a
            // bound back would start rejecting what it has been accepting.
            return null;
        }
        return switch (held) {
            case final NanoTimeRange range -> narrowedTime(range, seen);
            case final NanoTimeValue value -> within(value.getValue(), value.getValue(), seen)
                    ? seen
                    : null;
            case final AbstractRange<?> range -> covers(range.getMin(), range.getMax(), seen)
                    ? seen
                    : null;
            case final AbstractSet<?> set -> narrowedToSome(set, seen);
            // One value is as narrow as a constraint goes. Whatever the window admitted, either this
            // admits it too and nothing has changed, or it does not and there is nothing to narrow to.
            case final AbstractValue<?> ignored -> null;
            default -> null;
        };
    }

    // What the model holds as named values, against what the window saw. Answered by which values each
    // admits rather than by how each is written: the window summarises numbers as a span, so a span
    // and the named values it covers are the same answer written two ways, and writing one over the
    // other would record a narrowing that narrowed nothing.
    //
    // A span the named values do not cover in full is no narrowing either, however it reads. Values of
    // 1, 35 and 39 seen at 1 and at 39 come back as 1 to 39, and holding the model to that would have
    // it start admitting the thirty seven numbers between — a widening, done by the pass that exists
    // to undo widenings.
    // Kept in the shape the model already holds rather than the shape the window was summarised in.
    // The account kept beside the model writes whole numbers down as a range whether or not the model
    // holds a set, so handing that account straight back would turn a set into a range on its way
    // through, which says the same thing about the values it names and a different thing about the
    // gaps between them.
    private static ConstraintValue narrowedToSome(final AbstractSet<?> held, final ConstraintValue seen) {
        final Set<?> values = held.getSet();
        final Set<Object> admitted = admitted(seen, values.size());
        if (admitted == null || !values.containsAll(admitted) || admitted.size() >= values.size()) {
            return null;
        }
        // Taken in the order the model holds them rather than the order they were seen, so a set that
        // loses one value still reads as the set it was less that value.
        final Set<Object> kept = new LinkedHashSet<>();
        values.forEach(value -> {
            if (admitted.contains(value)) {
                kept.add(value);
            }
        });
        return sameShape(held, kept);
    }

    private static ConstraintValue sameShape(final AbstractSet<?> held, final Set<Object> kept) {
        return switch (held) {
            case final IntegerSet ignored -> new IntegerSet(asSet(kept));
            case final LongSet ignored -> new LongSet(asSet(kept));
            case final DoubleSet ignored -> new DoubleSet(asSet(kept));
            case final StringSet ignored -> new StringSet(asSet(kept));
            default -> null;
        };
    }

    // Safe because every value came out of the set being rebuilt, so each is already of its type.
    @SuppressWarnings("unchecked")
    private static <T> Set<T> asSet(final Set<Object> values) {
        return (Set<T>) values;
    }

    // Every value something admits, or null where that cannot be listed. A span wider than the limit
    // asked for is given up on rather than walked: the caller is asking whether its own values cover
    // the span, and too many of them to count is already an answer of no.
    private static Set<Object> admitted(final ConstraintValue seen, final int limit) {
        return switch (seen) {
            case final AbstractSet<?> set -> new LinkedHashSet<>(set.getSet());
            case final AbstractValue<?> value -> Set.of(value.getValue());
            case final IntegerRange range -> span(range.getMin(), range.getMax(), limit);
            case final LongRange range -> span(range.getMin(), range.getMax(), limit);
            case null, default -> null;
        };
    }

    private static Set<Object> span(final Number min, final Number max, final int limit) {
        if (min == null || max == null || max.longValue() < min.longValue()) {
            return null;
        }
        final Set<Object> values = new LinkedHashSet<>();
        final boolean ints = min instanceof Integer;
        for (long value = min.longValue(); value <= max.longValue(); value++) {
            if (values.size() >= limit) {
                return null;
            }
            values.add(ints
                    ? (Object) (int) value
                    : (Object) value);
        }
        return values;
    }

    // A duration with one end left unasserted keeps it that way: only the end being learnt is brought
    // in, and only when the window's own is inside it. Writing the window's range in whole would close
    // the end the configuration asked never to be closed.
    private static ConstraintValue narrowedTime(final NanoTimeRange held, final ConstraintValue seen) {
        final NanoTime low = lowTime(seen);
        final NanoTime high = highTime(seen);
        if (low == null || high == null) {
            return null;
        }
        if (held.getMin() == null && held.getMax() == null) {
            return null;
        }
        if (held.getMin() == null) {
            return high.isLessThan(held.getMax())
                    ? new NanoTimeRange(null, high)
                    : null;
        }
        if (held.getMax() == null) {
            return low.isGreaterThan(held.getMin())
                    ? new NanoTimeRange(low, null)
                    : null;
        }
        return within(held.getMin(), held.getMax(), seen)
                ? seen
                : null;
    }

    private static NanoTime lowTime(final ConstraintValue seen) {
        return switch (seen) {
            case final NanoTimeValue value -> value.getValue();
            case final NanoTimeRange range -> range.getMin();
            case null, default -> null;
        };
    }

    private static NanoTime highTime(final ConstraintValue seen) {
        return switch (seen) {
            case final NanoTimeValue value -> value.getValue();
            case final NanoTimeRange range -> range.getMax();
            case null, default -> null;
        };
    }

    private static boolean within(final NanoTime low, final NanoTime high, final ConstraintValue seen) {
        return switch (seen) {
            case final NanoTimeValue value -> !value.getValue().isLessThan(low)
                                              && !value.getValue().isGreaterThan(high);
            case final NanoTimeRange range -> range.getMin() != null
                                              && range.getMax() != null
                                              && !range.getMin().isLessThan(low)
                                              && !range.getMax().isGreaterThan(high);
            case null, default -> false;
        };
    }

    private static boolean covers(final Object low, final Object high, final ConstraintValue seen) {
        final List<Object> ends = seen instanceof final AbstractRange<?> range
                ? List.of(range.getMin(), range.getMax())
                : new ArrayList<>(valuesOf(seen));
        return !ends.isEmpty()
               && ends.stream().allMatch(end -> end instanceof Comparable
                                                && compare(low, end) <= 0
                                                && compare(high, end) >= 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(final Object a, final Object b) {
        return ((Comparable) a).compareTo(b);
    }

    private static Set<Object> valuesOf(final ConstraintValue seen) {
        return switch (seen) {
            case final AbstractSet<?> set -> new LinkedHashSet<>(set.getSet());
            case final AbstractValue<?> value -> Set.of(value.getValue());
            case null, default -> Set.of();
        };
    }

    private static byte[] copyOf(final ByteBuffer byteBuffer) {
        final byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.duplicate().get(bytes);
        return bytes;
    }
}
