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

import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSharedFileStorePublisher {

    private static final int SHARD_INDEX = 0;

    @TempDir
    Path tempDir;

    @Mock
    private NodeInfo nodeInfo;

    private SharedFileStorePublisher publisher;
    private Path sharedRoot;
    private PlanBDoc doc;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        when(nodeInfo.getThisNodeName()).thenReturn("test-node");
        publisher = new SharedFileStorePublisher(nodeInfo);

        sharedRoot = tempDir.resolve("shared");
        Files.createDirectories(sharedRoot);

        doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_doc")
                .stateType(StateType.STATE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(1, sharedRoot.toAbsolutePath().toString()))
                        .build())
                .build();
    }

    // -----------------------------------------------------------------------
    // push — basic output structure
    // -----------------------------------------------------------------------

    @Test
    void push_copiesDataMdbToSharedStore() throws IOException {
        final Path localDir = createLocalShardDir(true, false);
        final SharedFileStoreShard shard = shardWithDir(localDir);

        publisher.push(doc, SHARD_INDEX, shard);

        assertThat(canonicalShardDir().resolve(PlanBConstants.DATA_FILE_NAME)).exists();
    }

    /**
     * Regression test for the SIGSEGV (SEGV_MAPERR) crash caused by sharing lock.mdb via the
     * shared filesystem.
     *
     * <p>lock.mdb contains {@code PTHREAD_MUTEX_ROBUST | PTHREAD_PROCESS_SHARED} mutexes whose
     * internal linked-list state encodes absolute virtual addresses from the writing process. When
     * another environment maps the file at a <em>different</em> virtual address, those pointers
     * become stale. The next {@code pthread_mutex_lock} call attempts to update the old list entry
     * and crashes with {@code SIGSEGV / SEGV_MAPERR}. LMDB recreates lock.mdb automatically when
     * it is absent on {@code mdb_env_open}.
     */
    @Test
    void push_neverCopiesLockMdbToSharedStore() throws IOException {
        // Given a local shard dir that has both data.mdb AND lock.mdb
        final Path localDir = createLocalShardDir(true, true);

        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        // lock.mdb must NOT appear in the shared shard directory
        assertThat(canonicalShardDir().resolve(PlanBConstants.LOCK_FILE_NAME))
                .as("lock.mdb must never be published to the shared store")
                .doesNotExist();
        // data.mdb still goes through normally
        assertThat(canonicalShardDir().resolve(PlanBConstants.DATA_FILE_NAME)).exists();
    }

    @Test
    void push_writesCompleteSentinel() throws IOException {
        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        assertThat(canonicalShardDir().resolve(PlanBConstants.COMPLETE_FILE_NAME)).exists();
    }

    @Test
    void push_writesVersionMarkerToSharedAndLocal() throws IOException {
        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        assertThat(canonicalShardDir().resolve(PlanBConstants.VERSION_FILE_NAME)).exists();
        assertThat(localDir.resolve(PlanBConstants.VERSION_FILE_NAME)).exists();
    }

    @Test
    void push_versionContainsNodeName() throws IOException {
        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        final String version = Files.readString(
                canonicalShardDir().resolve(PlanBConstants.VERSION_FILE_NAME));
        assertThat(version).contains("test-node");
    }

    // -----------------------------------------------------------------------
    // push — operational file carry-forward
    // -----------------------------------------------------------------------

    @Test
    void push_carriesForwardOperationalFile() throws IOException {
        // Place an operational file (.retention.last) in the existing shared shard dir.
        final Path existingSharedDir = canonicalShardDir();
        Files.createDirectories(existingSharedDir);
        Files.writeString(existingSharedDir.resolve(PlanBConstants.RETENTION_LAST_FILE_NAME),
                "2026-01-01T00:00:00Z");

        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        assertThat(canonicalShardDir().resolve(PlanBConstants.RETENTION_LAST_FILE_NAME)).exists();
    }

    @Test
    void push_doesNotCarryForwardSystemFiles() throws IOException {
        // Place stale system files in the existing shared shard dir.
        // They should be replaced by freshly generated copies, not carried forward.
        final Path existingSharedDir = canonicalShardDir();
        Files.createDirectories(existingSharedDir);
        Files.writeString(existingSharedDir.resolve(PlanBConstants.VERSION_FILE_NAME), "stale-version");
        Files.writeString(existingSharedDir.resolve(PlanBConstants.COMPLETE_FILE_NAME), "stale");

        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        // The version file must be freshly written (contains node name), not the stale value.
        final String newVersion = Files.readString(
                canonicalShardDir().resolve(PlanBConstants.VERSION_FILE_NAME));
        assertThat(newVersion).contains("test-node").doesNotContain("stale-version");
    }

    // -----------------------------------------------------------------------
    // push — no data.mdb (empty local shard)
    // -----------------------------------------------------------------------

    @Test
    void push_noLocalDataMdb_stillWritesSentinels() throws IOException {
        // Local dir exists but has no data.mdb — push should still complete.
        final Path localDir = createLocalShardDir(false, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));

        assertThat(canonicalShardDir().resolve(PlanBConstants.COMPLETE_FILE_NAME)).exists();
        assertThat(canonicalShardDir().resolve(PlanBConstants.VERSION_FILE_NAME)).exists();
    }

    // -----------------------------------------------------------------------
    // push — no prior canonical shard
    // -----------------------------------------------------------------------

    @Test
    void push_noExistingSharedShard_createsCanonicalDir() throws IOException {
        // Canonical shard dir does not exist yet.
        assertThat(canonicalShardDir()).doesNotExist();
        final Path localDir = createLocalShardDir(true, false);
        publisher.push(doc, SHARD_INDEX, shardWithDir(localDir));
        assertThat(canonicalShardDir()).isDirectory();
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — .tmp_ dir
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_deletesTmpDir() throws IOException {
        final Path shardsDocDir = sharedShardsDocDir();
        Files.createDirectories(shardsDocDir);
        final Path tmpDir = shardsDocDir.resolve(PlanBConstants.TMP_DIR_PREFIX + SHARD_INDEX + "_12345");
        Files.createDirectories(tmpDir);
        Files.writeString(tmpDir.resolve("data.mdb"), "partial");

        publisher.recoverOrphaned(shardsDocDir, SHARD_INDEX);

        assertThat(tmpDir).doesNotExist();
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — .old_ dir with canonical present
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_deletesOldDirWhenCanonicalExists() throws IOException {
        final Path shardsDocDir = sharedShardsDocDir();
        Files.createDirectories(canonicalShardDir());   // canonical is present

        final Path oldDir = shardsDocDir.resolve(PlanBConstants.OLD_DIR_PREFIX + SHARD_INDEX + "_12345");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("data.mdb"), "old-data");

        publisher.recoverOrphaned(shardsDocDir, SHARD_INDEX);

        assertThat(oldDir).doesNotExist();
        assertThat(canonicalShardDir()).exists();
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — .old_ dir with canonical absent
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_restoresOldDirWhenCanonicalMissing() throws IOException {
        final Path shardsDocDir = sharedShardsDocDir();
        Files.createDirectories(shardsDocDir);
        // canonical shard dir does NOT exist
        assertThat(canonicalShardDir()).doesNotExist();

        final Path oldDir = shardsDocDir.resolve(PlanBConstants.OLD_DIR_PREFIX + SHARD_INDEX + "_12345");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("data.mdb"), "rescued-data");

        publisher.recoverOrphaned(shardsDocDir, SHARD_INDEX);

        assertThat(oldDir).doesNotExist();
        assertThat(canonicalShardDir()).isDirectory();
        assertThat(canonicalShardDir().resolve("data.mdb")).exists();
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — non-existent parent is a no-op
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_nonExistentParentDir_isNoOp() {
        final Path missing = sharedRoot.resolve("does-not-exist").resolve("also-missing");
        // Must not throw.
        publisher.recoverOrphaned(missing, SHARD_INDEX);
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — unrelated dirs are untouched
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_ignoresUnrelatedDirs() throws IOException {
        final Path shardsDocDir = sharedShardsDocDir();
        Files.createDirectories(shardsDocDir);
        final Path unrelated = shardsDocDir.resolve("some_other_dir");
        Files.createDirectories(unrelated);

        publisher.recoverOrphaned(shardsDocDir, SHARD_INDEX);

        assertThat(unrelated).exists();
    }

    // -----------------------------------------------------------------------
    // recoverOrphaned — dirs for a different shard index are untouched
    // -----------------------------------------------------------------------

    @Test
    void recoverOrphaned_ignoresDirsForDifferentShardIndex() throws IOException {
        final Path shardsDocDir = sharedShardsDocDir();
        Files.createDirectories(shardsDocDir);

        // Temp dir for shard 1, not shard 0.
        final Path otherShardTmp = shardsDocDir.resolve(PlanBConstants.TMP_DIR_PREFIX + "1_12345");
        Files.createDirectories(otherShardTmp);

        publisher.recoverOrphaned(shardsDocDir, SHARD_INDEX);

        // Should be left alone — belongs to shard 1.
        assertThat(otherShardTmp).exists();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns the canonical shared shard directory for shard 0 of the test doc. */
    private Path canonicalShardDir() {
        return sharedShardsDocDir().resolve(PlanBConstants.formatShardIndex(SHARD_INDEX));
    }

    private Path sharedShardsDocDir() {
        return sharedRoot
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid());
    }

    /**
     * Creates a local shard directory with an optional {@code data.mdb} and
     * {@code lock.mdb}. Returns the directory path.
     */
    private Path createLocalShardDir(final boolean withDataMdb,
                                     final boolean withLockMdb) throws IOException {
        final Path dir = tempDir.resolve("local_shard_" + UUID.randomUUID());
        Files.createDirectories(dir);
        if (withDataMdb) {
            Files.write(dir.resolve(PlanBConstants.DATA_FILE_NAME),
                    new byte[]{0x4D, 0x44, 0x42, 0x31}); // "MDB1" marker bytes
        }
        if (withLockMdb) {
            Files.writeString(dir.resolve(PlanBConstants.LOCK_FILE_NAME), "lock");
        }
        return dir;
    }

    /** Returns a mock SharedFileStoreShard whose getShardDir() returns the given directory. */
    private static SharedFileStoreShard shardWithDir(final Path dir) {
        final SharedFileStoreShard shard = mock(SharedFileStoreShard.class);
        when(shard.getShardDir()).thenReturn(dir);
        return shard;
    }
}
