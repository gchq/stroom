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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestShardPass {

    private static final List<Integer> FOUR_SHARDS = List.of(0, 1, 2, 3);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void everyShardIsWorkedUnderItsOwnLock() {
        final Locks locks = Locks.allAvailable();
        final Set<Integer> worked = ConcurrentHashMap.newKeySet();

        final Set<Integer> busy = pass(locks).run(FOUR_SHARDS, shard -> "lock-" + shard,
                worked::add, "testing");

        assertThat(worked).containsExactlyInAnyOrderElementsOf(FOUR_SHARDS);
        assertThat(locks.taken).containsExactlyInAnyOrder("lock-0", "lock-1", "lock-2", "lock-3");
        assertThat(busy).as("nothing was held by anyone else").isEmpty();
    }

    @Test
    void aShardHeldBySomethingElseComesBack() {
        // A lock another node holds presents as tryLock returning without running anything.
        final Locks locks = Locks.except(2);
        final Set<Integer> worked = ConcurrentHashMap.newKeySet();

        final Set<Integer> busy = pass(locks).run(FOUR_SHARDS, shard -> "lock-" + shard,
                worked::add, "testing");

        assertThat(busy).as("said so, rather than looking like a shard with nothing to do")
                .containsExactly(2);
        assertThat(worked).as("the shards nobody held were still worked").containsExactlyInAnyOrder(0, 1, 3);
    }

    @Test
    void onlyTheShardsGivenAreAskedFor() {
        final Locks locks = Locks.allAvailable();
        final Set<Integer> worked = ConcurrentHashMap.newKeySet();

        pass(locks).run(List.of(1, 3), shard -> "lock-" + shard, worked::add, "testing");

        assertThat(worked).containsExactlyInAnyOrder(1, 3);
        assertThat(locks.taken).as("a shard left out is never locked, so it costs no contention")
                .containsExactlyInAnyOrder("lock-1", "lock-3");
    }

    @Test
    void oneShardFailingLeavesTheRestWorked() {
        final Locks locks = Locks.allAvailable();
        final Set<Integer> worked = ConcurrentHashMap.newKeySet();

        final Set<Integer> busy = pass(locks).run(FOUR_SHARDS, shard -> "lock-" + shard, shard -> {
            if (shard == 1) {
                throw new IllegalStateException("as happens");
            }
            worked.add(shard);
        }, "testing");

        assertThat(worked).containsExactlyInAnyOrder(0, 2, 3);
        assertThat(busy).as("it ran and failed, which is not the same as never getting the shard")
                .isEmpty();
    }

    @Test
    void shardsAreWorkedAtTheSameTime() throws InterruptedException {
        final Locks locks = Locks.allAvailable();
        // Each shard waits for all four to arrive. One after another this never completes.
        final CountDownLatch arrived = new CountDownLatch(4);
        final CountDownLatch allArrived = new CountDownLatch(1);

        final Thread runner = new Thread(() -> pass(locks).run(FOUR_SHARDS, shard -> "lock-" + shard,
                shard -> {
                    arrived.countDown();
                    try {
                        allArrived.await(10, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "testing"));
        runner.start();

        assertThat(arrived.await(10, TimeUnit.SECONDS))
                .as("all four shards were in the work at once")
                .isTrue();
        allArrived.countDown();
        runner.join(10_000);
    }

    @Test
    void anInterruptedPassStartsNoMoreShards() throws InterruptedException {
        final Locks locks = Locks.allAvailable();
        final Set<Integer> worked = ConcurrentHashMap.newKeySet();

        final Thread runner = new Thread(() -> {
            Thread.currentThread().interrupt();
            pass(locks).run(FOUR_SHARDS, shard -> "lock-" + shard, worked::add, "testing");
        });
        runner.start();
        runner.join(10_000);

        assertThat(worked).as("interrupted before the first shard, so none were started").isEmpty();
    }

    private ShardPass pass(final Locks locks) {
        return new ShardPass(locks, new MockSecurityContext(), executor);
    }


    // --------------------------------------------------------------------------------


    // Stands in for the cluster lock. Says which locks were asked for, and can refuse one the way a
    // lock held by another node is refused — by returning without running anything.
    private static class Locks implements ClusterLockService {

        private final Set<String> taken = ConcurrentHashMap.newKeySet();
        private final Set<Integer> unavailable;

        private Locks(final Set<Integer> unavailable) {
            this.unavailable = unavailable;
        }

        private static Locks allAvailable() {
            return new Locks(Set.of());
        }

        private static Locks except(final int shard) {
            return new Locks(Set.of(shard));
        }

        @Override
        public void tryLock(final String lockName, final Runnable runnable) {
            taken.add(lockName);
            final int shard = Integer.parseInt(lockName.substring(lockName.lastIndexOf('-') + 1));
            if (!unavailable.contains(shard)) {
                runnable.run();
            }
        }

        @Override
        public void lock(final String lockName, final Runnable runnable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T lockResult(final String lockName, final Supplier<T> supplier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteLocks(final String prefix) {
            throw new UnsupportedOperationException();
        }
    }
}
