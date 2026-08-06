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
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
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
 * Whether an orphan trace — one whose root span never arrived — can be listed and opened.
 *
 * <p>This matters because queries now read archive buckets only, never the holding area. If an orphan
 * were unlistable in a bucket its spans would be carried on the shared store forever and never be
 * viewable, so the question decides whether orphan handling needs new machinery or just less latency.
 */
class TestOrphanTraceVisibility {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final Instant CUTOFF = Instant.parse("2024-02-01T00:00:00.000Z");
    private static final Instant OLD = Instant.parse("2024-01-15T12:00:00.000Z");
    private static final String OLD_DAY = "2024-01-15";

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_SPAN = "1111111111111111";
    private static final String CHILD_SPAN = "2222222222222222";

    /**
     * The published bucket is the case that decides the design question. {@code archiveOldData} stages
     * orphan spans with no root entry, but {@code pushArchive} merges that delta into the bucket and calls
     * {@code mergeComplete}, which synthesizes the flagged root there.
     */
    @Test
    void publishedBucket_listsAndOpensAnOrphan(@TempDir final Path tempDir) throws IOException {
        final Path shared = Files.createDirectory(tempDir.resolve("shared"));
        final Path dbDir = Files.createDirectory(tempDir.resolve("db"));
        final Path archiveBaseDir = Files.createDirectory(tempDir.resolve("archive"));
        final PlanBDoc doc = buildSharedDoc(shared);

        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.merge(batchWithOrphanSpan(tempDir, doc));
            db.mergeComplete();
        }
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            assertThat(db.archiveOldData(CUTOFF, ArchivalGranularity.DAY, archiveBaseDir))
                    .as("the orphan's spans are swept by insert time")
                    .isGreaterThanOrEqualTo(1);
        }

        final List<Path> staged = listSubDirs(archiveBaseDir);
        assertThat(staged).hasSize(1);
        assertThat(staged.getFirst().getFileName().toString()).isEqualTo(OLD_DAY);

        // What archiveOldData stages carries no trace-root entry of its own.
        try (final TraceDb delta = TraceDb.create(
                staged.getFirst(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            assertThat(roots(delta)).as("the staged delta has no root entry").isEmpty();
        }

        // pushArchive does not use NodeInfo, so null is fine here.
        final SharedFileStorePublisher publisher = new SharedFileStorePublisher(
                null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, new PlanBPaths(tempDir.resolve("local_state")));
        publisher.pushArchive(doc, 0, new StagedArchive(OLD_DAY, staged.getFirst()));

        final Path bucket = shared
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(0))
                .resolve(OLD_DAY);
        try (final TraceDb archive = TraceDb.create(bucket, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final List<TraceRoot> found = roots(archive);
            assertThat(found).as("the published bucket must list the orphan").hasSize(1);
            assertThat(found.getFirst().getTraceId()).isEqualTo(TRACE_A);
            assertThat(found.getFirst().isOrphan()).isTrue();

            final Trace trace = archive.getTrace(HexStringUtil.decode(TRACE_A));
            assertThat(trace.root()).isNull();
            assertThat(spanCount(trace)).as("and open it").isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * A batch env holding one parentless span, for the shard to {@code merge}. It has to arrive that way
     * rather than by a bare {@code insert}: only {@code merge} queues a root rebuild for every traceId it
     * touches, so an inserted orphan would never get the synthesized root this test is about.
     */
    private static Path batchWithOrphanSpan(final Path tempDir, final PlanBDoc doc) throws IOException {
        final Path batch = Files.createDirectory(tempDir.resolve("batch_" + UUID.randomUUID()));
        try (final TraceDb db = TraceDb.create(batch, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(childKey(), span(OLD, OLD))));
        }
        return batch;
    }

    private static List<TraceRoot> roots(final TraceDb db) {
        final TracesResultPage page = db.findTraces(new FindTraceCriteria(
                new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
        return page.getValues();
    }

    private static int spanCount(final Trace trace) {
        return trace.getParentSpanIdMap().values().stream().mapToInt(List::size).sum();
    }

    private static PlanBDoc buildSharedDoc(final Path sharedPath) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .sharedFileStore(new SharedFileStoreSettings(
                                1, sharedPath.toAbsolutePath().toString()))
                        .build())
                .build();
    }

    private static SpanKey childKey() {
        return SpanKey.builder().traceId(TRACE_A).parentSpanId(ROOT_SPAN).spanId(CHILD_SPAN).build();
    }

    private static SpanValue span(final Instant start, final Instant insert) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(insert))
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
