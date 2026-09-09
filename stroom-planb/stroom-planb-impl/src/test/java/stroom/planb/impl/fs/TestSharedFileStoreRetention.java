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

import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.archive.BucketGranularityUtil;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.task.api.ExecutorProvider;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.apache.commons.lang3.NotImplementedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The retention schedule and the pruning of expired archive buckets, both owned by
 * {@link SharedFileStoreMergeProcessor}. Sweeping the holding shard is the strategy's half and is
 * covered by {@link TestHoldingAreaMergeStrategy}.
 */
class TestSharedFileStoreRetention {

    private static final int SHARD_INDEX = 0;
    private static final SimpleDuration ONE_HOUR = SimpleDuration.builder()
            .time(1).timeUnit(TimeUnit.HOURS).build();
    private static final SimpleDuration CHECK_INTERVAL = SimpleDuration.builder()
            .time(16).timeUnit(TimeUnit.HOURS).build();

    @TempDir
    Path tempDir;

    private final String docUuid = UUID.randomUUID().toString();
    private Path archiveShardDir;

    @BeforeEach
    void setUp() throws IOException {
        archiveShardDir = sharedRoot()
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(docUuid)
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX));
        Files.createDirectories(archiveShardDir);
    }

    // -----------------------------------------------------------------------
    // retentionDue
    // -----------------------------------------------------------------------

    @Test
    void retentionDisabled_isNotDue() {
        assertThat(due(docWithRetention(false, ONE_HOUR))).isFalse();
    }

    @Test
    void noRetentionSettings_isNotDue() {
        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(docUuid)
                .name("test")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder().build())
                .build();
        assertThat(due(doc)).isFalse();
    }

    @Test
    void neverRun_isDue() {
        assertThat(due(docWithRetention(true, ONE_HOUR))).isTrue();
    }

    @Test
    void runTooRecently_isNotDue() throws IOException {
        writeLastRun(Instant.now());
        assertThat(due(docWithRetention(true, ONE_HOUR))).isFalse();
    }

    @Test
    void intervalElapsed_isDue() throws IOException {
        writeLastRun(Instant.now().minusSeconds(17L * 3600));
        assertThat(due(docWithRetention(true, ONE_HOUR))).isTrue();
    }

    /** A corrupt marker fails open rather than stalling retention forever. */
    @Test
    void corruptLastRunFile_isDue() throws IOException {
        writeLastRun("not-an-instant");
        assertThat(due(docWithRetention(true, ONE_HOUR))).isTrue();
    }

    @Test
    void recordingTheRun_makesItNotDue() throws IOException {
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        assertThat(due(doc)).isTrue();

        new OperationMarker(PlanBConstants.RETENTION_LAST_FILE_NAME)
                .recordRun(archiveShardDir.getParent(), SHARD_INDEX);

        assertThat(due(doc)).isFalse();
    }

    // -----------------------------------------------------------------------
    // A shared-store doc has no local shard to read
    // -----------------------------------------------------------------------

    /**
     * A store held on the shared file store keeps no whole-shard copy a node can read — its records are
     * in the archive buckets. Handing back a local shard would read as an empty store and be snapshotted
     * as one, so {@code createShard} must refuse until a read path for such stores exists.
     */
    @Test
    void sharedStoreDoc_hasNoLocalShard() {
        final PlanBDocCache docCache = mock(PlanBDocCache.class);
        final PlanBDoc doc = docWithRetention(true, ONE_HOUR);
        when(docCache.get(doc.getName())).thenReturn(doc);

        final ShardManager shardManager = new ShardManager(
                null,
                null,
                docCache,
                null,
                null,
                () -> PlanBConfig.builder().build(),
                new PlanBPaths(tempDir.resolve("local")),
                null,
                null,
                mock(ExecutorProvider.class),
                null);

        assertThatThrownBy(() -> shardManager.getShardForMapName(doc.getName()))
                .isInstanceOf(NotImplementedException.class);
    }

    // -----------------------------------------------------------------------
    // deleteExpiredArchiveShards
    // -----------------------------------------------------------------------

    @Test
    void deletesArchiveBucket_pastRetention() throws IOException {
        final Path bucket = archiveBucket(Instant.now().minusSeconds(7L * 24 * 3600));
        SharedFileStoreMergeProcessor.deleteExpiredArchiveShards(ctx(sharedDoc()));
        assertThat(bucket).doesNotExist();
    }

    @Test
    void keepsArchiveBucket_withinRetention() throws IOException {
        final Path bucket = archiveBucket(Instant.now());
        SharedFileStoreMergeProcessor.deleteExpiredArchiveShards(ctx(sharedDoc()));
        assertThat(bucket).exists();
    }

    /**
     * Changing the granularity leaves buckets behind in the old layout, and their names are still the
     * only record of how they were written. Decoding one with the doc's current granularity instead
     * fails to parse it, which would skip it on every run and strand it for good.
     */
    @Test
    void deletesDayBucket_afterGranularityChangedToHour() throws IOException {
        final Path dayBucket = archiveBucket(
                BucketGranularity.DAY, Instant.now().minusSeconds(7L * 24 * 3600));
        SharedFileStoreMergeProcessor.deleteExpiredArchiveShards(
                ctx(sharedDoc(BucketGranularity.HOUR)));
        assertThat(dayBucket).doesNotExist();
    }

    /** The same, one granularity wider — a label that fails to parse rather than failing to split. */
    @Test
    void deletesWeekBucket_afterGranularityChangedToDay() throws IOException {
        final Path weekBucket = archiveBucket(
                BucketGranularity.WEEK, Instant.now().minusSeconds(30L * 24 * 3600));
        SharedFileStoreMergeProcessor.deleteExpiredArchiveShards(
                ctx(sharedDoc(BucketGranularity.DAY)));
        assertThat(weekBucket).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean due(final PlanBDoc doc) {
        return SharedFileStoreMergeProcessor.retentionDue(doc, SHARD_INDEX);
    }

    private MergeContext ctx(final PlanBDoc doc) {
        return new MergeContext(doc, SHARD_INDEX, "lock-name", true);
    }

    private Path sharedRoot() {
        return tempDir.resolve("shared");
    }

    private Path archiveBucket(final Instant bucketTime) throws IOException {
        return archiveBucket(BucketGranularity.DAY, bucketTime);
    }

    private Path archiveBucket(final BucketGranularity granularity,
                               final Instant bucketTime) throws IOException {
        final Path bucket = archiveShardDir
                .resolve(BucketGranularityUtil.label(granularity, bucketTime));
        Files.createDirectories(bucket);
        return bucket;
    }

    private PlanBDoc sharedDoc() {
        return sharedDoc(BucketGranularity.DAY);
    }

    private PlanBDoc sharedDoc(final BucketGranularity granularity) {
        return PlanBDoc.builder()
                .uuid(docUuid)
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                sharedRoot().toAbsolutePath().toString()))
                        .granularity(granularity)
                        .retention(new RetentionSettings.Builder()
                                .enabled(true)
                                .duration(ONE_HOUR)
                                .checkInterval(CHECK_INTERVAL)
                                .build())
                        .build())
                .build();
    }

    private PlanBDoc docWithRetention(final boolean enabled, final SimpleDuration duration) {
        return PlanBDoc.builder()
                .uuid(docUuid)
                .name("test")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1,
                                sharedRoot().toAbsolutePath().toString()))
                        .granularity(BucketGranularity.DAY)
                        .retention(new RetentionSettings.Builder()
                                .enabled(enabled)
                                .duration(duration)
                                .checkInterval(CHECK_INTERVAL)
                                .build())
                        .build())
                .build();
    }

    private void writeLastRun(final Instant timestamp) throws IOException {
        writeLastRun(timestamp.toString());
    }

    private void writeLastRun(final String content) throws IOException {
        Files.writeString(archiveShardDir.resolve(PlanBConstants.RETENTION_LAST_FILE_NAME),
                content, StandardCharsets.UTF_8);
    }
}
