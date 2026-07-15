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
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceRootField;
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
