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
import stroom.planb.impl.fs.ArchiveOperation;
import stroom.planb.impl.fs.SharedFileStoreOperationContext;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.impl.fs.StagedArchive;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
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

    @TempDir
    Path tempDir;

    private SharedFileStorePublisher publisher;
    private ArchiveOperation archiveOperation;
    private Path sharedShardsDocDir;

    @BeforeEach
    void setUp() throws IOException {
        publisher = mock(SharedFileStorePublisher.class);
        archiveOperation = new ArchiveOperation(publisher);
        sharedShardsDocDir = tempDir.resolve("shards").resolve("doc-uuid");
        Files.createDirectories(sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX)));
    }

    // -----------------------------------------------------------------------
    // isDue — disabled / null archival
    // -----------------------------------------------------------------------

    @Test
    void isDue_archivalDisabled_returnsFalse() {
        final PlanBDoc doc = docWithArchival(false, SEVEN_DAYS);
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    @Test
    void isDue_archivalNull_returnsFalse() {
        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.TEMPORAL_STATE)
                .build();
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isDue — no last-run file (never run)
    // -----------------------------------------------------------------------

    @Test
    void isDue_noLastRunFile_returnsTrue() {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isDue — file present, interval not yet elapsed
    // -----------------------------------------------------------------------

    @Test
    void isDue_lastRunTooRecent_returnsFalse() throws IOException {
        writeLastRunFile(Instant.now());
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        // 10% of 7 days = 16.8 hours — running now should not be due
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isDue — file present, interval has elapsed
    // -----------------------------------------------------------------------

    @Test
    void isDue_lastRunIntervalElapsed_returnsTrue() throws IOException {
        // Write a last-run 2 days ago. 10% of 7 days = ~16.8 hours — well overdue.
        writeLastRunFile(Instant.now().minusSeconds(2 * 24 * 3600));
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isDue — corrupt file content
    // -----------------------------------------------------------------------

    @Test
    void isDue_corruptLastRunFile_returnsTrue() throws IOException {
        writeLastRunFile("not-a-timestamp");
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // run — writes last-run file
    // -----------------------------------------------------------------------

    @Test
    void run_writesLastRunFile() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);
        archiveOperation.run(ctx(doc, shard));

        final Path lastFile = sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.ARCHIVAL_LAST_FILE_NAME);
        assertThat(lastFile).exists();
        final String content = Files.readString(lastFile, StandardCharsets.UTF_8).trim();
        assertThat(Instant.parse(content)).isNotNull();
    }

    @Test
    void run_thenIsDue_returnsFalse() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);
        archiveOperation.run(ctx(doc, shard));
        assertThat(archiveOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // run — return value
    // -----------------------------------------------------------------------

    @Test
    void run_returnsTrue_whenShardsArchived() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        assertThat(archiveOperation.run(ctx(doc, shard))).isTrue();
    }

    @Test
    void run_returnsFalse_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);
        assertThat(archiveOperation.run(ctx(doc, shard))).isFalse();
    }

    @Test
    void run_returnsFalse_whenNotDue() throws IOException {
        // Write a recent last-run so isDue returns false.
        writeLastRunFile(Instant.now());
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        assertThat(archiveOperation.run(ctx(doc, shard))).isFalse();
    }

    // -----------------------------------------------------------------------
    // run — publisher interactions
    // -----------------------------------------------------------------------

    @Test
    void run_callsPushArchiveForEachShard() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final List<String> dateLabels = List.of("2025-05-18", "2025-05-19");
        final SharedFileStoreShard shard = mockShardWithDirs(doc, dateLabels);

        archiveOperation.run(ctx(doc, shard));

        // Verify push was called once for each date-labelled shard dir (2 labels → 2 calls).
        verify(publisher, times(2)).pushArchive(any(), any(Integer.class), any());
    }

    @Test
    void run_doesNotCallPublisher_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);

        archiveOperation.run(ctx(doc, shard));

        verify(publisher, never()).pushArchive(any(), any(Integer.class), any());
    }

    @Test
    void run_callsCompact_whenShardsArchived() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShardWithDirs(doc, List.of("2025-05-18"));

        archiveOperation.run(ctx(doc, shard));

        verify(shard).compact();
    }

    @Test
    void run_doesNotCallCompact_whenNoShardsToArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(true, SEVEN_DAYS);
        final SharedFileStoreShard shard = mockShard(doc, 0);

        archiveOperation.run(ctx(doc, shard));

        verify(shard, never()).compact();
    }

    // -----------------------------------------------------------------------
    // run — archival disabled does nothing
    // -----------------------------------------------------------------------

    @Test
    void run_archivalDisabled_doesNotArchive() throws IOException {
        final PlanBDoc doc = docWithArchival(false, SEVEN_DAYS);
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);

        final boolean result = archiveOperation.run(ctx(doc, shard));

        assertThat(result).isFalse();
        verify(shard, never()).archiveOldData(any(), any());
        verify(publisher, never()).pushArchive(any(), any(Integer.class), any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PlanBDoc docWithArchival(final boolean enabled, final SimpleDuration duration) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                null,
                                new ArchivalSettings.Builder()
                                        .enabled(enabled)
                                        .duration(duration)
                                        .granularity(ArchivalGranularity.DAY)
                                        .build()))
                        .build())
                .build();
    }

    private SharedFileStoreOperationContext ctx(final PlanBDoc doc, final SharedFileStoreShard shard) {
        return new SharedFileStoreOperationContext(doc, SHARD_INDEX, shard, sharedShardsDocDir, "lock-name");
    }

    /**
     * Creates a mock SharedFileStoreShard whose archiveOldData returns the given count (0 = nothing to archive).
     */
    private static SharedFileStoreShard mockShard(final PlanBDoc doc,
                                                  final long count) throws IOException {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.archiveOldData(any(), any())).thenReturn(count);
        return shard;
    }

    /**
     * Creates a mock SharedFileStoreShard whose archiveOldData returns a positive count
     * and also creates dated subdirectories in the archiveBaseDir argument so that
     * ArchiveOperation's Files.list() sees real archive shards.
     */
    @SuppressWarnings("unchecked")
    private static SharedFileStoreShard mockShardWithDirs(final PlanBDoc doc,
                                                          final List<String> dateLabels) throws IOException {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.archiveOldData(any(), any())).thenAnswer((InvocationOnMock inv) -> {
            final Path archiveBaseDir = inv.getArgument(1, Path.class);
            for (final String label : dateLabels) {
                Files.createDirectories(archiveBaseDir.resolve(label));
            }
            return (long) dateLabels.size();
        });
        return shard;
    }

    private void writeLastRunFile(final Instant timestamp) throws IOException {
        writeLastRunFile(timestamp.toString());
    }

    private void writeLastRunFile(final String content) throws IOException {
        final Path lastFile = sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.ARCHIVAL_LAST_FILE_NAME);
        Files.writeString(lastFile, content, StandardCharsets.UTF_8);
    }
}
