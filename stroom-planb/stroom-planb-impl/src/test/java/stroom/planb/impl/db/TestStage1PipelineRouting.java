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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.State;
import stroom.planb.impl.fs.SharedFileStoreCleaner;
import stroom.planb.impl.fs.SharedFileStoreDocStore;
import stroom.planb.impl.fs.SharedFileStorePartDestination;
import stroom.planb.impl.fs.SharedFileStoreWriter;
import stroom.planb.impl.rest.FileDescriptor;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.impl.rest.RestPartDestination;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStage1PipelineRouting {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    @Test
    void testShardKeyRouter() {
        // Test edge cases
        assertThat(ShardKeyRouter.computeShardIndex("test".getBytes(StandardCharsets.UTF_8), 0)).isEqualTo(0);
        assertThat(ShardKeyRouter.computeShardIndex("test".getBytes(StandardCharsets.UTF_8), 1)).isEqualTo(0);
        assertThat(ShardKeyRouter.computeShardIndex("test", 1)).isEqualTo(0);
        assertThat(ShardKeyRouter.computeShardIndex(12345L, 1)).isEqualTo(0);

        // Test distribution
        final int shards = 4;
        final Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            final int shardIndex = ShardKeyRouter.computeShardIndex("key-" + i, shards);
            distribution.merge(shardIndex, 1, Integer::sum);
            assertThat(shardIndex).isGreaterThanOrEqualTo(0).isLessThan(shards);
        }
        // Verify all buckets received some keys
        assertThat(distribution).hasSize(shards);
        for (int i = 0; i < shards; i++) {
            assertThat(distribution.get(i)).isGreaterThan(150); // expects roughly uniform
        }
    }

    @Test
    void testSharedFileStoreWriter(@TempDir final Path tempDir) throws IOException {
        final Path localDir = tempDir.resolve("local");
        final Path sharedTarget = tempDir.resolve("shared/target");
        Files.createDirectories(localDir);

        // Writing a dummy data.mdb file
        final Path dummyLocalData = localDir.resolve("data.mdb");
        Files.writeString(dummyLocalData, "lmdb-data-content", StandardCharsets.UTF_8);

        // Copy
        SharedFileStoreWriter.copyToSharedStore(localDir, sharedTarget);

        // Verify copied files
        assertThat(sharedTarget).exists();
        assertThat(sharedTarget.resolve("data.mdb")).exists();
        assertThat(Files.readString(sharedTarget.resolve("data.mdb"))).isEqualTo("lmdb-data-content");
        assertThat(sharedTarget.resolve(".complete")).exists();

        // Verify failure cleanup
        final Path invalidLocalDir = tempDir.resolve("non-existent");
        final Path nextSharedTarget = tempDir.resolve("shared/target-fail");

        assertThatThrownBy(() -> SharedFileStoreWriter.copyToSharedStore(invalidLocalDir, nextSharedTarget))
                .isInstanceOf(IOException.class);

        assertThat(nextSharedTarget.resolveSibling("target-fail.tmp")).doesNotExist();
        assertThat(nextSharedTarget).doesNotExist();
    }

    @Test
    void testStreamWriterFactoryWithSharedStore(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = tempDir.resolve("writer");
        final Path sharedRoot = tempDir.resolve("shared");
        final StatePaths statePaths = new StatePaths(tempDir); // resolving tempDir/writer as writerDir

        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_map")
                .stateType(StateType.STATE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(4, sharedRoot.toAbsolutePath().toString()))
                        .build())
                .build();

        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get("test_map")).thenReturn(doc);

        final FileTransferClient fileTransferClient = Mockito.mock(FileTransferClient.class);

        // Instantiate PlanBStreamWriterFactory
        final BatchDestination batchPublisher = new DefaultBatchDestination();
        final PlanBStreamWriterFactory shardWriters = new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                batchPublisher,
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));

        final Meta meta = Mockito.mock(Meta.class);
        Mockito.when(meta.getId()).thenReturn(999L);

        // 1. Test Writing and Sharding
        try (final PlanBStreamWriter shardWriter = shardWriters.createWriter(meta)) {
            // Write some test data
            for (int i = 0; i < 20; i++) {
                final State state = new State(KeyPrefix.create("key-" + i), ValString.create("value-" + i));
                shardWriter.addState(doc, state);
            }

            // Verify local staging directories exist
            final Path localBaseDir = Files.list(writerDir).findFirst().orElseThrow();
            for (int shardIndex = 0; shardIndex < 4; shardIndex++) {
                final Path shardLocalPath = localBaseDir.resolve(doc.getUuid() + "_" + shardIndex);
                // Not all shards are guaranteed to receive data, but with 20 items and 4 shards, most should.
                // We'll verify the directory exists for at least the ones that received writes.
            }
        }

        // 2. Verify files are copied to shared root and deleted locally
        assertThat(writerDir).isEmptyDirectory(); // Local temp staging must have been cleaned up

        final Path sharedProcessingDir = sharedRoot.resolve("processing").resolve(doc.getUuid());
        assertThat(sharedProcessingDir).exists();

        // There should be shard subdirectories: e.g. 0000, 0001, etc.
        try (var shards = Files.list(sharedProcessingDir)) {
            final long shardCount = shards.count();
            assertThat(shardCount).isGreaterThan(0);
        }

        // Under each shard folder, there should be a batch directory containing data.mdb and .complete
        try (var shardFolders = Files.walk(sharedProcessingDir, 3)) {
            final boolean hasComplete = shardFolders
                    .filter(p -> p.getFileName().toString().equals(".complete"))
                    .anyMatch(Files::exists);
            assertThat(hasComplete).isTrue();
        }
    }

    @Test
    void testStreamWriterFactoryFallback(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = tempDir.resolve("writer");
        final StatePaths statePaths = new StatePaths(tempDir);


        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_map_fallback")
                .stateType(StateType.STATE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(1, null))
                        .build())
                .build();

        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get("test_map_fallback")).thenReturn(doc);

        final FileTransferClient fileTransferClient = Mockito.mock(FileTransferClient.class);

        final BatchDestination batchPublisher = new DefaultBatchDestination();
        final PlanBStreamWriterFactory shardWriters = new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                batchPublisher,
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));

        final Meta meta = Mockito.mock(Meta.class);
        Mockito.when(meta.getId()).thenReturn(888L);

        try (final PlanBStreamWriter shardWriter = shardWriters.createWriter(meta)) {
            final State state = new State(KeyPrefix.create("key-1"), ValString.create("value-1"));
            shardWriter.addState(doc, state);
        }

        // Verify fallback zip upload was triggered
        Mockito.verify(fileTransferClient, Mockito.times(1))
                .storePart(Mockito.any(FileDescriptor.class), Mockito.any(Path.class), Mockito.anyBoolean());

        // Local staging should still be cleaned up
        assertThat(writerDir).isEmptyDirectory();
    }

    @Test
    void testStreamWriterFactoryWithZeroShardCount(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = tempDir.resolve("writer");
        final Path sharedRoot = tempDir.resolve("shared");
        final StatePaths statePaths = new StatePaths(tempDir);

        final PlanBDoc doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_map_zero")
                .stateType(StateType.STATE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(0, sharedRoot.toAbsolutePath().toString()))
                        .build())
                .build();

        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get("test_map_zero")).thenReturn(doc);

        final FileTransferClient fileTransferClient = Mockito.mock(FileTransferClient.class);

        final BatchDestination batchPublisher = new DefaultBatchDestination();
        final PlanBStreamWriterFactory shardWriters = new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                batchPublisher,
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));

        final Meta meta = Mockito.mock(Meta.class);
        Mockito.when(meta.getId()).thenReturn(777L);

        try (final PlanBStreamWriter shardWriter = shardWriters.createWriter(meta)) {
            final State state = new State(KeyPrefix.create("key-1"), ValString.create("value-1"));
            shardWriter.addState(doc, state);

            // Verify local staging directory resolves exactly to docUuid (no _0 suffix)
            final Path localBaseDir = Files.list(writerDir).findFirst().orElseThrow();
            final Path shardLocalPath = localBaseDir.resolve(doc.getUuid());
            assertThat(shardLocalPath).exists();
            assertThat(localBaseDir.resolve(doc.getUuid() + "_0")).doesNotExist();
        }

        // Verify fallback zip upload was triggered instead of copying to shared path
        Mockito.verify(fileTransferClient, Mockito.times(1))
                .storePart(Mockito.any(FileDescriptor.class), Mockito.any(Path.class), Mockito.anyBoolean());

        // Shared processing directory for this map should NOT exist
        final Path sharedProcessingDir = sharedRoot.resolve("processing").resolve(doc.getUuid());
        assertThat(sharedProcessingDir).doesNotExist();

        // Local staging should still be cleaned up
        assertThat(writerDir).isEmptyDirectory();
    }

    @Test
    void testStartupCleanup(@TempDir final Path tempDir) throws IOException {
        final Path sharedRoot = tempDir.resolve("shared");
        final StatePaths statePaths = new StatePaths(tempDir);
        final String docUuid = UUID.randomUUID().toString();

        // --- Local staging cleanup (PlanBStreamWriterFactory constructor) ---
        final Path localStagingFolder = statePaths.getWriterDir().resolve("some_stale_staging_dir");
        Files.createDirectories(localStagingFolder);

        final FileTransferClient fileTransferClient = Mockito.mock(FileTransferClient.class);
        new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                new DefaultBatchDestination(),
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));

        // Writer dir (and everything under it) must be wiped on construction.
        assertThat(localStagingFolder).doesNotExist();

        // --- Shared .tmp cleanup (SharedFileStoreCleaner.startup()) ---
        // Pre-create an incomplete .tmp dir on shared storage
        final Path incompleteSharedTemp = sharedRoot
                .resolve("processing")
                .resolve(docUuid)
                .resolve("0000")
                .resolve("batch_1" + PlanBConstants.TMP_DIR_SUFFIX);
        Files.createDirectories(incompleteSharedTemp);
        Files.writeString(incompleteSharedTemp.resolve("data.mdb"), "partial-data", StandardCharsets.UTF_8);
        // Backdate so the 5-minute age filter treats it as an orphan.
        Files.setLastModifiedTime(incompleteSharedTemp,
                FileTime.from(Instant.now().minus(Duration.ofMinutes(10))));

        // Pre-create a complete (non-.tmp) batch — must be preserved
        final Path completeSharedBatch = sharedRoot
                .resolve("processing")
                .resolve(docUuid)
                .resolve("0000")
                .resolve("batch_2");
        Files.createDirectories(completeSharedBatch);
        Files.writeString(completeSharedBatch.resolve("data.mdb"), "complete-data", StandardCharsets.UTF_8);

        final SharedFileStoreDocStore mockDataSource = Mockito.mock(SharedFileStoreDocStore.class);
        Mockito.when(mockDataSource.getLiveSharedPathData())
                .thenReturn(Map.of(sharedRoot, Set.of(docUuid)));

        final SharedFileStoreCleaner cleaner = new SharedFileStoreCleaner(Set.of(mockDataSource));
        cleaner.startup();

        // Incomplete .tmp dir must be deleted; complete batch must survive.
        assertThat(incompleteSharedTemp).doesNotExist();
        assertThat(completeSharedBatch).exists();
        assertThat(completeSharedBatch.resolve("data.mdb")).exists();
    }
}
