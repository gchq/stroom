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

package stroom.analytics.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pooled object is an LMDB env with an async writer behind it, so the borrow/release protocol
 * decides when an env is opened and closed. Pins that only one env exists per key at a time, that
 * one is never closed while borrowed, and that nothing can borrow an env that is being closed.
 */
class TestDuplicateCheckStorePool {

    private static final String KEY = "a-rule-uuid";

    @Test
    void sharesOneObjectUntilTheLastReferenceIsReleased() {
        final Pool pool = new Pool();

        final String first = pool.pool.borrow(KEY);
        final String second = pool.pool.borrow(KEY);

        assertThat(second).isSameAs(first);
        assertThat(pool.created).hasSize(1);

        pool.pool.release(KEY);
        // Still borrowed once, so the env must stay open.
        assertThat(pool.destroyed).isEmpty();

        pool.pool.release(KEY);
        assertThat(pool.destroyed).containsExactly(first);
    }

    /**
     * The object is removed from the map BEFORE the destruction handler runs, so a borrow that
     * arrives while an env is being closed gets a fresh env rather than the one being closed.
     * Reentrant because the striped lock is held for the whole of release().
     */
    @Test
    void borrowDuringDestructionGetsAFreshObject() {
        final Pool pool = new Pool();
        final AtomicReference<String> borrowedDuringDestruction = new AtomicReference<>();

        final String original = pool.pool.borrow(KEY);
        pool.onDestroy = destroyed -> borrowedDuringDestruction.set(pool.pool.borrow(KEY));

        pool.pool.release(KEY);

        assertThat(borrowedDuringDestruction.get()).isNotNull();
        assertThat(borrowedDuringDestruction.get()).isNotSameAs(original);
        assertThat(pool.created).hasSize(2);
    }

    @Test
    void doIfNotInUseDoesNotRunWhileTheObjectIsBorrowed() {
        final Pool pool = new Pool();
        pool.pool.borrow(KEY);

        assertThat(pool.pool.doIfNotInUse(KEY, () -> {
            throw new AssertionError("Must not run while the object is borrowed");
        })).isFalse();

        pool.pool.release(KEY);

        final AtomicBoolean ran = new AtomicBoolean();
        assertThat(pool.pool.doIfNotInUse(KEY, () -> ran.set(true))).isTrue();
        assertThat(ran).isTrue();
    }

    /**
     * The point of doIfNotInUse is to delete an object's files knowing nothing can open an env on
     * them while the action runs, so a borrow arriving mid action must wait rather than create one.
     */
    @Test
    void doIfNotInUseBlocksABorrowForTheSameKey() throws Exception {
        final Pool pool = new Pool();
        final AtomicBoolean actionFinished = new AtomicBoolean();
        final AtomicBoolean createdBeforeActionFinished = new AtomicBoolean();
        final CyclicBarrier actionStarted = new CyclicBarrier(2);
        pool.onCreate = key -> {
            if (!actionFinished.get()) {
                createdBeforeActionFinished.set(true);
            }
        };

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<Boolean> sweep = executor.submit(() -> pool.pool.doIfNotInUse(KEY, () -> {
                try {
                    actionStarted.await(10, TimeUnit.SECONDS);
                    // Give the borrower time to get to the lock and block on it.
                    Thread.sleep(200);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
                actionFinished.set(true);
            }));

            actionStarted.await(10, TimeUnit.SECONDS);
            pool.pool.borrow(KEY);

            assertThat(sweep.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(createdBeforeActionFinished).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Two threads racing for the same key must share one env. Two envs on one dir in a process is
     * exactly what LMDB does not support.
     */
    @Test
    void concurrentBorrowsShareOneObject() throws Exception {
        final Pool pool = new Pool();
        final int threads = 16;
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final ExecutorService executor = Executors.newCachedThreadPool();
        try {
            final List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return pool.pool.borrow(KEY);
                }));
            }

            final String first = futures.getFirst().get(10, TimeUnit.SECONDS);
            for (final Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(first);
            }
            assertThat(pool.created).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * A handler failing must not leave the reference count wrong, or an env would be leaked or
     * closed while still in use.
     */
    @Test
    void failingHandlerDoesNotBreakTheProtocol() {
        final Pool pool = new Pool();
        pool.onBorrow = object -> {
            throw new RuntimeException("Borrow handler failed");
        };
        pool.onRelease = object -> {
            throw new RuntimeException("Release handler failed");
        };

        final String object = pool.pool.borrow(KEY);
        pool.pool.release(KEY);

        assertThat(pool.destroyed).containsExactly(object);
    }

    @Test
    void releasingSomethingNeverBorrowedThrows() {
        final Pool pool = new Pool();

        assertThatThrownBy(() -> pool.pool.release(KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("doesn't exist");
    }

    @Test
    void useReleasesEvenWhenTheConsumerThrows() {
        final Pool pool = new Pool();

        assertThatThrownBy(() -> pool.pool.use(KEY, object -> {
            throw new RuntimeException("Consumer failed");
        })).hasMessage("Consumer failed");

        // Released, so the env was closed rather than leaked.
        assertThat(pool.destroyed).hasSize(1);
        assertThat(pool.pool.doIfNotInUse(KEY, () -> {
        })).isTrue();
    }

    // --------------------------------------------------------------------------------

    /**
     * A pool of distinguishable objects, with handlers that can be swapped per test.
     */
    private static class Pool {

        private final AtomicInteger sequence = new AtomicInteger();
        private final List<String> created = new ArrayList<>();
        private final List<String> destroyed = new ArrayList<>();
        private Consumer<String> onCreate = key -> {
        };
        private Consumer<String> onBorrow = object -> {
        };
        private Consumer<String> onRelease = object -> {
        };
        private Consumer<String> onDestroy = object -> {
        };

        private final DuplicateCheckStorePool<String, String> pool = new DuplicateCheckStorePool<>(
                key -> {
                    onCreate.accept(key);
                    final String object = key + "-" + sequence.incrementAndGet();
                    synchronized (created) {
                        created.add(object);
                    }
                    return object;
                },
                object -> onBorrow.accept(object),
                object -> onRelease.accept(object),
                object -> {
                    destroyed.add(object);
                    onDestroy.accept(object);
                });
    }
}
