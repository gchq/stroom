/*
 * Copyright 2026 Crown Copyright
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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoreShard {

    private static final Duration LIFESPAN = Duration.ofMinutes(10);

    /**
     * Creating a snapshot must not throw, as it is called from the merge path as well as the scheduled job, so an
     * escaping exception would fail data merging for the shard. See gh-5689.
     */
    @Test
    void createSnapshotSucceeds(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final PlanBDoc doc = createDoc(new SnapshotSettings(true, false, false));
        final StoreShard shard = createShard(statePaths, doc);
        try {
            assertThatNoException().isThrownBy(shard::createSnapshot);

            assertThat(statePaths.getSnapshotDir().resolve(doc.getUuid()).resolve("snapshot.zip")).exists();
        } finally {
            // The shard's env must be closed before JUnit deletes the @TempDir
            shard.close();
        }
    }

    /**
     * A second call should be a no op rather than re-zipping, as a snapshot already exists for the current
     * write time.
     */
    @Test
    void createSnapshotIsNotRepeatedWithoutWrites(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final PlanBDoc doc = createDoc(new SnapshotSettings(true, false, false));
        final StoreShard shard = createShard(statePaths, doc);
        try {
            shard.createSnapshot();
            final Path zip = statePaths.getSnapshotDir().resolve(doc.getUuid()).resolve("snapshot.zip");
            assertThat(zip).exists();

            assertThatNoException().isThrownBy(shard::createSnapshot);
            assertThat(zip).exists();
        } finally {
            shard.close();
        }
    }

    /**
     * A store that uses snapshots only for query must still have snapshots created for it. This condition was
     * previously not negated, so such a store never published a snapshot. See gh-5689.
     */
    @Test
    void createSnapshotWhenOnlyUsedForQuery(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final PlanBDoc doc = createDoc(new SnapshotSettings(false, false, true));
        final StoreShard shard = createShard(statePaths, doc);
        try {
            shard.createSnapshot();

            assertThat(statePaths.getSnapshotDir().resolve(doc.getUuid()).resolve("snapshot.zip")).exists();
        } finally {
            shard.close();
        }
    }

    /**
     * Nothing will ever fetch a snapshot for a store that uses them for nothing, so don't spend the I/O.
     */
    @Test
    void noSnapshotWhenSnapshotsAreNotUsed(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final PlanBDoc doc = createDoc(new SnapshotSettings(false, false, false));
        final StoreShard shard = createShard(statePaths, doc);
        try {
            shard.createSnapshot();

            assertThat(statePaths.getSnapshotDir().resolve(doc.getUuid()).resolve("snapshot.zip")).doesNotExist();
        } finally {
            shard.close();
        }
    }

    /**
     * A part written snapshot is about the size of the shard itself, and a full disk is the most likely reason
     * for creation to keep failing, so leaving it behind would hold on to the space that caused the failure.
     * See gh-5689.
     */
    @Test
    void failedSnapshotCreationLeavesNoPartWrittenFile(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final PlanBDoc doc = createDoc(new SnapshotSettings(true, false, false));
        final StoreShard shard = createShard(statePaths, doc);
        try {
            // Make the final move fail by putting a non empty dir where the snapshot zip needs to go. The zip is
            // still written to the temp file first, so this exercises a failure after the temp file exists.
            final Path snapshotDir = statePaths.getSnapshotDir().resolve(doc.getUuid());
            final Path zip = snapshotDir.resolve("snapshot.zip");
            Files.createDirectories(zip);
            Files.writeString(zip.resolve("occupied"), "occupied");

            shard.createSnapshot();

            assertThat(snapshotDir.resolve("snapshot.tmp")).doesNotExist();
        } finally {
            shard.close();
        }
    }

    /**
     * close() must close the env even when the calling thread is interrupted, as shutdown
     * interrupts task threads, and must leave the interrupt flag set for its caller.
     */
    @Test
    void closeIsUninterruptibleAndRestoresTheInterruptFlag(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        final StoreShard shard = createShard(statePaths, createDoc(new SnapshotSettings(false, false, false)));

        Thread.currentThread().interrupt();
        try {
            assertThatNoException().isThrownBy(shard::close);
        } finally {
            // Asserts close() left the flag set for its caller, and clears it. Must be
            // cleared before the probe below: unlike close(), get() is interruptible, so it
            // would report the interrupt rather than the closed shard.
            assertThat(Thread.interrupted()).isTrue();
        }

        // The db really was closed, not skipped.
        assertThatThrownBy(() -> shard.get(db -> null))
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
    }

    /**
     * Every method that uses the db must report a closed shard as a {@link
     * SnapshotShard.ShardClosedException} rather than dereferencing a null db.
     */
    @Test
    void useAfterCloseThrowsShardClosedException(@TempDir final Path tempDir) {
        final StatePaths statePaths = new StatePaths(tempDir);
        // Retention enabled so that deleteOldData actually reaches the db rather than
        // returning early, which would pass for the wrong reason.
        final PlanBDoc doc = PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder()
                        .retention(new RetentionSettings.Builder()
                                .enabled(true)
                                .duration(SimpleDuration.builder().time(1).timeUnit(TimeUnit.DAYS).build())
                                .build())
                        .build())
                .build();
        final StoreShard shard = createShard(statePaths, doc);
        shard.close();

        assertThatThrownBy(() -> shard.merge(tempDir))
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
        assertThatThrownBy(() -> shard.deleteOldData(doc))
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
        assertThatThrownBy(() -> shard.deleteOldMergeStatus(Instant.now()))
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
        assertThatThrownBy(shard::compact)
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
        assertThatThrownBy(() -> shard.get(db -> null))
                .isInstanceOf(SnapshotShard.ShardClosedException.class);
        assertThatThrownBy(shard::getInfo)
                .isInstanceOf(SnapshotShard.ShardClosedException.class);

        // compact() must fail before it creates its working dir, or it would leave a full
        // sized copy of the data behind that nothing reclaims.
        assertThat(statePaths.getShardDir().resolve(doc.getUuid()).resolve("compacted"))
                .doesNotExist();
    }

    private PlanBDoc createDoc(final SnapshotSettings snapshotSettings) {
        return PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder().snapshotSettings(snapshotSettings).build())
                .build();
    }

    private StoreShard createShard(final StatePaths statePaths, final PlanBDoc doc) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final PlanBConfig config = PlanBConfig.builder().build();
        return new StoreShard(
                new ByteBuffers(byteBufferFactory),
                byteBufferFactory,
                () -> config,
                statePaths,
                doc);
    }

    /**
     * Snapshot creation re-zips the whole shard while holding the write lock, so a shard that always fails must
     * not be retried on every run of the snapshot creation job. See gh-5689.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 10",
            "2, 20",
            "3, 30",
            "5, 50",
            "6, 60",
    })
    void snapshotRetryDelayGrowsLinearlyWithFailures(final int failureCount, final int expectedMinutes) {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, failureCount))
                .isEqualTo(Duration.ofMinutes(expectedMinutes));
    }

    @ParameterizedTest
    @CsvSource({
            "7",
            "100",
            "1000",
    })
    void snapshotRetryDelayIsCapped(final int failureCount) {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, failureCount))
                .isEqualTo(Duration.ofMinutes(60));
    }

    /**
     * The delay is read before the failure count has been incremented in some paths, so a count of zero must
     * still yield a usable delay rather than zero, which would mean no back off at all.
     */
    @Test
    void snapshotRetryDelayIsAtLeastOneLifespan() {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, 0)).isEqualTo(LIFESPAN);
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, -1)).isEqualTo(LIFESPAN);
    }

    @Test
    void snapshotRetryDelayScalesWithConfiguredLifespan() {
        final Duration lifespan = Duration.ofMinutes(2);
        assertThat(StoreShard.getSnapshotRetryDelay(lifespan, 3)).isEqualTo(Duration.ofMinutes(6));
        assertThat(StoreShard.getSnapshotRetryDelay(lifespan, 99)).isEqualTo(Duration.ofMinutes(12));
    }
}
