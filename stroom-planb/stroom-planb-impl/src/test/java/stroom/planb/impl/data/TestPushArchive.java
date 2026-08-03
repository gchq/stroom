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

package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.io.ByteSize;
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the two invariants {@code SharedFileStorePublisher.pushArchive} exists to uphold.
 *
 * <p><b>No LMDB env on the shared mount.</b> Merging a new batch into an existing bucket copies the
 * bucket down to local staging, merges there, and copies the finished file back up — so the shared
 * store only ever sees whole-file copies and renames. The observable signature of a violation is a
 * {@code lock.mdb} appearing in the shared archive tree, which is what LMDB creates when an env is
 * opened.
 *
 * <p><b>The bucket dir is never renamed away.</b> Publication renames the data file <em>within</em> the
 * live bucket dir rather than swapping the dir itself, so there is no instant at which the bucket is
 * absent. That matters because {@code recoverOrphaned} only scans the {@code shards/} tree, so an
 * interrupted dir swap under {@code archive/} would orphan the bucket permanently.
 *
 * <p>That repeated pushes for one date <em>merge</em> rather than overwrite is covered by
 * {@code TestArchiveOldData.pushArchive_mergesRepeatedBatchesForSameDay_ratherThanOverwriting}.
 */
class TestPushArchive {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_INDEX = 0;
    private static final String DAY_LABEL = "2024-01-10";
    private static final Instant DAY = Instant.parse("2024-01-10T09:00:00.000Z");

    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TRACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ROOT_SPAN = "1111111111111111";

    @TempDir
    Path tempDir;

    private Path shared;
    private Path localState;
    private PlanBDoc doc;
    private SharedFileStorePublisher publisher;

    @BeforeEach
    void setUp() throws IOException {
        shared = Files.createDirectories(tempDir.resolve("shared"));
        localState = tempDir.resolve("local_state");
        doc = buildSharedDoc(shared);
        publisher = newPublisher();
    }

    // -----------------------------------------------------------------------
    // Rule #1 — nothing opens an LMDB env on the shared mount
    // -----------------------------------------------------------------------

    @Test
    void mergeIntoExisting_opensNoLmdbEnvOnTheSharedStore() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        // A lock.mdb anywhere under archive/ means an env was opened there.
        assertThat(findFiles(shared.resolve(PlanBConstants.ARCHIVE_DIR_NAME),
                PlanBConstants.LOCK_FILE_NAME)).isEmpty();
        // Also assert the merge really merged, so a regression to overwrite-instead-of-merge cannot
        // hide behind the rule #1 assertion above.
        assertThat(archivedTraceIds()).containsExactly(TRACE_A, TRACE_B);
    }

    @Test
    void mergeIntoExisting_leavesOnlyDataAndVersionInTheBucket() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        assertThat(listNames(bucketDir()))
                .containsExactlyInAnyOrder(PlanBConstants.DATA_FILE_NAME, PlanBConstants.VERSION_FILE_NAME);
    }

    // -----------------------------------------------------------------------
    // The bucket dir is never renamed away
    // -----------------------------------------------------------------------

    /**
     * Proves the live bucket directory itself is reused rather than swapped: a sentinel file written
     * into it survives a subsequent push. The old {@code pushDir} protocol renamed the dir to
     * {@code .old_} and deleted it, which would take the sentinel with it.
     */
    @Test
    void repeatedPush_reusesTheBucketDirRatherThanSwappingIt() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        final Path sentinel = bucketDir().resolve("sentinel.txt");
        Files.writeString(sentinel, "still here");

        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        assertThat(sentinel).exists();
    }

    @Test
    void push_createsNoTempOrOldDirsInTheArchiveTree() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        assertThat(listNames(bucketDir().getParent()))
                .as("no .tmp_ / .old_ siblings of the bucket dir")
                .containsExactly(DAY_LABEL);
    }

    // -----------------------------------------------------------------------
    // Bucket completeness and the reader's re-sync signal
    // -----------------------------------------------------------------------

    @Test
    void firstPush_writesDataAndVersion() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));

        assertThat(bucketDir().resolve(PlanBConstants.DATA_FILE_NAME)).exists();
        assertThat(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME)).exists();
        assertThat(archivedTraceIds()).containsExactly(TRACE_A);
    }

    /** ArchiveStoreShard re-syncs its local copy when .version changes, so each push must bump it. */
    @Test
    void eachPush_bumpsTheVersionMarker() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        final String first = Files.readString(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME));

        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));
        final String second = Files.readString(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void pushWithNoDataFile_doesNotCreateABucket() throws IOException {
        // An empty staged dir has no data.mdb, so there is nothing to publish.
        publisher.pushArchive(doc, SHARD_INDEX,
                new StagedArchive(DAY_LABEL, Files.createDirectories(tempDir.resolve("empty"))));

        assertThat(bucketDir()).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // Failure leaves the live bucket intact
    // -----------------------------------------------------------------------

    /**
     * A merge that fails must not damage the bucket already on the shared store: the merge happens on
     * a local copy, and nothing is published until it succeeds.
     */
    @Test
    void failedMerge_leavesTheLiveBucketUntouched() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        final String versionBefore = Files.readString(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME));

        // Merging from a dir with no LMDB env fails once a bucket already exists.
        final Path broken = Files.createDirectories(tempDir.resolve("broken"));
        assertThatThrownBy(() -> publisher.pushArchive(doc, SHARD_INDEX, new StagedArchive(DAY_LABEL, broken)))
                .isInstanceOf(Exception.class);

        assertThat(Files.readString(bucketDir().resolve(PlanBConstants.VERSION_FILE_NAME)))
                .isEqualTo(versionBefore);
        assertThat(archivedTraceIds()).containsExactly(TRACE_A);
        assertThat(listNames(bucketDir()))
                .containsExactlyInAnyOrder(PlanBConstants.DATA_FILE_NAME, PlanBConstants.VERSION_FILE_NAME);
    }

    // -----------------------------------------------------------------------
    // Orphaned temp data files in the bucket dir
    // -----------------------------------------------------------------------

    /**
     * A JVM kill between the copy up and the rename orphans a bucket-sized temp file inside the live
     * bucket dir, and nothing else would ever clean it ({@code recoverOrphaned} scans only
     * {@code shards/}, as does {@code SharedFileStoreCleaner}). The next push must sweep it.
     */
    @Test
    void push_sweepsOrphanedTempDataLeftByAnInterruptedPush() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));

        // Simulate the crash: a temp file left behind from a push that never reached the rename.
        final Path orphan = bucketDir().resolve(PlanBConstants.DATA_TMP_FILE_NAME + "_stale_uid");
        Files.writeString(orphan, "partially copied bucket");

        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        assertThat(orphan).doesNotExist();
        assertThat(listNames(bucketDir()))
                .containsExactlyInAnyOrder(PlanBConstants.DATA_FILE_NAME, PlanBConstants.VERSION_FILE_NAME);
        // The sweep must not disturb the data itself.
        assertThat(archivedTraceIds()).containsExactly(TRACE_A, TRACE_B);
    }

    // -----------------------------------------------------------------------
    // Local staging hygiene
    // -----------------------------------------------------------------------

    @Test
    void staging_isEmptyAfterASuccessfulPush() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch2", TRACE_B));

        assertThat(listNames(stagingDir())).isEmpty();
    }

    @Test
    void staging_isEmptyAfterAFailedPush() throws IOException {
        publisher.pushArchive(doc, SHARD_INDEX, stagedBatch("batch1", TRACE_A));

        final Path broken = Files.createDirectories(tempDir.resolve("broken"));
        assertThatThrownBy(() -> publisher.pushArchive(doc, SHARD_INDEX, new StagedArchive(DAY_LABEL, broken)))
                .isInstanceOf(Exception.class);

        assertThat(listNames(stagingDir())).isEmpty();
    }

    /** Staging dirs left by a crashed JVM are dead on arrival, so construction clears them. */
    @Test
    void construction_clearsStaleStagingDirs() throws IOException {
        final Path stale = Files.createDirectories(stagingDir().resolve("merge_stale"));
        Files.writeString(stale.resolve(PlanBConstants.DATA_FILE_NAME), "junk");

        newPublisher();

        assertThat(listNames(stagingDir())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SharedFileStorePublisher newPublisher() {
        // pushArchive does not use NodeInfo, so null is fine here.
        return new SharedFileStorePublisher(
                null, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, new StatePaths(localState));
    }

    private Path stagingDir() {
        return new StatePaths(localState).getArchiveStagingDir();
    }

    private Path bucketDir() {
        return shared.resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(DAY_LABEL);
    }

    /** Builds a local single-trace archive batch env, as archiveOldData would produce. */
    private StagedArchive stagedBatch(final String dirName, final String traceId) throws IOException {
        final Path dir = Files.createDirectories(tempDir.resolve(dirName));
        try (final TraceDb db = TraceDb.create(dir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, false)) {
            db.write(writer -> db.insert(writer, new SpanKV(rootKey(traceId), span(DAY))));
        }
        return new StagedArchive(DAY_LABEL, dir);
    }

    private List<String> archivedTraceIds() {
        try (final TraceDb archive = TraceDb.create(
                bucketDir(), BYTE_BUFFERS, BYTE_BUFFER_FACTORY, doc, true)) {
            final TracesResultPage page = archive.findTraces(new FindTraceCriteria(
                    new PageRequest(0, 1000), null, null, SimpleDuration.ZERO));
            return page.getValues().stream().map(TraceRoot::getTraceId).sorted().toList();
        }
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

    private static SpanKey rootKey(final String traceId) {
        return SpanKey.builder().traceId(traceId).parentSpanId("").spanId(ROOT_SPAN).build();
    }

    private static SpanValue span(final Instant start) {
        return SpanValue.builder()
                .startTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .endTimeUnixNano(NanoTimeUtil.fromInstant(start))
                .insertTime(NanoTimeUtil.fromInstant(start))
                .build();
    }

    private static List<String> listNames(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        try (final Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> names.add(p.getFileName().toString()));
        }
        return names;
    }

    private static List<Path> findFiles(final Path root, final String fileName) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.getFileName().toString().equals(fileName)).toList();
        }
    }
}
