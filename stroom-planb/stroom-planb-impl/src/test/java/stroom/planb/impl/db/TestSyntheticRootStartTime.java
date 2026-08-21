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
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A synthesized root's start time must not move as spans arrive.
 *
 * <p>The archive bucket for a trace is labelled from its root's start time. A rootless trace gets a root
 * synthesized by {@code buildRootFromStats}, so if that start time tracked the latest span end the trace
 * would be re-bucketed on every cycle — fragments scattered across buckets, stale copies left in the
 * earlier ones. The earliest span start is fixed by the data already received, so it is stable.
 */
class TestSyntheticRootStartTime {

    private static final ByteBufferFactoryImpl BBF = new ByteBufferFactoryImpl();
    private static final ByteBuffers BB = new ByteBuffers(BBF);

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ABSENT_ROOT_SPAN = "1111111111111111";

    private static final Instant EARLY = Instant.parse("2024-01-10T00:00:00.000Z");
    private static final Instant EARLY_END = Instant.parse("2024-01-10T01:00:00.000Z");
    private static final Instant LATE = Instant.parse("2024-01-12T00:00:00.000Z");
    private static final Instant LATE_END = Instant.parse("2024-01-12T01:00:00.000Z");

    @Test
    void syntheticRootStartsAtTheEarliestSpanStart(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        mergeBatch(dbDir, doc, tempDir, "b1", child("2222222222222222", EARLY), EARLY, EARLY_END);

        assertThat(rootStart(dbDir, doc))
                .as("earliest span start, not its end")
                .isEqualTo(NanoTimeUtil.fromInstant(EARLY));
    }

    @Test
    void syntheticRootStartDoesNotMoveWhenALaterSpanArrives(@TempDir final Path tempDir)
            throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final PlanBDoc doc = buildDoc();

        mergeBatch(dbDir, doc, tempDir, "b1", child("2222222222222222", EARLY), EARLY, EARLY_END);
        mergeBatch(dbDir, doc, tempDir, "b2", child("3333333333333333", LATE), LATE, LATE_END);

        assertThat(rootStart(dbDir, doc))
                .as("still the earliest start, so the trace keeps one bucket")
                .isEqualTo(NanoTimeUtil.fromInstant(EARLY));
    }

    /** The consequence: one bucket for the whole rootless trace, labelled by its earliest span start. */
    @Test
    void rootlessTraceArchivesToASingleBucket(@TempDir final Path tempDir) throws IOException {
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildDoc();

        mergeBatch(dbDir, doc, tempDir, "b1", child("2222222222222222", EARLY), EARLY, EARLY_END);
        mergeBatch(dbDir, doc, tempDir, "b2", child("3333333333333333", LATE), LATE, LATE_END);

        try (final TraceDb db = TraceDb.create(dbDir, BB, BBF, doc, false)) {
            db.publish(Instant.parse("2024-06-01T00:00:00.000Z"), archiveBaseDir);
        }

        assertThat(listSubDirs(archiveBaseDir).stream().map(p -> p.getFileName().toString()).toList())
                .as("one bucket, the earliest span's day")
                .containsExactly("2024-01-10");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    // Spans must arrive via merge: only merge queues the root rebuild that synthesizes the root.
    private static void mergeBatch(final Path dbDir,
                                   final PlanBDoc doc,
                                   final Path tempDir,
                                   final String batchName,
                                   final SpanKey key,
                                   final Instant start,
                                   final Instant end) throws IOException {
        final Path batch = Files.createDirectory(tempDir.resolve(batchName));
        try (final TraceDb db = TraceDb.create(batch, BB, BBF, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(key, span(start, end))));
        }
        try (final TraceDb db = TraceDb.create(dbDir, BB, BBF, doc, false)) {
            db.merge(batch);
            db.mergeComplete();
        }
    }

    private static Object rootStart(final Path dbDir, final PlanBDoc doc) {
        try (final TraceDb db = TraceDb.create(dbDir, BB, BBF, doc, true)) {
            final List<TraceRoot> roots = db.findTraces(new FindTraceCriteria(
                    new PageRequest(0, 100), null, null, SimpleDuration.ZERO)).getValues();
            assertThat(roots).hasSize(1);
            assertThat(roots.getFirst().isOrphan()).as("root was synthesized").isTrue();
            return roots.getFirst().getStartTime();
        }
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

    // Production builds keys with SpanKey.create(span), so the start time is on the key as well as the
    // value — the key is what orders siblings, and what the earliest-start scan reads.
    private static SpanKey child(final String spanId, final Instant start) {
        return SpanKey.builder()
                .traceId(TRACE_A)
                .parentSpanId(ABSENT_ROOT_SPAN)
                .spanId(spanId)
                .startTimeUnixNano(Long.toString(NanoTimeUtil.fromInstant(start).toEpochNanos()))
                .build();
    }

    private static SpanValue span(final Instant start, final Instant end) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(end))
                .insertTime(NanoTimeUtil.fromInstant(start))
                .build();
    }

    private static List<Path> listSubDirs(final Path dir) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (final var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).sorted().forEach(result::add);
        }
        return result;
    }
}
