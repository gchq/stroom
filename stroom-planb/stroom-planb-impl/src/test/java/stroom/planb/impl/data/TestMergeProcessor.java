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

import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.dao.StatePaths;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestMergeProcessor {

    private static final String DOC_UUID = "6a04c9a1-1cc9-42b1-b871-6d245a4a9724";

    private ExecutorService executorService;
    private ExecutorProvider executorProvider;

    @BeforeEach
    void beforeEach() {
        executorService = Executors.newCachedThreadPool();
        executorProvider = new ExecutorProvider() {

            @Override
            public Executor get() {
                return executorService;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return executorService;
            }
        };
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        // Interrupt the queue consumers and the unzip loop, which otherwise block awaiting more data.
        executorService.shutdownNow();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Data that has been queued for merge only exists in the merging dir, as the staged zip it arrived in is
     * deleted when the data is queued. It must therefore survive a restart so that interrupted merges can be
     * rerun. The unzip dir on the other hand is transient, as anything in it still exists as a staged zip, so
     * anything left behind by the last run is deleted. See gh-5696.
     */
    @Test
    void constructorKeepsQueuedMergeData(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final Path queuedFile = createQueuedDir(statePaths, 1).resolve("data.mdb");
        Files.writeString(queuedFile, "queued");

        final Path unzipLeftover = statePaths.getUnzipDir().resolve("001");
        Files.createDirectories(unzipLeftover);
        Files.writeString(unzipLeftover.resolve("data.mdb"), "leftover");

        createMergeProcessor(statePaths, Mockito.mock(ShardManager.class));

        assertThat(queuedFile).exists();
        assertThat(unzipLeftover).doesNotExist();
    }

    /**
     * Queue creation is serialised by a striped lock rather than by
     * {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent}, so it double checks the map under that
     * lock. Two threads racing for the same doc must still end up sharing one queue; two queues on the same
     * dir would each hand the same dir to a consumer and merge it twice. See gh-5689.
     */
    @Test
    void concurrentCreationSharesOneQueuePerDoc(@TempDir final Path tempDir) throws Exception {
        final MergeProcessor mergeProcessor = createMergeProcessor(
                new StatePaths(tempDir), Mockito.mock(ShardManager.class));

        final int threads = 16;
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final List<Future<DirQueue>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executorService.submit(() -> {
                // Release them all at once so they genuinely contend.
                barrier.await(10, TimeUnit.SECONDS);
                return mergeProcessor.getOrCreateDirQueue(DOC_UUID);
            }));
        }

        final DirQueue first = futures.getFirst().get(10, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        for (final Future<DirQueue> future : futures) {
            assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(first);
        }
    }

    /**
     * Dirs left on a merge queue by the last run, e.g. because merging was interrupted by shutdown, are merged
     * when the merge job first runs after boot. See gh-5696.
     */
    @Test
    void mergeResumesQueuedDataAfterRestart(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final Path queuedDir1 = createQueuedDir(statePaths, 1);
        final Path queuedDir2 = createQueuedDir(statePaths, 2);

        final List<Path> merged = new CopyOnWriteArrayList<>();
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        final Shard shard = Mockito.mock(Shard.class);
        Mockito.when(shardManager.getShardForDocUuid(DOC_UUID)).thenReturn(shard);
        Mockito.doAnswer(invocation -> {
            merged.add(invocation.getArgument(0));
            return null;
        }).when(shard).merge(Mockito.any());

        final MergeProcessor mergeProcessor = createMergeProcessor(statePaths, shardManager);
        mergeProcessor.merge();

        waitUntil(() -> merged.size() == 2, "both queued dirs to be merged");
        waitUntil(() -> !Files.exists(queuedDir1) && !Files.exists(queuedDir2), "merged dirs to be deleted");
        assertThat(merged).containsExactly(queuedDir1, queuedDir2);
    }

    /**
     * An interrupted merge must leave the source dir in place so the merge can be rerun, and must stop the
     * queue consumer rather than have it move on and consume the rest of the queue while the system is
     * shutting down. A later run of the merge job restarts consumption, and dirs skipped by the interrupted
     * consumer are merged after a restart. See gh-5696.
     */
    @Test
    void interruptedMergeKeepsDataAndStopsConsumer(@TempDir final Path tempDir)
            throws IOException, InterruptedException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final Path queuedDir1 = createQueuedDir(statePaths, 1);
        final Path queuedDir2 = createQueuedDir(statePaths, 2);

        final List<Path> merged = new CopyOnWriteArrayList<>();
        final AtomicInteger mergeAttempts = new AtomicInteger();
        final AtomicBoolean interrupting = new AtomicBoolean(true);
        final CountDownLatch firstMergeStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirstMerge = new CountDownLatch(1);
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        final Shard shard = Mockito.mock(Shard.class);
        Mockito.when(shardManager.getShardForDocUuid(DOC_UUID)).thenReturn(shard);
        Mockito.doAnswer(invocation -> {
            mergeAttempts.incrementAndGet();
            if (interrupting.get()) {
                // Hold the merge in progress until the test releases it. The merge job restarts any
                // consumer that has already died, so the interruption must not happen until the job's
                // restart sweep has run, else the sweep may legitimately restart the consumer and a
                // second merge attempt is made. Holding the first merge open until merge() has returned
                // makes the timing deterministic.
                firstMergeStarted.countDown();
                releaseFirstMerge.await(10, TimeUnit.SECONDS);
                throw new UncheckedInterruptedException(new InterruptedException("Interrupted at shutdown"));
            }
            merged.add(invocation.getArgument(0));
            return null;
        }).when(shard).merge(Mockito.any());

        final MergeProcessor mergeProcessor = createMergeProcessor(statePaths, shardManager);
        mergeProcessor.merge();

        // merge() has returned, so its consumer restart sweep has completed while the consumer was still
        // alive. Now let the first merge fail.
        assertThat(firstMergeStarted.await(10, TimeUnit.SECONDS)).isTrue();
        releaseFirstMerge.countDown();

        // The first merge is interrupted. The consumer must stop rather than churn through the rest of the
        // queue, and the interrupted dir must remain on disk.
        waitQuietPeriod();
        assertThat(merged).isEmpty();
        assertThat(mergeAttempts).hasValue(1);
        assertThat(queuedDir1).exists();
        assertThat(queuedDir2).exists();

        // The next run of the merge job restarts the consumer, which continues from where the queue got to.
        interrupting.set(false);
        mergeProcessor.merge();
        waitUntil(() -> merged.contains(queuedDir2), "queued dir 2 to be merged after consumer restart");
        // The consumer deletes the dir only once shard.merge() has returned, and the mock records
        // the call from inside merge(), so waiting for the record alone would race the delete.
        waitUntil(() -> !Files.exists(queuedDir2), "queued dir 2 to be deleted after merging");

        // The dir the interrupted consumer skipped stays on disk until a restart, when the recreated queue
        // finds it again.
        assertThat(queuedDir1).exists();
        final MergeProcessor rebooted = createMergeProcessor(statePaths, shardManager);
        rebooted.merge();
        waitUntil(() -> merged.contains(queuedDir1), "queued dir 1 to be merged after reboot");
        waitUntil(() -> !Files.exists(queuedDir1), "queued dir 1 to be deleted after merging");
    }

    /**
     * Merge status records may only be pruned while no replayable copy of any merge source exists,
     * i.e. the staging store is drained and the doc's merge queue holds no dirs on disk.
     * See docs/merge-idempotency-design.md.
     */
    @Test
    void mergeStatusPruningIsGatedOnQuiescence(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        final MergeProcessor mergeProcessor = createMergeProcessor(statePaths, shardManager);

        mergeProcessor.maintainShards();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Predicate<String>> quiescenceCaptor = ArgumentCaptor.forClass(Predicate.class);
        Mockito.verify(shardManager)
                .deleteOldMergeStatus(quiescenceCaptor.capture(), Mockito.any(Instant.class));
        final Predicate<String> quiescent = quiescenceCaptor.getValue();

        // Nothing on disk for the doc: pruning is allowed.
        assertThat(quiescent.test(DOC_UUID)).isTrue();

        // A dir on the doc's merge queue blocks pruning, even though no in-memory queue is consuming it.
        createQueuedDir(statePaths, 1);
        assertThat(quiescent.test(DOC_UUID)).isFalse();
        FileUtil.deleteDir(statePaths.getMergingDir().resolve(DOC_UUID));
        assertThat(quiescent.test(DOC_UUID)).isTrue();

        // A zip waiting in the staging store blocks pruning for all docs.
        final Path stagedZip = statePaths.getStagingDir().resolve("0").resolve("001.zip");
        Files.createDirectories(stagedZip.getParent());
        Files.writeString(stagedZip, "zip");
        assertThat(quiescent.test(DOC_UUID)).isFalse();
    }

    private MergeProcessor createMergeProcessor(final StatePaths statePaths, final ShardManager shardManager) {
        return new MergeProcessor(
                statePaths,
                MockSecurityContext.getInstance(),
                new SimpleTaskContextFactory(),
                shardManager,
                executorProvider,
                PlanBConfig::new);
    }

    /**
     * Create a dir on the merge queue for {@link #DOC_UUID} as {@link DirQueue} would have laid it out, with a
     * single file in it.
     */
    private Path createQueuedDir(final StatePaths statePaths, final long id) throws IOException {
        final Path queuedDir = DirUtil.createPath(statePaths.getMergingDir().resolve(DOC_UUID), id);
        Files.createDirectories(queuedDir);
        Files.writeString(queuedDir.resolve("data.mdb"), "data " + id);
        return queuedDir;
    }

    private void waitUntil(final BooleanSupplier condition, final String message) {
        final Instant end = Instant.now().plusSeconds(10);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(end)) {
                throw new AssertionError("Timed out waiting for " + message);
            }
            sleep(10);
        }
    }

    /**
     * Give a queue consumer that is wrongly still running time to consume more of the queue.
     */
    private void waitQuietPeriod() {
        sleep(500);
    }

    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }
}
