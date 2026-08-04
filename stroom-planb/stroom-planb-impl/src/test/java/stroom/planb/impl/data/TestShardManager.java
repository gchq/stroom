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

import stroom.util.concurrent.StripedLock;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TestShardManager {

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
