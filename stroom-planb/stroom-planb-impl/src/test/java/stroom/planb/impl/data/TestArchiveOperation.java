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

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.fs.ArchiveOperation;
import stroom.planb.impl.fs.LocalArchive;
import stroom.planb.impl.fs.SharedFileStoreOperationContext;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestArchiveOperation {

    private static final int SHARD_INDEX = 0;
    private static final SimpleDuration SEVEN_DAYS = SimpleDuration.builder()
            .time(7).timeUnit(TimeUnit.DAYS).build();
    private static final SimpleDuration CHECK_INTERVAL = SimpleDuration.builder()
            .time(16).timeUnit(TimeUnit.HOURS).build();

    @TempDir
    Path tempDir;

    private SharedFileStorePublisher publisher;
    private ArchiveOperation archiveOperation;
    private Path sharedShardsDocDir;

    @BeforeEach
    void setUp() throws IOException {
        publisher = mock(SharedFileStorePublisher.class);
        archiveOperation = new ArchiveOperation(
                new LocalArchive(publisher, new PlanBPaths(tempDir.resolve("local_state"))));
        sharedShardsDocDir = tempDir.resolve("shards").resolve("doc-uuid");
        Files.createDirectories(sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX)));
    }

    // -----------------------------------------------------------------------
    // isDue — disabled / null archival
    // -----------------------------------------------------------------------

    /**
     * Archival cannot be switched off. Queries read archive buckets rather than the holding area, so a
     * store that stopped archiving would accumulate data nothing could find — {@code ArchivalSettings}
     * therefore forces {@code enabled} true and the UI has no toggle.
     */
    @Test
    void isDue_cannotBeDisabled() {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        assertThat(doc.getSettings() instanceof HasSharedFileStore s
                   && s.getSharedFileStore().getArchival().isEnabled())
                .as("settings force archival on")
                .isTrue();
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isDue — archival runs every merge cycle, so the check interval does not gate it
    // -----------------------------------------------------------------------

    /**
     * Archival claims the lock on its own account whenever it is enabled, regardless of when it last ran.
     * Delaying it delays queries, since the archive is the queryable copy.
     */
    @Test
    void isDue_trueWheneverArchivalIsEnabled() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX))
                .as("never run before")
                .isTrue();

        writeCompactionMarker(Instant.now());
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX))
                .as("just compacted — still due, the marker only gates compaction")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // The marker gates COMPACTION, not archival
    // -----------------------------------------------------------------------

    /** Compaction is a full env copy, so it is throttled by the check interval and records the marker. */
    @Test
    void run_compactsAndWritesMarker_whenNothingCompactedYet() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        archiveOperation.run(ctx(doc, shard));

        verify(shard).compact();
        final Path lastFile = compactMarkerFile();
        assertThat(lastFile).exists();
        assertThat(Instant.parse(Files.readString(lastFile, StandardCharsets.UTF_8).trim())).isNotNull();
    }

    /** Archival still runs, but a full env copy is not repeated within the interval. */
    @Test
    void run_archivesButSkipsCompaction_whenCompactedRecently() throws IOException {
        writeCompactionMarker(Instant.now());
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        assertThat(archiveOperation.run(ctx(doc, shard)))
                .as("archival ran")
                .isTrue();
        verify(shard, never()).compact();
    }

    /** Nothing archived means nothing to reclaim, so neither compaction nor the marker happens. */
    @Test
    void run_writesNoMarker_whenNothingArchived() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);

        assertThat(archiveOperation.run(ctx(doc, shard))).isFalse();
        assertThat(compactMarkerFile()).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // run — return value
    // -----------------------------------------------------------------------

    private Path compactMarkerFile() {
        return sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.COMPACTION_LAST_FILE_NAME);
    }

    @Test
    void run_returnsTrue_whenShardsArchived() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        assertThat(archiveOperation.run(ctx(doc, shard))).isTrue();
    }

    @Test
    void run_returnsFalse_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);
        assertThat(archiveOperation.run(ctx(doc, shard))).isFalse();
    }

    // -----------------------------------------------------------------------
    // run — publisher interactions
    // -----------------------------------------------------------------------

    @Test
    void run_callsPushArchiveForEachShard() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final List<String> dateLabels = List.of("2025-05-18", "2025-05-19");
        final SharedFileStoreShard shard = mockShardWithDirs(doc, dateLabels);

        archiveOperation.run(ctx(doc, shard));

        // Verify push was called once for each date-labelled shard dir (2 labels → 2 calls).
        verify(publisher, times(2)).pushArchive(any(), any(Integer.class), any());
    }

    @Test
    void run_doesNotCallPublisher_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);

        archiveOperation.run(ctx(doc, shard));

        verify(publisher, never()).pushArchive(any(), any(Integer.class), any());
    }

    @Test
    void run_callsCompact_whenShardsArchived() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        archiveOperation.run(ctx(doc, shard));

        verify(shard).compact();
    }

    @Test
    void run_doesNotCallCompact_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);

        archiveOperation.run(ctx(doc, shard));

        verify(shard, never()).compact();
    }

    // -----------------------------------------------------------------------
    // run — archival disabled does nothing
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PlanBDoc docWithArchival(final SimpleDuration duration) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                null,
                                new ArchivalSettings.Builder()
                                        .duration(duration)
                                        .checkInterval(CHECK_INTERVAL)
                                        .granularity(ArchivalGranularity.DAY)
                                        .build()))
                        .build())
                .build();
    }

    private SharedFileStoreOperationContext ctx(final PlanBDoc doc, final SharedFileStoreShard shard) {
        return new SharedFileStoreOperationContext(doc, SHARD_INDEX, shard, sharedShardsDocDir, "lock-name");
    }

    /**
     * Creates a mock SharedFileStoreShard whose runArchival returns the given count (0 = nothing to archive).
     */
    private static SharedFileStoreShard mockShard(final PlanBDoc doc,
                                                  final long count) throws IOException {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.runArchival(any(), any())).thenReturn(count);
        return shard;
    }

    /**
     * Creates a mock SharedFileStoreShard whose runArchival returns a positive count
     * and also creates dated subdirectories in the archiveBaseDir argument so that
     * ArchiveOperation's Files.list() sees real archive shards.
     */
    @SuppressWarnings("unchecked")
    private static SharedFileStoreShard mockShardWithDirs(final PlanBDoc doc,
                                                          final List<String> dateLabels) throws IOException {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.runArchival(any(), any())).thenAnswer((InvocationOnMock inv) -> {
            final Path archiveBaseDir = inv.getArgument(1, Path.class);
            for (final String label : dateLabels) {
                Files.createDirectories(archiveBaseDir.resolve(label));
            }
            return (long) dateLabels.size();
        });
        return shard;
    }

    private void writeCompactionMarker(final Instant timestamp) throws IOException {
        writeCompactionMarker(timestamp.toString());
    }

    private void writeCompactionMarker(final String content) throws IOException {
        final Path lastFile = sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.COMPACTION_LAST_FILE_NAME);
        Files.writeString(lastFile, content, StandardCharsets.UTF_8);
    }
}
