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
import stroom.pathways.shared.TracesResultPage;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceRootField;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.TimeRange;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@code TraceDb.findTraces} reports an <em>exact</em> total under a time-range
 * filter, so the traces-list pager shows the true count rather than {@code "?"}.
 *
 * <p>Before the fix, a time-range query that filled the requested page reported
 * {@code exact=false} (the code stopped at {@code offset+length} and could not tell whether
 * more matches existed). It now counts matches exactly via a key-only walk of the
 * chronologically ordered START_TIME secondary index, so the total is always exact —
 * independent of page size and of the sort column.
 */
class TestFindTracesCount {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final String ROOT_SPAN = "1111111111111111";

    /**
     * Time range covering all inserted traces, with a page far smaller than the match count:
     * the total must be the full count and exact (the old {@code exact=false} case).
     */
    @Test
    void exactTotalUnderTimeRange_smallerPage(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        // 5 traces, one per day 2024-01-10 .. 2024-01-14 at 09:00.
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                for (int day = 10; day <= 14; day++) {
                    insertRoot(db, writer, traceId(day),
                            Instant.parse("2024-01-" + day + "T09:00:00.000Z"));
                }
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = db.findTraces(criteria(
                    new PageRequest(0, 2),                                 // page smaller than 5
                    null,
                    timeRange("2024-01-01T00:00:00.000Z", "2024-02-01T00:00:00.000Z")));

            assertThat(page.getValues()).hasSize(2);                       // page is full
            assertThat(page.getPageResponse().getTotal()).isEqualTo(5L);   // true total, not offset+2
            assertThat(page.getPageResponse().isExact()).isTrue();         // not "?"
        }
    }

    /**
     * A time range covering only a subset of traces must report exactly the subset size.
     */
    @Test
    void exactTotalUnderTimeRange_subsetWindow(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                for (int day = 10; day <= 14; day++) {
                    insertRoot(db, writer, traceId(day),
                            Instant.parse("2024-01-" + day + "T09:00:00.000Z"));
                }
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            // Window covers the 10th, 11th, 12th only (13th 09:00 falls after the 'to' bound).
            final TracesResultPage page = db.findTraces(criteria(
                    new PageRequest(0, 2),
                    null,
                    timeRange("2024-01-10T00:00:00.000Z", "2024-01-13T00:00:00.000Z")));

            assertThat(page.getPageResponse().getTotal()).isEqualTo(3L);
            assertThat(page.getPageResponse().isExact()).isTrue();
        }
    }

    /**
     * The count is order-independent: it always uses the START_TIME index even when the page
     * is sorted by another column (here TRACE_ID, which is served by the primary DBI).
     */
    @Test
    void exactTotalIsSortIndependent(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                for (int day = 10; day <= 14; day++) {
                    insertRoot(db, writer, traceId(day),
                            Instant.parse("2024-01-" + day + "T09:00:00.000Z"));
                }
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = db.findTraces(criteria(
                    new PageRequest(0, 2),
                    List.of(new CriteriaFieldSort(TraceRootField.TRACE_ID, false, false)),
                    timeRange("2024-01-01T00:00:00.000Z", "2024-02-01T00:00:00.000Z")));

            assertThat(page.getPageResponse().getTotal()).isEqualTo(5L);
            assertThat(page.getPageResponse().isExact()).isTrue();
        }
    }

    /**
     * Regression guard: with no time range the O(1) LMDB stat count is still used and exact.
     */
    @Test
    void exactTotalWithoutTimeRange_usesStatCount(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> {
                for (int day = 10; day <= 14; day++) {
                    insertRoot(db, writer, traceId(day),
                            Instant.parse("2024-01-" + day + "T09:00:00.000Z"));
                }
            });
        }

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = db.findTraces(criteria(
                    new PageRequest(0, 2), null, null));

            assertThat(page.getPageResponse().getTotal()).isEqualTo(5L);
            assertThat(page.getPageResponse().isExact()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void insertRoot(final TraceDb db,
                                   final LmdbWriter writer,
                                   final String traceId,
                                   final Instant start) {
        final SpanKey rootKey = SpanKey.builder()
                .traceId(traceId).parentSpanId("").spanId(ROOT_SPAN).build();
        final SpanValue value = SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(start))
                .build();
        db.insert(writer, new SpanKV(rootKey, value));
    }

    private static FindTraceCriteria criteria(final PageRequest pageRequest,
                                              final List<CriteriaFieldSort> sortList,
                                              final TimeRange timeRange) {
        return new FindTraceCriteria(pageRequest, sortList, null, null, null, SimpleDuration.ZERO, timeRange);
    }

    private static TimeRange timeRange(final String from, final String to) {
        return new TimeRange("test", from, to);
    }

    /** 16-byte (32 hex char) trace id derived from a day number. */
    private static String traceId(final int day) {
        return String.format("%032d", day);
    }

    private static PlanBDoc buildDoc() {
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
