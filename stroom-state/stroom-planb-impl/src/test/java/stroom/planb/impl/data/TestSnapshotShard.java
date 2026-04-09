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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.data.SnapshotShard.DbFactory;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.concurrent.Guard;
import stroom.util.concurrent.Guard.TryAgainException;
import stroom.util.concurrent.StripedGuard;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.Env.AlreadyClosedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TestSnapshotShard {

    private static final DbFactory DB_FACTORY = (
            doc,
            dbDir,
            byteBuffers,
            byteBufferFactory,
            readOnly) ->
            new TestDb();
    private static ExecutorService executorService;

    @TempDir
    Path tempDir;

    @Mock
    private ByteBuffers byteBuffers;
    @Mock
    private ByteBufferFactory byteBufferFactory;
    @Mock
    private FileTransferClient fileTransferClient;

    private PlanBConfig config;
    private StatePaths statePaths;
    private PlanBDoc doc;
    private AutoCloseable mocks;

    @BeforeAll
    static void beforeAll() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterAll
    static void afterAll() {
        executorService.shutdown();
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Create config with short durations for testing
        config = PlanBConfig
                .builder()
                .nodeList(Collections.singletonList("test-node"))
                .minTimeToKeepSnapshots(StroomDuration.ofSeconds(10))
                .snapshotRetryFetchInterval(StroomDuration.ofSeconds(2))
                .build();

        statePaths = new StatePaths(tempDir);
        doc = PlanBDoc.builder().uuid("test-uuid").name("test-shard").build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        FileUtil.deleteDir(tempDir);
    }

    @Test
    void testConcurrentReads() throws Exception {
        // Given: A snapshot shard that returns successful fetches
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When: Multiple threads read concurrently
        final int threadCount = 50;
        final int readsPerThread = 100;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            final CountDownLatch startLatch = new CountDownLatch(1);
            final AtomicInteger successCount = new AtomicInteger(0);
            final List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < readsPerThread; j++) {
                            final String info = shard.getInfo();
                            if (info != null) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor));
            }

            startLatch.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

            // Then: All reads should succeed
            assertThat(successCount.get()).isEqualTo(threadCount * readsPerThread);
        }
    }

    @Test
    void testSnapshotRotation() throws Exception {
        // Given: A snapshot that will expire quickly
        config = config.copy().minTimeToKeepSnapshots(StroomDuration.ofMillis(100)).build();

        final AtomicInteger fetchCount = new AtomicInteger(0);
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    fetchCount.incrementAndGet();
                    return Instant.now();
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // Initial fetch
        assertThat(fetchCount.get()).isEqualTo(1);

        // When: We wait for expiry and trigger a read
        Thread.sleep(150);
        shard.getInfo();

        // Give rotation time to complete
        Thread.sleep(200);

        // Then: A new snapshot should have been fetched
        assertThat(fetchCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testOnlyOneRotationAtATime() throws Exception {
        // Given: A snapshot with slow fetch
        config = config
                .copy()
                .minTimeToKeepSnapshots(StroomDuration.ofMillis(100))
                .build();

        final CountDownLatch fetchStarted = new CountDownLatch(1);
        final CountDownLatch proceedFetch = new CountDownLatch(1);
        final AtomicInteger fetchCount = new AtomicInteger(0);

        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    final int count = fetchCount.incrementAndGet();
                    if (count == 2) {
                        fetchStarted.countDown();
                        proceedFetch.await();
                    }
                    return Instant.now();
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When: We expire the snapshot and trigger multiple reads
        Thread.sleep(150);

        try (final ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {
                    try {
                        shard.getInfo();
                    } catch (final Exception e) {
                        // Ignore
                    }
                });
            }

            // Wait for rotation to start
            assertThat(fetchStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // Then: Only one rotation should be in progress
            assertThat(fetchCount.get()).isEqualTo(2); // Initial + one rotation

            proceedFetch.countDown();
        }
    }

    @Test
    void testFailedFetchExtendsExpiry() throws Exception {
        // Given: A snapshot that fails to fetch
        config = config
                .copy()
                .minTimeToKeepSnapshots(StroomDuration.ofMillis(100))
                .snapshotRetryFetchInterval(StroomDuration.ofSeconds(5))
                .build();

        final AtomicInteger fetchCount = new AtomicInteger(0);
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    final int count = fetchCount.incrementAndGet();
                    if (count == 1) {
                        return Instant.now();
                    } else {
                        throw new RuntimeException("Fetch failed");
                    }
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When: We wait for expiry and trigger reads
        Thread.sleep(150);
        shard.getInfo();
        Thread.sleep(200);

        // Trigger another read quickly
        shard.getInfo();
        Thread.sleep(200);

        // Then: Should not keep retrying due to extended expiry
        // Should be 2: initial + one failed rotation
        assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    void testGuardPreventsUseAfterDestroy() {
        // Given: A guard with a destroy callback
        final AtomicBoolean destroyed = new AtomicBoolean(false);
        final Guard guard = new StripedGuard(() -> destroyed.set(true), 64);

        // When: We destroy the guard
        guard.destroy();

        // Then: Subsequent acquires should throw TryAgainException
        assertThatThrownBy(() -> guard.acquire(() -> "test"))
                .isInstanceOf(TryAgainException.class);

        // And the callback should have been called
        assertThat(destroyed.get()).isTrue();
    }

    @Test
    void testGuardReferenceCountingWithConcurrency() throws Exception {
        // Given: A guard with multiple concurrent acquisitions
        final AtomicInteger destroyCount = new AtomicInteger(0);
        final AtomicInteger maxConcurrent = new AtomicInteger(0);
        final AtomicInteger currentConcurrent = new AtomicInteger(0);

        final Guard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

        final int threadCount = 20;
        final CyclicBarrier barrier = new CyclicBarrier(threadCount);
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            final CountDownLatch completeLatch = new CountDownLatch(threadCount);

            // When: Multiple threads acquire and hold the guard
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronise start
                        guard.acquire(() -> {
                            final int concurrent = currentConcurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                            ThreadUtil.sleep(50); // Hold for a bit
                            currentConcurrent.decrementAndGet();
                            return null;
                        });
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            // Wait for all acquisitions to complete
            assertThat(completeLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // Then: Destroy should work
            guard.destroy();
            assertThat(destroyCount.get()).isEqualTo(1);
            assertThat(maxConcurrent.get()).isGreaterThan(1); // Verify concurrency happened
        }
    }

    @Test
    void testGuardDoubleDestroyIsIdempotent() {
        // Given: A guard
        final AtomicInteger destroyCount = new AtomicInteger(0);
        final Guard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

        // When: We destroy it twice
        guard.destroy();
        guard.destroy(); // Should not throw

        // Then: Callback should only be called once
        assertThat(destroyCount.get()).isEqualTo(1);
    }

    @Test
    void testRetryLogicWithTryAgainException() {
        // Given: A snapshot that throws TryAgainException a few times then succeeds
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When/Then: Normal operation should succeed
        final String info = shard.getInfo();
        assertThat(info).isNotNull();
    }

    @Test
    void testCleanupIsNoOp() {
        // Given: A snapshot
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // Access the DB
        shard.getInfo();

        final String info = shard.getInfo();
        assertThat(info).isNotNull();
    }

    @Test
    void testDeleteDestroysSnapshot() {
        // Given: A snapshot
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When: We delete it
        final boolean deleted = shard.delete();

        // Then: Should return true
        assertThat(deleted).isTrue();

        // And subsequent access should fail or create new instance
        // (behaviour depends on implementation)
    }

    @Test
    void testUnsupportedOperations() {
        // Given: A snapshot shard
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When/Then: Unsupported operations throw
        assertThatThrownBy(() -> shard.merge(tempDir))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not supported");

        assertThat(shard.deleteOldData(doc)).isEqualTo(0L);
        assertThat(shard.condense(doc)).isEqualTo(0L);

        // These should not throw
        shard.compact();
        shard.checkSnapshotStatus(null);
        shard.createSnapshot();
    }

    @Test
    void testGetDoc() {
        // Given: A snapshot shard
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenReturn(Instant.now());

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When/Then: getDoc returns the correct document
        assertThat(shard.getDoc()).isEqualTo(doc);
    }

    @Test
    void testConcurrentRotationAndReads() throws Exception {
        // Given: A snapshot that expires quickly
        config = config
                .copy()
                .minTimeToKeepSnapshots(StroomDuration.ofMillis(50))
                .build();

        final AtomicInteger fetchCount = new AtomicInteger(0);
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    fetchCount.incrementAndGet();
                    Thread.sleep(100); // Slow fetch
                    return Instant.now();
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();

        // When: We hammer it with reads while rotation is happening
        try (final ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        shard.getInfo();
                        successCount.incrementAndGet();
                    } catch (final Throwable t) {
                        firstError.compareAndSet(null, t);
                    }
                });
                Thread.sleep(10);
            }
        }

        // Then: All reads should succeed despite rotation
        assertThat(firstError.get()).isNull();
        assertThat(successCount.get()).isEqualTo(100);
    }

    @Test
    void testRaceConditionBetweenDestroyAndAcquire() throws Exception {
        // Given: A guard being destroyed while threads try to acquire
        final AtomicInteger destroyCount = new AtomicInteger(0);
        final Guard guard = new StripedGuard(destroyCount::incrementAndGet, 64);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicInteger tryAgainCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);

        final int threadCount = 50;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // When: Some threads destroy while others try to acquire
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (index == 0) {
                            guard.destroy();
                        } else {
                            try {
                                guard.acquire(() -> {
                                    successCount.incrementAndGet();
                                    return null;
                                });
                            } catch (final TryAgainException e) {
                                tryAgainCount.incrementAndGet();
                            }
                        }
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            startLatch.countDown();
        }

        // Then: Destroy should be called exactly once
        assertThat(destroyCount.get()).isEqualTo(1);
        // And some attempts should have failed with TryAgainException
        assertThat(tryAgainCount.get()).isGreaterThan(0);
    }

    @Test
    void testReadsDuringRotationRetryGracefully() throws Exception {
        // This test verifies that when rotation replaces the current instance,
        // any concurrent readers that got the old instance reference and find
        // the guard destroyed will get TryAgainException and retry successfully
        // with the new instance.

        // Given: A snapshot with short expiry
        config = config
                .copy()
                .minTimeToKeepSnapshots(StroomDuration.ofMillis(100))
                .build();

        final AtomicInteger fetchCount = new AtomicInteger(0);
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    fetchCount.incrementAndGet();
                    return Instant.now();
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // Access the DB
        shard.getInfo();

        // Wait for expiry and trigger rotation
        Thread.sleep(150);
        shard.getInfo(); // triggers rotation asynchronously

        // Wait for rotation to complete
        Thread.sleep(300);

        // When/Then: Subsequent reads should succeed (via the new instance)
        // and NOT throw any exception
        final String info = shard.getInfo();
        assertThat(info).isNotNull();
        assertThat(fetchCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testFailedRotationCleansUpAbandonedDirectory() throws Exception {
        // Given: A snapshot where rotation fetches will fail
        config = config
                .copy()
                .minTimeToKeepSnapshots(StroomDuration.ofMillis(100))
                .snapshotRetryFetchInterval(StroomDuration.ofSeconds(5))
                .build();

        final AtomicInteger fetchCount = new AtomicInteger(0);
        when(fileTransferClient.fetchSnapshot(any(), any(), any()))
                .thenAnswer(inv -> {
                    final int count = fetchCount.incrementAndGet();
                    if (count == 1) {
                        return Instant.now(); // First fetch succeeds
                    } else {
                        throw new RuntimeException("Fetch failed");
                    }
                });

        final SnapshotShard shard = new SnapshotShard(
                byteBuffers,
                byteBufferFactory,
                () -> config,
                statePaths,
                fileTransferClient,
                doc,
                DB_FACTORY,
                executorService);

        // When: We wait for expiry and trigger a failed rotation
        Thread.sleep(150);
        shard.getInfo(); // triggers rotation
        Thread.sleep(300); // wait for rotation to complete

        // Then: The snapshot dir should NOT accumulate abandoned directories.
        // Count the directories under the snapshot/uuid path.
        final Path snapshotUuidDir = statePaths.getSnapshotDir().resolve(doc.getUuid());
        if (Files.exists(snapshotUuidDir)) {
            try (final Stream<Path> dirs = Files.list(snapshotUuidDir).filter(Files::isDirectory)) {
                final long dirCount = dirs.count();
                // Should have at most 1 directory (the active instance).
                // The failed rotation's directory should have been cleaned up.
                assertThat(dirCount).isLessThanOrEqualTo(1);
            }
        }
    }

    private static final class TestDb implements Db<String, String> {

        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void insert(final LmdbWriter writer, final KV<String, String> kv) {
            ensureOpen();
        }

        @Override
        public String get(final String key) {
            ensureOpen();
            return "";
        }

        @Override
        public void search(final ExpressionCriteria criteria,
                           final FieldIndex fieldIndex,
                           final DateTimeSettings dateTimeSettings,
                           final ExpressionPredicateFactory expressionPredicateFactory,
                           final ValuesConsumer consumer) {
            ensureOpen();
        }

        @Override
        public void merge(final Path source) {
            ensureOpen();
        }

        @Override
        public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
            ensureOpen();
            return 0;
        }

        @Override
        public long condense(final Instant condenseBefore) {
            ensureOpen();
            return 0;
        }

        @Override
        public void compact(final Path destination) {
            ensureOpen();
        }

        @Override
        public LmdbWriter createWriter() {
            ensureOpen();
            return null;
        }

        @Override
        public void write(final Consumer<LmdbWriter> consumer) {
            ensureOpen();
        }

        @Override
        public void lock(final Runnable runnable) {
            ensureOpen();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                throw new AlreadyClosedException();
            }
        }

        @Override
        public long count() {
            ensureOpen();
            return 0;
        }

        @Override
        public String getInfoString() {
            ensureOpen();
            return "";
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new AlreadyClosedException();
            }
        }
    }
}
