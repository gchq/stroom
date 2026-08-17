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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceRootField;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for stale secondary-index entries: across a two-cycle re-merge in which a
 * trace deepens, sorting the traces list by depth / services / total-spans must return the
 * trace exactly once. A stranded (stale-valued) sort-index entry shows up as a duplicate row
 * when the query walks that index — the user-visible symptom of the orphaned entries the
 * per-batch merge copy used to leave behind.
 */
class TestTraceRootIndexConsistency {

    private static final ByteBufferFactoryImpl BBF = new ByteBufferFactoryImpl();
    private static final ByteBuffers BB = new ByteBuffers(BBF);

    private static final String TRACE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT = "1111111111111111";
    private static final String CHILD = "2222222222222222";
    private static final String GRAND = "3333333333333333";

    @Test
    void sortIndexesStayDeDupedAcrossTwoCycleReMerge(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            // Cycle 1: batch = root + child (subtree depth 2).
            db.merge(buildBatch(dir, "b1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
            }, doc));
            db.mergeComplete();
            assertNoDuplicateRows(db);

            // Cycle 2: batch = root (re-delivered) + grandchild → merged trace deepens to 3.
            db.merge(buildBatch(dir, "b2", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(GRAND, CHILD), span("grandchild", t)));
            }, doc));
            db.mergeComplete();
            assertNoDuplicateRows(db);
        }
    }

    /** Each field-sorted query walks that field's secondary index; a stale entry duplicates the row. */
    private void assertNoDuplicateRows(final TraceDb db) {
        for (final String sortField : List.of(
                TraceRootField.DEPTH, TraceRootField.SERVICES, TraceRootField.TOTAL_SPANS,
                TraceRootField.TRACE_START, TraceRootField.DURATION, TraceRootField.OPERATION)) {
            final List<String> ids = db.findTraces(new FindTraceCriteria(
                            new PageRequest(0, 100),
                            List.of(new CriteriaFieldSort(sortField, false, false)),
                            null, SimpleDuration.ZERO))
                    .getValues().stream().map(TraceRoot::getTraceId).toList();
            assertThat(ids)
                    .as("sort by '%s' must return the trace exactly once (no stale index entry)", sortField)
                    .containsExactly(TRACE);
        }
    }

    /**
     * A trace's derived fields (depth/services/totalSpans) must stay current when child spans
     * arrive in a later cycle than the root span (the root was processed in an earlier cycle,
     * so it is NOT in this cycle's source roots). Before the fix these froze at the values
     * computed when the root was last seen.
     */
    @Test
    void derivedFieldsStayCurrentWhenChildrenArriveAfterRoot(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            // Cycle 1: root + child → depth 2, services 2, totalSpans 2.
            db.merge(buildBatch(dir, "c1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
            }, doc));
            db.mergeComplete();

            // Cycle 2: grandchild ONLY (no root span) → the merged trace deepens to 3.
            db.merge(buildBatch(dir, "c2", (d, w) ->
                    d.insert(w, new SpanKV(key(GRAND, CHILD), span("grandchild", t))), doc));
            db.mergeComplete();

            final TraceRoot root = storedRoot(db);
            assertThat(root.getDepth()).as("depth").isEqualTo(3);
            assertThat(root.getServices()).as("services").isEqualTo(3);
            assertThat(root.getTotalSpans()).as("totalSpans").isEqualTo(3);
        }
    }

    /**
     * A deep (many-level) trace within the safety valve → depth is exact, computed by the
     * bounded DFS without materialising the trace.
     */
    @Test
    void depthIsExactForDeepTrace(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            // Root + a straight chain of 5 descendants → depth 6.
            db.merge(buildBatch(dir, "deep", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                String parent = ROOT;
                for (int i = 1; i <= 5; i++) {
                    final String spanId = sid(i);
                    d.insert(w, new SpanKV(key(spanId, parent), span("op" + i, t)));
                    parent = spanId;
                }
            }, doc));
            db.mergeComplete();

            final TraceRoot root = storedRoot(db);
            assertThat(root.getDepth()).as("depth").isEqualTo(6);
            assertThat(root.getTotalSpans()).as("totalSpans").isEqualTo(6);
            assertThat(root.getServices()).as("services").isEqualTo(6);
        }
    }

    /**
     * A malformed cyclic trace (CHILD ↔ GRAND) must NOT loop forever: the DFS's path-visited
     * guard skips the back-edge, so it terminates with depth = the longest simple path
     * (ROOT→CHILD→GRAND = 3), while totalSpans (dedup-by-key) and services (distinct names)
     * stay exact.
     */
    @Test
    void cyclicTraceTerminatesAndCountsStayExact(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "cyclic", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("c", t)));
                d.insert(w, new SpanKV(key(GRAND, CHILD), span("g", t)));
                // Back-edge: a span with id CHILD whose parent is GRAND → CHILD ↔ GRAND.
                d.insert(w, new SpanKV(key(CHILD, GRAND), span("c", t)));
            }, doc));
            db.mergeComplete(); // must return (no infinite loop)

            final TraceRoot root = storedRoot(db);
            // Longest simple path ROOT→CHILD→GRAND; the CHILD back-edge from GRAND is skipped.
            assertThat(root.getDepth()).as("depth (longest simple path)").isEqualTo(3);
            // 4 distinct span keys; 3 distinct names (run, c, g — "c" reused).
            assertThat(root.getTotalSpans()).as("totalSpans").isEqualTo(4);
            assertThat(root.getServices()).as("services").isEqualTo(3);
        }
    }

    /** Re-delivering the same spans across cycles must not inflate totalSpans. */
    @Test
    void duplicateSpansAreNotDoubleCounted(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            for (final String batch : List.of("d1", "d2")) {
                db.merge(buildBatch(dir, batch, (d, w) -> {
                    d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                    d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
                }, doc));
                db.mergeComplete();
            }

            assertThat(storedRoot(db).getTotalSpans()).as("totalSpans (deduped)").isEqualTo(2);
        }
    }

    /** The streaming traversal API returns the same root/children as the tree structure. */
    @Test
    void streamingTraversalMatchesStructure(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "s1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
                d.insert(w, new SpanKV(key(GRAND, CHILD), span("grandchild", t)));
            }, doc));
            db.mergeComplete();

            final byte[] traceId = HexStringUtil.decode(TRACE);
            assertThat(db.rootSpan(traceId).map(Span::getSpanId)).contains(ROOT);
            assertThat(db.children(traceId, HexStringUtil.decode(ROOT)).stream().map(Span::getSpanId).toList())
                    .containsExactly(CHILD);
            assertThat(db.children(traceId, HexStringUtil.decode(CHILD)).stream().map(Span::getSpanId).toList())
                    .containsExactly(GRAND);
            assertThat(db.children(traceId, HexStringUtil.decode(GRAND))).isEmpty();
        }
    }

    /** Retention keeps a root until its own end time is older than the cutoff, then drops it. */
    @Test
    void retentionDropsRootOnceItsOwnEndAges(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "r1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
            }, doc));
            db.mergeComplete();

            // Cutoff before the root's own end → root retained.
            db.runRetention(t.minusSeconds(60), false);
            assertThat(traceIds(db)).as("root retained while its end is recent").containsExactly(TRACE);

            // Cutoff after the root's own end → root (and its root span) dropped.
            db.runRetention(t.plusSeconds(60), false);
            assertThat(traceIds(db)).as("aged root dropped").isEmpty();
        }
    }

    /**
     * The age gate (root's own end) vs the old quiet gate: a root whose own end is old is
     * dropped by retention even though the trace is still receiving spans (recent activity),
     * and the late child is left behind as a parentless orphan.
     */
    @Test
    void retentionDropsAgedRootDespiteRecentActivity(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant rootT = Instant.parse("2026-07-10T09:00:00.000Z");   // root ends here (aged)
        final Instant childT = Instant.parse("2026-07-10T11:00:00.000Z");  // later activity
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "r1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", rootT)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", rootT)));
            }, doc));
            db.mergeComplete();

            // A later child keeps the trace active, but the root's own end stays at rootT.
            db.merge(buildBatch(dir, "r2", (d, w) ->
                    d.insert(w, new SpanKV(key(GRAND, CHILD), span("late", childT))), doc));
            db.mergeComplete();
            assertThat(storedRoot(db).getLastActivityMs())
                    .as("trace is still active").isEqualTo(childT.toEpochMilli());

            // Cutoff between the root end and the late child → root dropped despite the recent
            // activity (the gate keys on the root's own end), late child left as an orphan.
            db.runRetention(rootT.plusSeconds(3600), false); // 10:00
            assertThat(traceIds(db)).as("aged root dropped despite activity").isEmpty();
            assertThat(db.get(key(ROOT, ""))).as("aged root span deleted").isNull();
            assertThat(db.get(key(GRAND, CHILD))).as("late child retained as orphan").isNotNull();
        }
    }

    /** lastActivityMs tracks the latest span insert time as later spans arrive. */
    @Test
    void lastActivityAdvancesAsSpansArrive(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t1 = Instant.parse("2026-07-10T09:00:00.000Z");
        final Instant t2 = Instant.parse("2026-07-10T10:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "a1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", t1)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t1)));
            }, doc));
            db.mergeComplete();
            assertThat(storedRoot(db).getLastActivityMs()).isEqualTo(t1.toEpochMilli());

            db.merge(buildBatch(dir, "a2", (d, w) ->
                    d.insert(w, new SpanKV(key(GRAND, CHILD), span("grandchild", t2))), doc));
            db.mergeComplete();
            assertThat(storedRoot(db).getLastActivityMs()).isEqualTo(t2.toEpochMilli());
        }
    }

    /**
     * End time (and hence duration) reflects the LATEST span end across the trace, not the
     * root span's own end — so a trace whose root ended early (e.g. a pump root) but keeps
     * producing later spans still reports a duration spanning those spans.
     */
    @Test
    void endTimeReflectsLatestSpanEnd(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant rootTime = Instant.parse("2026-07-10T09:00:00.000Z");
        final Instant childTime = Instant.parse("2026-07-10T09:05:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "e1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", rootTime)));    // root ends early
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", childTime))); // child ends later
            }, doc));
            db.mergeComplete();

            final TraceRoot root = storedRoot(db);
            assertThat(NanoTimeUtil.toInstant(root.getStartTime()).toEpochMilli())
                    .as("start = root start").isEqualTo(rootTime.toEpochMilli());
            assertThat(NanoTimeUtil.toInstant(root.getEndTime()).toEpochMilli())
                    .as("end = latest span end").isEqualTo(childTime.toEpochMilli());
        }
    }

    /**
     * The root span's <em>own</em> end is persisted as {@code rootEndTime}, distinct from
     * {@code endTime} (the max end across all spans). Their gap is what the UI thresholds on to
     * mark "trailing leaked activity". Reading it back via {@code findTraces} also round-trips the
     * field through {@code TraceRootValueSerde}.
     */
    @Test
    void rootEndTimeIsRootSpanOwnEnd(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant rootTime = Instant.parse("2026-07-10T09:00:00.000Z");
        final Instant childTime = Instant.parse("2026-07-10T09:05:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "re1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", rootTime)));       // root ends early
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("child", childTime))); // span 5 min later
            }, doc));
            db.mergeComplete();

            final TraceRoot root = storedRoot(db);
            assertThat(NanoTimeUtil.toInstant(root.getRootEndTime()).toEpochMilli())
                    .as("rootEndTime = root span's own end").isEqualTo(rootTime.toEpochMilli());
            assertThat(NanoTimeUtil.toInstant(root.getEndTime()).toEpochMilli())
                    .as("endTime = latest span end").isEqualTo(childTime.toEpochMilli());
            assertThat(root.getEndTime().diff(root.getRootEndTime()).getNanos())
                    .as("trailing gap = 5 minutes")
                    .isEqualTo((childTime.toEpochMilli() - rootTime.toEpochMilli()) * 1_000_000L);
        }
    }

    /**
     * Counts are cumulative (total-ever): once counted, {@code totalSpans} is not decremented when
     * a span later ages out of the live shard under retention (Option 2). So the list can show
     * more spans than the live detail retains.
     */
    @Test
    void totalSpansCumulative_notDecrementedByRetention(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant oldT = Instant.parse("2026-07-10T09:00:00.000Z");
        final Instant recentT = Instant.parse("2026-07-10T10:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "c1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("run", recentT)));       // root recent → active
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("oldChild", oldT)));  // old child
                d.insert(w, new SpanKV(key(GRAND, ROOT), span("newChild", recentT))); // recent child
            }, doc));
            db.mergeComplete();
            assertThat(storedRoot(db).getTotalSpans()).as("initial").isEqualTo(3);

            // Age out spans older than a cutoff between old and recent; the trace stays active.
            db.runRetention(oldT.plusSeconds(60), false);

            assertThat(storedRoot(db).getTotalSpans()).as("cumulative (not decremented)").isEqualTo(3);
            assertThat(liveSpanCount(db)).as("live spans after age-out").isEqualTo(2);
        }
    }

    /**
     * A re-delivered child span must not be folded into the stored root twice. The span DBI
     * rejects the duplicate, so the stats accumulator ignores it; the incremental child-update
     * path has to ignore it too or the root's totalSpans (and its TOTAL_SPANS index entry)
     * drifts above the trace's real span count. Written straight to the store rather than via
     * merge/mergeComplete, which would rebuild the root from stats and hide the drift.
     */
    @Test
    void redeliveredChildSpan_notCountedTwice(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.write(w -> {
                db.insert(w, new SpanKV(key(ROOT, ""), span("run", t)));
                db.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t)));
            });
            assertThat(storedRoot(db).getTotalSpans()).as("root + child").isEqualTo(2);

            db.write(w -> db.insert(w, new SpanKV(key(CHILD, ROOT), span("child", t))));

            assertThat(storedRoot(db).getTotalSpans())
                    .as("re-delivered child not counted again").isEqualTo(2);
            assertThat(liveSpanCount(db)).as("live spans").isEqualTo(2);
        }
    }

    /** services counts distinct names cumulatively across cycles (repeat name → no change). */
    @Test
    void servicesCumulative_distinctNamesAcrossCycles(@TempDir final Path dir) throws IOException {
        final PlanBDoc doc = doc();
        final Instant t = Instant.parse("2026-07-10T09:00:00.000Z");
        final Path target = Files.createDirectory(dir.resolve("target"));

        try (final TraceDb db = TraceDb.create(target, BB, BBF, doc, false)) {
            db.merge(buildBatch(dir, "s1", (d, w) -> {
                d.insert(w, new SpanKV(key(ROOT, ""), span("a", t)));
                d.insert(w, new SpanKV(key(CHILD, ROOT), span("b", t)));
            }, doc));
            db.mergeComplete();
            db.merge(buildBatch(dir, "s2", (d, w) -> {
                d.insert(w, new SpanKV(key(GRAND, CHILD), span("a", t)));      // name "a" reused
                d.insert(w, new SpanKV(key(sid(9), CHILD), span("c", t)));     // new name "c"
            }, doc));
            db.mergeComplete();

            final TraceRoot root = storedRoot(db);
            assertThat(root.getTotalSpans()).as("totalSpans").isEqualTo(4);
            assertThat(root.getServices()).as("distinct names a,b,c").isEqualTo(3);
        }
    }

    private int liveSpanCount(final TraceDb db) {
        return db.getTrace(HexStringUtil.decode(TRACE)).getParentSpanIdMap().values().stream()
                .mapToInt(List::size).sum();
    }

    private List<String> traceIds(final TraceDb db) {
        return db.findTraces(new FindTraceCriteria(
                        new PageRequest(0, 100), null, null, SimpleDuration.ZERO))
                .getValues().stream().map(TraceRoot::getTraceId).toList();
    }

    private static String sid(final int n) {
        return String.format("%016x", n);
    }

    private TraceRoot storedRoot(final TraceDb db) {
        return db.findTraces(new FindTraceCriteria(
                        new PageRequest(0, 100), null, null, SimpleDuration.ZERO))
                .getValues().stream()
                .filter(r -> r.getTraceId().equals(TRACE))
                .findFirst().orElseThrow();
    }

    // -------------------------------------------------------------------------

    private Path buildBatch(final Path base,
                            final String name,
                            final BiConsumer<TraceDb, LmdbWriter> load,
                            final PlanBDoc doc) throws IOException {
        final Path p = Files.createDirectory(base.resolve(name));
        try (final TraceDb db = TraceDb.create(p, BB, BBF, doc, false)) {
            db.write(w -> load.accept(db, w));
        }
        return p;
    }

    private static SpanValue span(final String name, final Instant t) {
        return SpanValue.builder()
                .name(name)
                .startTimeUnixNano(NanoTimeUtil.fromInstant(t))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(t))
                .insertTime(NanoTimeUtil.fromInstant(t))
                .build();
    }

    private static SpanKey key(final String spanId, final String parentSpanId) {
        return SpanKey.builder().traceId(TRACE).parentSpanId(parentSpanId).spanId(spanId).build();
    }

    private static PlanBDoc doc() {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .build())
                .build();
    }
}
