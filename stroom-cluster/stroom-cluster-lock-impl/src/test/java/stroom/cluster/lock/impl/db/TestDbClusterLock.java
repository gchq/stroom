/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cluster.lock.impl.db;

import stroom.db.util.JooqUtil;
import stroom.test.common.util.db.DbTestUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

class TestDbClusterLock {

    @Test
    void testLock() throws InterruptedException {

        final ClusterLockDbConnProvider clusterLockDbConnProvider = DbTestUtil.getTestDbDatasource(
                new ClusterLockDbModule(), new ClusterLockDbConfig());

        final DbClusterLock dbClusterLock = new DbClusterLock(clusterLockDbConnProvider, ClusterLockConfig::new);
        final DbClusterLockThreads dbClusterLockThreads = new DbClusterLockThreads(dbClusterLock);

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final List<Integer> sequence = new ArrayList<>(3);

        // This thread should acquire the lock first stopping the second thread
        // from adding to the sequence until after this thread completes.
        final Thread thread1 = new Thread(() -> {
            dbClusterLockThreads.thread1("TEST", sequence);
            countDownLatch.countDown();
        });

        final Thread thread2 = new Thread(() -> {
            dbClusterLockThreads.thread2("TEST", sequence);
            countDownLatch.countDown();
        });

        // Start the threads.
        thread1.start();
        thread2.start();

        // Now make sure the sequence is as expected.
        // Use a timeout so if it goes wrong the test won't just sit there for ages.
        final boolean success = countDownLatch.await(20, TimeUnit.SECONDS);

        assertThat(success)
                .withFailMessage("Gave up waiting for the latch to count down")
                .isTrue();

        assertThat(sequence)
                .hasSize(3);
        assertThat(sequence.get(0))
                .isEqualTo(1);
        assertThat(sequence.get(1))
                .isEqualTo(2);
        assertThat(sequence.get(2))
                .isEqualTo(3);
    }

    @Test
    void testTryLock() throws InterruptedException {

        final ClusterLockDbConnProvider clusterLockDbConnProvider = DbTestUtil.getTestDbDatasource(
                new ClusterLockDbModule(), new ClusterLockDbConfig());

        final DbClusterLock dbClusterLock = new DbClusterLock(clusterLockDbConnProvider, ClusterLockConfig::new);
        final DbClusterTryLockThreads dbClusterTryLockThreads = new DbClusterTryLockThreads(dbClusterLock);

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final List<Integer> sequence = new ArrayList<>(3);

        // This thread should acquire the lock first stopping the second thread
        // from adding to the sequence until after this thread completes.
        final Thread thread1 = new Thread(() -> {
            dbClusterTryLockThreads.thread1("TEST", sequence);
            countDownLatch.countDown();
        });

        final Thread thread2 = new Thread(() -> {
            dbClusterTryLockThreads.thread2("TEST", sequence);
            countDownLatch.countDown();
        });

        // Start the threads.
        thread1.start();
        thread2.start();

        // Now make sure the sequence is as expected.
        // Use a timeout so if it goes wrong the test won't just sit there for ages.
        final boolean success = countDownLatch.await(20, TimeUnit.SECONDS);

        assertThat(success)
                .withFailMessage("Gave up waiting for the latch to count down")
                .isTrue();

        assertThat(sequence)
                .containsExactly(1, 2);
    }

    @Test
    void testOwnerTracking() {
        final ClusterLockDbConnProvider clusterLockDbConnProvider = DbTestUtil.getTestDbDatasource(
                new ClusterLockDbModule(), new ClusterLockDbConfig());

        final DbClusterLock dbClusterLock = new DbClusterLock(clusterLockDbConnProvider, ClusterLockConfig::new);
        final String lockName = "TRACKING_TEST";

        dbClusterLock.lock(lockName, () -> {
            JooqUtil.context(clusterLockDbConnProvider, context -> {
                final Optional<? extends org.jooq.Record> opt = context.select(
                                CLUSTER_LOCK.NODE_NAME,
                                CLUSTER_LOCK.THREAD_NAME,
                                CLUSTER_LOCK.LEASE_MS,
                                CLUSTER_LOCK.LOCK_TIME_MS)
                        .from(CLUSTER_LOCK)
                        .where(CLUSTER_LOCK.NAME.eq(lockName))
                        .fetchOptional();

                assertThat(opt.isPresent()).isTrue();
                final org.jooq.Record record = opt.get();
                assertThat(record.get(CLUSTER_LOCK.THREAD_NAME)).isEqualTo(Thread.currentThread().getName());
                assertThat(record.get(CLUSTER_LOCK.NODE_NAME)).isEqualTo("unknown");
                assertThat(record.get(CLUSTER_LOCK.LEASE_MS)).isNotNull();
            });
        });
    }

    @Test
    void testLockTTL() throws InterruptedException {
        final ClusterLockDbConnProvider clusterLockDbConnProvider = DbTestUtil.getTestDbDatasource(
                new ClusterLockDbModule(), new ClusterLockDbConfig());

        // Thread 1 uses a short 200ms lockTimeout so the DB LEASE_MS is set to 200ms.
        // This means thread 1's lease expires 200ms after it acquires the lock.
        final ClusterLockConfig shortLeaseConfig = new ClusterLockConfig(
                new ClusterLockDbConfig(),
                stroom.util.time.StroomDuration.ofMillis(200));
        final DbClusterLock dbClusterLock1 = new DbClusterLock(clusterLockDbConnProvider, () -> shortLeaseConfig);

        // Thread 2 uses a longer lockTimeout so that its acquisition retry window
        // is large enough to handle transient DB latency, while the COALESCE in
        // tryAcquireLock still reads thread 1's DB LEASE_MS = 200ms.
        final ClusterLockConfig longWaitConfig = new ClusterLockConfig(
                new ClusterLockDbConfig(),
                stroom.util.time.StroomDuration.ofMillis(5_000));
        final DbClusterLock dbClusterLock2 = new DbClusterLock(clusterLockDbConnProvider, () -> longWaitConfig);

        final String lockName = "TTL_TEST";

        final CountDownLatch thread1Acquired = new CountDownLatch(1);
        final CountDownLatch thread2Finished = new CountDownLatch(1);
        final List<String> results = new ArrayList<>();

        final Thread thread1 = new Thread(() -> {
            dbClusterLock1.lock(lockName, () -> {
                thread1Acquired.countDown();
                try {
                    // Hold the lock for 1 second. But the DB lease is only 200ms!
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // "thread1 done" is added regardless of interrupt (expected behaviour).
            });
        });

        final Thread thread2 = new Thread(() -> {
            try {
                thread1Acquired.await();
                // Wait another 300ms so thread 1's 200ms DB lease has definitely expired.
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Thread 2 should steal the expired lock on its first tryAcquireLock attempt
            // because LOCK_TIME_MS + COALESCE(LEASE_MS=200, 5000) = T1+200 < now=T1+300.
            dbClusterLock2.lock(lockName, () -> {
                results.add("thread2 acquired");
                thread2Finished.countDown();
            });
        });

        thread1.start();
        thread2.start();

        // Allow up to 5 seconds: thread startup + 300ms sleep + DB round-trip + safety margin.
        final boolean success = thread2Finished.await(5_000, TimeUnit.MILLISECONDS);
        assertThat(success).withFailMessage("Thread 2 failed to steal the lock after lease expired").isTrue();

        // At this point thread 2 has finished; thread 1 may still be sleeping.
        // Only thread2's result should be present at this instant.
        assertThat(results).contains("thread2 acquired");

        thread1.join();
        thread2.join();
    }
}

