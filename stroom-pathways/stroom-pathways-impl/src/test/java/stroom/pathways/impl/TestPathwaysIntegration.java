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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.otel.trace.Span;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code trace-pathways-pending} DBI trigger
 * introduced as part of the completion-based pathways processing mechanism.
 *
 * <p>These tests replace the old event-driven {@code PathwaysEntityEventHandler}
 * tests; they verify directly on {@link TraceDb} that:
 * <ul>
 *   <li>Inserting a root span (empty {@code parentSpanId}) populates the
 *       pending DBI.</li>
 *   <li>Inserting only child spans does NOT populate the pending DBI.</li>
 *   <li>The grace-period cutoff used by
 *       {@link PathwaysProcessor#exec()} correctly selects only
 *       traces whose root-span end time has elapsed the threshold.</li>
 * </ul>
 */
class TestPathwaysIntegration {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TracesDoc buildTracesDoc(final String name) {
        return TracesDoc.tracesBuilder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(2, null, null))
                        .maxStoreSize(ByteSize.ofMebibytes(10).getBytes())
                        .retention(new RetentionSettings.Builder()
                                .duration(SimpleDuration.ZERO)
                                .enabled(false)
                                .build())
                        .build())
                .build();
    }

    private Span buildRootSpan(final String traceId,
                               final String spanId,
                               final Instant endTime) {
        return Span.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId("")   // empty → root span
                .name("root-span")
                .endTimeUnixNano(NanoTimeUtil.fromInstant(endTime))
                .build();
    }

    private Span buildChildSpan(final String traceId,
                                final String spanId,
                                final String parentSpanId) {
        return Span.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)   // non-empty → child span
                .name("child-span")
                .build();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Inserting a root span (empty {@code parentSpanId}) into {@link TraceDb}
     * must populate the {@code trace-pathways-pending} DBI with the trace-ID
     * and the root span's end-time-epoch-ms.
     */
    @Test
    void testRootSpanPopulatesPendingDbi() throws Exception {
        final TracesDoc doc = buildTracesDoc("traces_root_span_test");
        final Path dbPath = Files.createDirectories(tempDir.resolve("db_root"));

        final Instant rootEnd = Instant.now().minusSeconds(30);
        final Span rootSpan = buildRootSpan(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                rootEnd);

        try (final TraceDb db = TraceDb.create(dbPath, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            try (final LmdbWriter writer = db.createWriter()) {
                db.insert(writer, rootSpan);
                writer.commit();
            }

            final List<byte[]> eligible = new ArrayList<>();
            db.iterateRootsMergedBefore(Long.MAX_VALUE, eligible::add);

            assertThat(eligible).hasSize(1);
        }
    }

    /**
     * Inserting only child spans (non-empty {@code parentSpanId}) must NOT
     * populate the {@code trace-pathways-pending} DBI — only root-span
     * insertions trigger it.
     */
    @Test
    void testChildSpanDoesNotPopulatePendingDbi() throws Exception {
        final TracesDoc doc = buildTracesDoc("traces_child_span_test");
        final Path dbPath = Files.createDirectories(tempDir.resolve("db_child"));

        final Span childSpan = buildChildSpan(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "a2fb4a1d1a96d312",
                "00f067aa0ba902b7");

        try (final TraceDb db = TraceDb.create(dbPath, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            try (final LmdbWriter writer = db.createWriter()) {
                db.insert(writer, childSpan);
                writer.commit();
            }

            final List<byte[]> pending = new ArrayList<>();
            db.iterateRootsMergedBefore(Long.MAX_VALUE, pending::add);

            assertThat(pending).isEmpty();
        }
    }

    /**
     * The grace-period cutoff is based on the wall-clock time at which the
     * root span was merged into the store (not the span's declared end time).
     * Traces merged after the cutoff are still within the grace period and
     * should NOT be returned by {@link TraceDb#iterateRootsMergedBefore}.
     *
     * <p>This test verifies:
     * <ul>
     *   <li>A cutoff strictly in the past (before insertion) → 0 eligible traces.</li>
     *   <li>A cutoff of {@link Long#MAX_VALUE} → all inserted root traces eligible.</li>
     * </ul>
     */
    @Test
    void testGracePeriodCutoffFiltersEligibleTraces() throws Exception {
        final TracesDoc doc = buildTracesDoc("traces_cutoff_test");
        final Path dbPath = Files.createDirectories(tempDir.resolve("db_cutoff"));

        // Record a cutoff time BEFORE insertion so that the merge times of both
        // traces will be >= cutoff (i.e. both still within the grace period).
        final long cutoffBeforeInsert = Instant.now().toEpochMilli() - 1_000L;

        final Span rootA = buildRootSpan(
                "aaaabbbbccccdddd0000000011112222",
                "1111222233334444",
                Instant.now().minusSeconds(30));

        final Span rootB = buildRootSpan(
                "ccccddddeeeeeeee0000000011112222",
                "5555666677778888",
                Instant.now().minusSeconds(30));

        try (final TraceDb db = TraceDb.create(dbPath, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            try (final LmdbWriter writer = db.createWriter()) {
                db.insert(writer, rootA);
                db.insert(writer, rootB);
                writer.commit();
            }

            // Cutoff strictly before insertion → grace period not yet elapsed for either trace.
            final List<byte[]> stillInGracePeriod = new ArrayList<>();
            db.iterateRootsMergedBefore(cutoffBeforeInsert, stillInGracePeriod::add);
            assertThat(stillInGracePeriod).isEmpty();

            // Long.MAX_VALUE cutoff → all inserted root traces are eligible.
            final List<byte[]> allEligible = new ArrayList<>();
            db.iterateRootsMergedBefore(Long.MAX_VALUE, allEligible::add);
            assertThat(allEligible).hasSize(2);
        }
    }
}
