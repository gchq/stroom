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

package stroom.planb.impl.fs;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two things {@link HoldingAreaMergeStrategy} does to a holding shard once its batches are in:
 * sweeping it, and draining it into the archive buckets. Both are driven with a mocked shard, so
 * these cover the strategy's own decisions rather than LMDB.
 */
class TestHoldingAreaMergeStrategy {

    private static final int SHARD_INDEX = 0;
    private static final SimpleDuration SEVEN_DAYS = SimpleDuration.builder()
            .time(7).timeUnit(TimeUnit.DAYS).build();
    private static final SimpleDuration CHECK_INTERVAL = SimpleDuration.builder()
            .time(16).timeUnit(TimeUnit.HOURS).build();

    @TempDir
    Path tempDir;

    private static final String DOC_UUID = "doc-uuid";

    private SharedFileStorePublisher publisher;
    private HoldingAreaMergeStrategy strategy;
    private Path sharedHoldingDocDir;

    @BeforeEach
    void setUp() throws IOException {
        publisher = mock(SharedFileStorePublisher.class);
        final PlanBPaths planBPaths = new PlanBPaths(tempDir.resolve("local_state"));
        strategy = new HoldingAreaMergeStrategy(
                mock(ByteBuffers.class),
                mock(ByteBufferFactory.class),
                () -> PlanBConfig.builder().build(),
                planBPaths,
                publisher,
                new LocalArchive(publisher, planBPaths));
        sharedHoldingDocDir = sharedRoot()
                .resolve(PlanBConstants.HOLDING_DIR_NAME)
                .resolve(DOC_UUID);
        Files.createDirectories(sharedHoldingDocDir.resolve(PlanBConstants.formatShardIndex(SHARD_INDEX)));
    }

    // -----------------------------------------------------------------------
    // sweep
    // -----------------------------------------------------------------------

    @Test
    void sweep_reportsModified_whenRecordsDeleted() {
        final MergeShard shard = mock(MergeShard.class);
        when(shard.runRetention(any())).thenReturn(3L);
        assertThat(strategy.sweep(ctx(doc()), shard)).isTrue();
    }

    @Test
    void sweep_reportsUnmodified_whenNothingDeleted() {
        final MergeShard shard = mock(MergeShard.class);
        when(shard.runRetention(any())).thenReturn(0L);
        assertThat(strategy.sweep(ctx(doc()), shard)).isFalse();
    }

    // -----------------------------------------------------------------------
    // drain — the compaction marker gates COMPACTION, not the drain
    // -----------------------------------------------------------------------

    /** Compaction is a full env copy, so it is throttled by the check interval and records a marker. */
    @Test
    void drain_compactsAndWritesMarker_whenNothingCompactedYet() throws IOException {
        final MergeShard shard = mockShardWithBuckets(List.of("2025-05-18"));

        strategy.drain(ctx(doc()), shard);

        verify(shard).compact();
        final Path marker = compactMarkerFile();
        assertThat(marker).exists();
        assertThat(Instant.parse(Files.readString(marker, StandardCharsets.UTF_8).trim())).isNotNull();
    }

    /** The drain still runs, but a full env copy is not repeated within the interval. */
    @Test
    void drain_runsButSkipsCompaction_whenCompactedRecently() throws IOException {
        writeCompactionMarker(Instant.now());
        final MergeShard shard = mockShardWithBuckets(List.of("2025-05-18"));

        assertThat(strategy.drain(ctx(doc()), shard)).isTrue();
        verify(shard, never()).compact();
    }

    /** Nothing drained means nothing to reclaim, so neither compaction nor the marker happens. */
    @Test
    void drain_writesNoMarker_whenNothingDrained() throws IOException {
        final MergeShard shard = mockShard(0);

        assertThat(strategy.drain(ctx(doc()), shard)).isFalse();
        assertThat(compactMarkerFile()).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // drain — publisher interactions
    // -----------------------------------------------------------------------

    @Test
    void drain_pushesEachDatedBucket() throws IOException {
        final MergeShard shard = mockShardWithBuckets(List.of("2025-05-18", "2025-05-19"));

        strategy.drain(ctx(doc()), shard);

        verify(publisher, times(2)).pushArchive(any(), any(Integer.class), any());
    }

    @Test
    void drain_pushesNothing_whenNothingDrained() throws IOException {
        final MergeShard shard = mockShard(0);

        strategy.drain(ctx(doc()), shard);

        verify(publisher, never()).pushArchive(any(), any(Integer.class), any());
        verify(shard, never()).compact();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MergeContext ctx(final PlanBDoc doc) {
        return new MergeContext(doc, SHARD_INDEX, "lock-name", true);
    }

    private Path sharedRoot() {
        return tempDir.resolve("shared");
    }

    private Path compactMarkerFile() {
        return sharedHoldingDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.COMPACTION_LAST_FILE_NAME);
    }

    /** A shard whose store has nothing to move on. */
    private static MergeShard mockShard(final long count) throws IOException {
        final MergeShard shard = mock(MergeShard.class);
        when(shard.runArchival(any(), any())).thenReturn(count);
        return shard;
    }

    /**
     * A shard whose store fills one delta dir per date label under the base dir it is handed, so
     * {@link LocalArchive#pushAll} finds real deltas to publish.
     */
    private static MergeShard mockShardWithBuckets(final List<String> dateLabels) throws IOException {
        final MergeShard shard = mock(MergeShard.class);
        when(shard.runArchival(any(), any())).thenAnswer((InvocationOnMock inv) -> {
            final Path base = inv.getArgument(1, Path.class);
            for (final String label : dateLabels) {
                Files.createDirectories(base.resolve(label));
            }
            return (long) dateLabels.size();
        });
        return shard;
    }

    private void writeCompactionMarker(final Instant timestamp) throws IOException {
        Files.writeString(compactMarkerFile(), timestamp.toString(), StandardCharsets.UTF_8);
    }

    private PlanBDoc doc() {
        return PlanBDoc.builder()
                .uuid(DOC_UUID)
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                sharedRoot().toAbsolutePath().toString()))
                        .granularity(ArchivalGranularity.DAY)
                        .holdingArea(new HoldingAreaSettings.Builder()
                                .completionGrace(SEVEN_DAYS)
                                .compactionFrequency(CHECK_INTERVAL)
                                .build())
                        .build())
                .build();
    }
}
