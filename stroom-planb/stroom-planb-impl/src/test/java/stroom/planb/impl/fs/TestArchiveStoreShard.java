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

package stroom.planb.impl.fs;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.RestStoreShard;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.task.api.ExecutorProvider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A local archive copy is replaced when its bucket is republished, never refreshed in place, so that a
 * read never waits for another read.
 */
class TestArchiveStoreShard {

    private static final String DATE_LABEL = "2026-09-02_11";
    private static final int SHARD_INDEX = 0;

    private final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
    private final ByteBuffers byteBuffers = new ByteBuffers(byteBufferFactory);
    private final PlanBConfig config = PlanBConfig.builder().build();

    /**
     * The copy is taken once, so a republish has to be noticed by comparing the bucket's version against
     * the one copied.
     */
    @Test
    void staleOnlyOnceTheBucketHasBeenRepublished(@TempDir final Path tempDir) throws Exception {
        final PlanBDoc doc = createDoc();
        final Path bucket = createBucket(tempDir, doc, "v1");
        final ArchiveStoreShard shard = createArchiveShard(tempDir, doc, bucket);

        try {
            assertThat(shard.isStale()).as("bucket unchanged").isFalse();

            Files.writeString(bucket.resolve(PlanBConstants.VERSION_FILE_NAME), "v2");
            // The check reads the version file at most once a second, so wait out that interval rather
            // than reading a throttled false.
            Thread.sleep(1_100);

            assertThat(shard.isStale()).as("bucket republished").isTrue();
        } finally {
            shard.close();
        }
    }

    /**
     * Closing a replaced copy must never wait: a reader may hold it for as long as its work takes, and
     * blocking would hand that wait to whoever is running cleanup.
     */
    @Test
    void replacedCopyIsClosedOnlyOnceItsReadersHaveLeft(@TempDir final Path tempDir) throws Exception {
        final PlanBDoc doc = createDoc();
        final Path bucket = createBucket(tempDir, doc, "v1");
        final ArchiveStoreShard shard = createArchiveShard(tempDir, doc, bucket);

        final CountDownLatch readerInside = new CountDownLatch(1);
        final CountDownLatch releaseReader = new CountDownLatch(1);
        final CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> shard.get(db -> {
            readerInside.countDown();
            await(releaseReader);
            return null;
        }));

        readerInside.await();
        assertThat(shard.closeIfUnused()).as("reader still inside").isFalse();
        assertThat(shard.getShardDir()).exists();

        releaseReader.countDown();
        reader.join();

        assertThat(shard.closeIfUnused()).as("reader has left").isTrue();
        assertThat(shard.getShardDir()).doesNotExist();
    }

    /**
     * The regression this design exists for. Refreshing a copy in place needed exclusive access, so a
     * query against a republished bucket waited for every reader already inside it — including the
     * pathway build, which holds one for minutes at a time.
     */
    @Test
    void readDoesNotWaitForAnotherReaderAfterARepublish(@TempDir final Path tempDir) throws Exception {
        final PlanBDoc doc = createDoc();
        final Path bucket = createBucket(tempDir, doc, "v1");
        final ArchiveShardRef ref = new ArchiveShardRef(DATE_LABEL, bucket, BucketGranularity.HOUR);
        final ShardManager shardManager = createShardManager(tempDir);

        final CountDownLatch readerInside = new CountDownLatch(1);
        final CountDownLatch releaseReader = new CountDownLatch(1);
        final CompletableFuture<Void> holder = CompletableFuture.runAsync(() ->
                shardManager.getArchive(doc, SHARD_INDEX, ref, db -> {
                    readerInside.countDown();
                    await(releaseReader);
                    return null;
                }));

        try {
            readerInside.await();
            Files.writeString(bucket.resolve(PlanBConstants.VERSION_FILE_NAME), "v2");
            // Both the old refresh and the current staleness check look at the bucket version at most
            // once a second. Without this wait the second read below skips the check altogether and
            // passes whatever the code does, because a shared read lock is never contended.
            Thread.sleep(1_100);

            // Refreshing the copy in place needed exclusive access, so this used to block until the
            // reader above released, however long that took.
            Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    shardManager.getArchive(doc, SHARD_INDEX, ref, db -> null));
        } finally {
            releaseReader.countDown();
            holder.join();
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private PlanBDoc createDoc() {
        return PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder().build())
                .build();
    }

    /**
     * Builds an archive bucket holding a real (empty) LMDB file, by opening a writable shard and copying
     * the {@code data.mdb} it creates.
     */
    private Path createBucket(final Path tempDir, final PlanBDoc doc, final String version) throws IOException {
        final PlanBPaths sourcePaths = new PlanBPaths(tempDir.resolve("source"));
        final RestStoreShard source = new RestStoreShard(
                byteBuffers, byteBufferFactory, () -> config, sourcePaths, doc);
        source.close();

        final Path bucket = tempDir
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(SHARD_INDEX))
                .resolve(DATE_LABEL);
        Files.createDirectories(bucket);
        Files.copy(sourcePaths.getShardDir().resolve(doc.getUuid()).resolve(PlanBConstants.DATA_FILE_NAME),
                bucket.resolve(PlanBConstants.DATA_FILE_NAME));
        Files.writeString(bucket.resolve(PlanBConstants.VERSION_FILE_NAME), version);
        return bucket;
    }

    private ArchiveStoreShard createArchiveShard(final Path tempDir, final PlanBDoc doc, final Path bucket) {
        return new ArchiveStoreShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                new PlanBPaths(tempDir.resolve("local")),
                doc,
                SHARD_INDEX,
                new ArchiveShardRef(DATE_LABEL, bucket, BucketGranularity.HOUR));
    }

    private ShardManager createShardManager(final Path tempDir) {
        return new ShardManager(
                byteBuffers,
                byteBufferFactory,
                null,
                null,
                null,
                () -> config,
                new PlanBPaths(tempDir.resolve("local")),
                null,
                null,
                mock(ExecutorProvider.class),
                null);
    }
}
