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
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.StripedLock;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestShardManager {

    /**
     * Once closed, no shard may be created, as creating one opens an LMDB env that nothing
     * would then close — possibly on a dir whose env closeAll() has just closed.
     */
    @Test
    void closeAllPreventsFurtherShardUse(@TempDir final Path tempDir) {
        final PlanBDoc doc = PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-map")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder().build())
                .build();
        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get(Mockito.any(String.class))).thenReturn(doc);
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final ShardManager shardManager = new ShardManager(
                new ByteBuffers(byteBufferFactory),
                byteBufferFactory,
                planBDocCache,
                Mockito.mock(PlanBDocStore.class),
                null,
                () -> new PlanBConfig(tempDir.toAbsolutePath().toString()),
                new StatePaths(tempDir),
                null,
                new SimpleTaskContextFactory(),
                new ExecutorProvider() {
                    @Override
                    public Executor get() {
                        return Runnable::run;
                    }

                    @Override
                    public Executor get(final ThreadPool threadPool) {
                        return Runnable::run;
                    }
                });

        // Usable before closing.
        assertThatNoException().isThrownBy(() -> shardManager.get(doc.getName(), db -> null));

        shardManager.closeAll();

        assertThatThrownBy(() -> shardManager.get(doc.getName(), db -> null))
                .hasMessageContaining("closed");
    }

    /**
     * A shard whose doc has been deleted while it was not open can never be opened again: nothing
     * routes merges to it and no query can resolve its name. cleanup() only walks the map of OPEN
     * shards, so without a disk driven sweep its dir would be held for the life of the deployment,
     * invisible in the shard listing too.
     */
    @Test
    void cleanupDeletesDirsOfDeletedDocs(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        final Path deletedShard = createDir(statePaths.getShardDir(), "deleted-doc-uuid");
        final Path notFoundShard = createDir(statePaths.getShardDir(), "not-found-doc-uuid");
        final Path liveShard = createDir(statePaths.getShardDir(), "live-doc-uuid");
        final Path unreadableShard = createDir(statePaths.getShardDir(), "unreadable-doc-uuid");
        final Path deletedSnapshot = createDir(statePaths.getSnapshotDir(), "deleted-doc-uuid");

        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef("deleted-doc-uuid"))).thenReturn(null);
        Mockito.when(docStore.readDocument(docRef("not-found-doc-uuid")))
                .thenThrow(new DocumentNotFoundException(docRef("not-found-doc-uuid")));
        Mockito.when(docStore.readDocument(docRef("live-doc-uuid")))
                .thenReturn(doc("live-doc-uuid"));
        // Anything but a definite "not found" must leave the dir alone, or a transient docstore
        // failure would destroy the data of a shard that is still live.
        Mockito.when(docStore.readDocument(docRef("unreadable-doc-uuid")))
                .thenThrow(new RuntimeException("Some transient failure"));

        createShardManager(tempDir, docStore, Mockito.mock(PlanBDocCache.class)).cleanup();

        assertThat(deletedShard).doesNotExist();
        assertThat(notFoundShard).doesNotExist();
        assertThat(deletedSnapshot).doesNotExist();
        assertThat(liveShard).exists();
        assertThat(unreadableShard).exists();
    }

    /**
     * An OPEN shard's dir must only ever be deleted via the shard, which closes its env first;
     * unlinking files an env is still mapped on is undefined behaviour. Proven by the map entry
     * being cleared, which cleanup() only does when the shard's own delete() reported success,
     * and that closes the db before deleting the dir.
     */
    @Test
    void cleanupClosesAnOpenShardBeforeDeletingItsDir(@TempDir final Path tempDir) {
        final PlanBDoc doc = doc(UUID.randomUUID().toString());
        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get(Mockito.any(String.class))).thenReturn(doc);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        // The doc has gone, so the sweep would delete the dir if it did not first check the map.
        Mockito.when(docStore.readDocument(Mockito.any()))
                .thenThrow(new DocumentNotFoundException(docRef(doc.getUuid())));

        final ShardManager shardManager = createShardManager(tempDir, docStore, planBDocCache);
        // Opens an env on the shard dir.
        shardManager.get(doc.getName(), db -> null);
        final Path shardDir = new StatePaths(tempDir).getShardDir().resolve(doc.getUuid());
        assertThat(shardDir).exists();

        shardManager.cleanup();

        // Deleted, but by the shard, which closed its env first, rather than by the dir sweep.
        assertThat(shardDir).doesNotExist();
        assertThat(shardManager.getExistingShard(doc.getUuid())).isEmpty();
    }

    private DocRef docRef(final String uuid) {
        return DocRef.builder().type(PlanBDoc.TYPE).uuid(uuid).build();
    }

    private PlanBDoc doc(final String uuid) {
        return PlanBDoc
                .builder()
                .uuid(uuid)
                .name("test-map")
                .stateType(StateType.STATE)
                .settings(new StateSettings.Builder().build())
                .build();
    }

    private Path createDir(final Path parent, final String name) throws IOException {
        final Path dir = parent.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("data.mdb"), "plan b data for " + name);
        return dir;
    }

    private ShardManager createShardManager(final Path tempDir,
                                            final PlanBDocStore docStore,
                                            final PlanBDocCache planBDocCache) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        return new ShardManager(
                new ByteBuffers(byteBufferFactory),
                byteBufferFactory,
                planBDocCache,
                docStore,
                null,
                () -> new PlanBConfig(tempDir.toAbsolutePath().toString()),
                new StatePaths(tempDir),
                null,
                new SimpleTaskContextFactory(),
                new ExecutorProvider() {
                    @Override
                    public Executor get() {
                        return Runnable::run;
                    }

                    @Override
                    public Executor get(final ThreadPool threadPool) {
                        return Runnable::run;
                    }
                });
    }

    /**
     * A node that stores shards publishes its snapshot as a file directly in the doc dir. Deleting that on
     * startup leaves it with nothing to serve, and as it is only recreated when a new one is required, a shard
     * that receives no further writes may never publish one again. See gh-5689.
     */
    @Test
    void deleteFetchedSnapshotsKeepsPublishedSnapshot(@TempDir final Path tempDir) throws IOException {
        final Path snapshotDir = tempDir.resolve("snapshots");
        final Path docDir = snapshotDir.resolve("2fd7f1a1-0e1e-4b1e-9f0a-2b0d5f6c7a8b");
        Files.createDirectories(docDir);

        // A snapshot this node publishes for other nodes to fetch.
        final Path publishedZip = docDir.resolve("snapshot.zip");
        Files.writeString(publishedZip, "published");

        // A snapshot previously fetched from the node that stores the shard.
        final Path fetchedDir = docDir.resolve(DateUtil.createFileDateTimeString(Instant.now()));
        Files.createDirectories(fetchedDir);
        final Path fetchedData = fetchedDir.resolve("data.mdb");
        Files.writeString(fetchedData, "fetched");

        ShardManager.deleteFetchedSnapshots(snapshotDir);

        assertThat(publishedZip).exists();
        assertThat(fetchedDir).doesNotExist();
        assertThat(docDir).exists();
    }

    @Test
    void deleteFetchedSnapshotsRemovesFetchesForAllDocs(@TempDir final Path tempDir) throws IOException {
        final Path snapshotDir = tempDir.resolve("snapshots");
        final Path fetchedOne = snapshotDir.resolve("uuid-1").resolve("2026-08-04T10-23-22-413Z");
        final Path fetchedTwo = snapshotDir.resolve("uuid-2").resolve("2026-08-04T10-33-22-413Z");
        Files.createDirectories(fetchedOne);
        Files.createDirectories(fetchedTwo);

        ShardManager.deleteFetchedSnapshots(snapshotDir);

        assertThat(fetchedOne).doesNotExist();
        assertThat(fetchedTwo).doesNotExist();
    }

    @Test
    void deleteFetchedSnapshotsToleratesMissingDir(@TempDir final Path tempDir) {
        assertThatNoException()
                .isThrownBy(() -> ShardManager.deleteFetchedSnapshots(tempDir.resolve("does-not-exist")));
    }

    /**
     * Creating a shard fetches a snapshot over HTTP with no client side timeout. Doing that inside
     * ConcurrentHashMap.computeIfAbsent holds the bin lock for the duration, so an unrelated shard whose key
     * hashes to the same bin can't be looked up until the fetch finishes. Creation must happen outside the map.
     * See gh-5689.
     */
    @Test
    void slowShardCreationDoesNotBlockAnotherShard() throws Exception {
        final Map<String, String> shardMap = new ConcurrentHashMap<>();
        final StripedLock creationLocks = new StripedLock();
        final CountDownLatch slowStarted = new CountDownLatch(1);
        final CountDownLatch releaseSlow = new CountDownLatch(1);

        final CompletableFuture<String> slow = CompletableFuture.supplyAsync(() ->
                getOrCreate(shardMap, creationLocks, "slow-uuid", () -> {
                    slowStarted.countDown();
                    await(releaseSlow);
                    return "slow-shard";
                }));

        assertThat(slowStarted.await(10, TimeUnit.SECONDS)).isTrue();

        // A different shard must be creatable while the slow one is still going.
        final CompletableFuture<String> fast = CompletableFuture.supplyAsync(() ->
                getOrCreate(shardMap, creationLocks, "fast-uuid", () -> "fast-shard"));

        assertThat(fast.get(10, TimeUnit.SECONDS)).isEqualTo("fast-shard");

        releaseSlow.countDown();
        assertThat(slow.get(10, TimeUnit.SECONDS)).isEqualTo("slow-shard");
    }

    /**
     * Two callers racing for the same shard must not both build one, as discarding the loser would mean
     * releasing it, and releasing a StoreShard deletes the shard data.
     */
    @Test
    void concurrentCallersForSameShardCreateItOnce() throws Exception {
        final Map<String, String> shardMap = new ConcurrentHashMap<>();
        final StripedLock creationLocks = new StripedLock();
        final AtomicInteger creations = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);

        final int threads = 20;
        final CompletableFuture<?>[] futures = new CompletableFuture<?>[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                await(start);
                return getOrCreate(shardMap, creationLocks, "same-uuid", () -> {
                    creations.incrementAndGet();
                    return "shard";
                });
            });
        }

        start.countDown();
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        assertThat(creations.get()).isEqualTo(1);
        for (final CompletableFuture<?> future : futures) {
            assertThat(future.get()).isEqualTo("shard");
        }
    }

    /**
     * Mirrors ShardManager.getOrCreateShard. Kept here rather than reaching into ShardManager, which needs a
     * large set of collaborators to build, so that the locking behaviour itself can be exercised.
     */
    private static <T> T getOrCreate(final Map<String, T> map,
                                     final StripedLock locks,
                                     final String key,
                                     final Supplier<T> creator) {
        final T existing = map.get(key);
        if (existing != null) {
            return existing;
        }

        final Lock lock = locks.getLockForKey(key);
        lock.lock();
        try {
            final T created = map.get(key);
            if (created != null) {
                return created;
            }
            final T value = creator.get();
            map.put(key, value);
            return value;
        } finally {
            lock.unlock();
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
