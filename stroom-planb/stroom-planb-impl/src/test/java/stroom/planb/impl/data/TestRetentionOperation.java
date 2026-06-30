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
import stroom.planb.impl.data.ArchivalGranularityUtil;
import stroom.planb.impl.fs.RetentionOperation;
import stroom.planb.impl.fs.SharedFileStoreOperationContext;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRetentionOperation {

    private static final int SHARD_INDEX = 0;
    private static final SimpleDuration ONE_HOUR = SimpleDuration.builder()
            .time(1).timeUnit(TimeUnit.HOURS).build();

    @TempDir
    Path tempDir;

    private RetentionOperation retentionOperation;
    private Path sharedShardsDocDir;

    @BeforeEach
    void setUp() throws IOException {
        retentionOperation = new RetentionOperation();
        sharedShardsDocDir = tempDir.resolve("shards").resolve("doc-uuid");
        Files.createDirectories(sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX)));
    }

    // -----------------------------------------------------------------------
    // isDue — disabled / null retention
    // -----------------------------------------------------------------------

    @Test
    void isDue_retentionDisabled_returnsFalse() {
        final PlanBDoc doc = docWithRetention(false, ONE_HOUR);
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    @Test
    void isDue_retentionNull_returnsFalse() {
        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.STATE)
                .build();
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isDue — no last-run file (never run)
    // -----------------------------------------------------------------------

    @Test
    void isDue_noLastRunFile_returnsTrue() {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isDue — file present, interval not yet elapsed
    // -----------------------------------------------------------------------

    @Test
    void isDue_lastRunTooRecent_returnsFalse() throws IOException {
        writeLastRunFile(Instant.now());
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isDue — file present, interval has elapsed
    // -----------------------------------------------------------------------

    @Test
    void isDue_lastRunIntervalElapsed_returnsTrue() throws IOException {
        writeLastRunFile(Instant.now().minusSeconds(7 * 60));
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isDue — corrupt file content
    // -----------------------------------------------------------------------

    @Test
    void isDue_corruptLastRunFile_returnsTrue() throws IOException {
        writeLastRunFile("not-a-timestamp");
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isTrue();
    }

    // -----------------------------------------------------------------------
    // run — writes last-run file and makes isDue return false
    // -----------------------------------------------------------------------

    @Test
    void run_writesLastRunFile() throws IOException {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 0L);
        retentionOperation.run(ctx(doc, shard));

        final Path lastFile = sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.RETENTION_LAST_FILE_NAME);
        assertThat(lastFile).exists();
        final String content = Files.readString(lastFile, StandardCharsets.UTF_8).trim();
        assertThat(Instant.parse(content)).isNotNull();
    }

    @Test
    void run_thenIsDue_returnsFalse() throws IOException {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 0L);
        retentionOperation.run(ctx(doc, shard));
        assertThat(retentionOperation.isDue(doc, sharedShardsDocDir, SHARD_INDEX)).isFalse();
    }

    @Test
    void run_returnsTrue_whenRecordsDeleted() throws IOException {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 5L);
        assertThat(retentionOperation.run(ctx(doc, shard))).isTrue();
    }

    @Test
    void run_returnsFalse_whenNoRecordsDeleted() throws IOException {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 0L);
        assertThat(retentionOperation.run(ctx(doc, shard))).isFalse();
    }

    @Test
    void run_returnsFalse_whenNotDue() throws IOException {
        // Write a recent last-run so isDue returns false.
        writeLastRunFile(Instant.now());
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 5L);
        assertThat(retentionOperation.run(ctx(doc, shard))).isFalse();
    }

    // -----------------------------------------------------------------------
    // deleteExpiredArchiveShards — sharded (HasSharedFileStore) doc
    // -----------------------------------------------------------------------

    /**
     * Creates a fake archive shard directory labelled for a date well before
     * the retention cutoff, runs retention, and asserts the dir is deleted.
     */
    @Test
    void run_deletesExpiredArchiveShardDir_whenHasSharedFileStore() throws IOException {
        final String docUuid = UUID.randomUUID().toString();
        final String sharedPath = tempDir.resolve("shared").toAbsolutePath().toString();

        // Archive shard dir: shared/archive/<uuid>/<shardIndex>/<dateLabel>
        final String expiredLabel = ArchivalGranularityUtil.label(
                ArchivalGranularity.DAY, Instant.now().minusSeconds(7L * 24 * 3600)); // 7 days ago
        final Path archiveShardDir = Path.of(sharedPath)
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(docUuid)
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(expiredLabel);
        Files.createDirectories(archiveShardDir);

        final PlanBDoc doc = traceDocWithRetention(docUuid, sharedPath, true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 0L);
        retentionOperation.run(ctx(doc, shard));

        assertThat(archiveShardDir).doesNotExist();
    }

    /**
     * Creates an archive shard directory labelled for today (not yet expired)
     * and asserts it is NOT deleted by retention.
     */
    @Test
    void run_retainsNonExpiredArchiveShardDir_whenHasSharedFileStore() throws IOException {
        final String docUuid = UUID.randomUUID().toString();
        final String sharedPath = tempDir.resolve("shared").toAbsolutePath().toString();

        // Recent label — within the 1-hour retention window so it should NOT be deleted
        final String recentLabel = ArchivalGranularityUtil.label(ArchivalGranularity.DAY, Instant.now());
        final Path archiveShardDir = Path.of(sharedPath)
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(docUuid)
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(recentLabel);
        Files.createDirectories(archiveShardDir);

        final PlanBDoc doc = traceDocWithRetention(docUuid, sharedPath, true, ONE_HOUR);
        final SharedFileStoreShard shard = mockShard(doc, 0L);
        retentionOperation.run(ctx(doc, shard));

        assertThat(archiveShardDir).exists();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PlanBDoc docWithRetention(final boolean enabled, final SimpleDuration duration) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder()
                        .retention(new RetentionSettings.Builder()
                                .enabled(enabled)
                                .duration(duration)
                                .build())
                        .build())
                .build();
    }

    private PlanBDoc traceDocWithRetention(final String uuid,
                                           final String sharedPath,
                                           final boolean enabled,
                                           final SimpleDuration duration) {
        return PlanBDoc.builder()
                .uuid(uuid)
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(1, sharedPath,
                                new ArchivalSettings.Builder()
                                        .enabled(true)
                                        .granularity(ArchivalGranularity.DAY)
                                        .build()))
                        .retention(new RetentionSettings.Builder()
                                .enabled(enabled)
                                .duration(duration)
                                .build())
                        .build())
                .build();
    }

    private SharedFileStoreOperationContext ctx(final PlanBDoc doc, final SharedFileStoreShard shard) {
        return new SharedFileStoreOperationContext(doc, SHARD_INDEX, shard, sharedShardsDocDir, "lock-name");
    }

    private static SharedFileStoreShard mockShard(final PlanBDoc doc, final long deletedCount) {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.deleteOldData(any())).thenReturn(deletedCount);
        return shard;
    }

    private void writeLastRunFile(final Instant timestamp) throws IOException {
        writeLastRunFile(timestamp.toString());
    }

    private void writeLastRunFile(final String content) throws IOException {
        final Path lastFile = sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(PlanBConstants.RETENTION_LAST_FILE_NAME);
        Files.writeString(lastFile, content, StandardCharsets.UTF_8);
    }
}
