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

package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCompletableQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCompletableQueue.class);

    private CompletableQueue<Integer> completableQueue;

    @BeforeEach
    void setUp() {
        completableQueue = new CompletableQueue<>(10);
    }

    @RepeatedTest(3)
    void putAndTake() throws InterruptedException {
        final int count = 1_000;
        final List<Integer> items = IntStream.rangeClosed(1, count)
                .boxed()
                .toList();
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();

        final CountDownLatch donePutsLatch = new CountDownLatch(count);
        final CountDownLatch doneTakesLatch = new CountDownLatch(count);

        // 10 threads putting, 10 taking
        final ExecutorService putsExecutor = Executors.newFixedThreadPool(10);
        final ExecutorService takeExecutor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    final Integer item = completableQueue.take();
                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                    doneTakesLatch.countDown();
                } catch (final InterruptedException | CompleteException e) {
                    throw new RuntimeException(e);
                }
            }, takeExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    completableQueue.put(item);
                    donePutsLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, putsExecutor);
        }

        donePutsLatch.await();
        doneTakesLatch.await();

        assertThat(outputItems)
                .containsExactlyInAnyOrderElementsOf(items);
    }

    @RepeatedTest(3)
    void putAndPoll() throws InterruptedException {
        final int count = 1_000;
        final List<Integer> items = IntStream.rangeClosed(1, count)
                .boxed()
                .toList();
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();

        final CountDownLatch donePutsLatch = new CountDownLatch(count);
        final CountDownLatch donePollingLatch = new CountDownLatch(count);

        // 10 threads putting, 10 taking
        final ExecutorService putsExecutor = Executors.newFixedThreadPool(10);
        final ExecutorService pollExecutor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    Integer item;
                    do {
                        item = completableQueue.poll();
                    } while (item == null);

                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                } catch (final InterruptedException | CompleteException e) {
                    throw new RuntimeException(e);
                }
                donePollingLatch.countDown();
            }, pollExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    completableQueue.put(item);
                    donePutsLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, putsExecutor);
        }

        donePutsLatch.await();
        donePollingLatch.await();

        assertThat(outputItems)
                .containsExactlyInAnyOrderElementsOf(items);
    }

    @RepeatedTest(3)
    void putAndPoll_withTimeout() throws InterruptedException {
        final int count = 1_000;
        final List<Integer> items = IntStream.rangeClosed(1, count)
                .boxed()
                .toList();
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();

        final CountDownLatch donePutsLatch = new CountDownLatch(count);
        final CountDownLatch donePollingLatch = new CountDownLatch(count);

        // 10 threads putting, 10 taking
        final ExecutorService putsExecutor = Executors.newFixedThreadPool(10);
        final ExecutorService pollExecutor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    Integer item;
                    do {
                        item = completableQueue.poll(5, TimeUnit.SECONDS);
                    } while (item == null);

                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                    donePollingLatch.countDown();
                } catch (final InterruptedException | CompleteException e) {
                    throw new RuntimeException(e);
                }
            }, pollExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    completableQueue.put(item);
                    donePutsLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, putsExecutor);
        }

        donePutsLatch.await();
        donePollingLatch.await();

        assertThat(outputItems)
                .containsExactlyInAnyOrderElementsOf(items);
    }

    @RepeatedTest(3)
    void complete() throws InterruptedException, CompleteException {
        final int cnt = 5;
        for (int i = 0; i < cnt; i++) {
            completableQueue.put(i);
        }
        completableQueue.complete();

        for (int i = 0; i < cnt; i++) {
            completableQueue.take();
        }

        Assertions
                .assertThatThrownBy(() -> {
                    completableQueue.take();
                })
                .isInstanceOf(CompleteException.class);
    }

    @RepeatedTest(3)
    void terminate() throws InterruptedException {
        final int count = 1_000;
        final List<Integer> items = IntStream.rangeClosed(1, count)
                .boxed()
                .toList();
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();

        final CountDownLatch donePutsLatch = new CountDownLatch(count);
        final CountDownLatch doneTakesLatch = new CountDownLatch(count);

        // 10 threads putting, 10 taking
        final ExecutorService putsExecutor = Executors.newFixedThreadPool(10);
        final ExecutorService takeExecutor = Executors.newFixedThreadPool(10);

        final AtomicBoolean completed = new AtomicBoolean(false);

        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    final Integer item = completableQueue.take();
                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    completed.set(true);
                }
                doneTakesLatch.countDown();
            }, takeExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    // Terminate half-way through
                    if (donePutsLatch.getCount() < (count / 2)) {
                        completableQueue.terminate();
                    } else {
                        completableQueue.put(item);
                    }
                    donePutsLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, putsExecutor);
        }

        donePutsLatch.await();
        doneTakesLatch.await();

        assertThat(completed)
                .isTrue();
    }

    @Test
    void size() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            completableQueue.put(i);
        }
        assertThat(completableQueue.size())
                .isEqualTo(5);
    }

    @Test
    void isEmpty() throws InterruptedException, CompleteException {
        assertThat(completableQueue.isEmpty())
                .isTrue();
        for (int i = 0; i < 5; i++) {
            completableQueue.put(i);
        }
        assertThat(completableQueue.isEmpty())
                .isFalse();

        for (int i = 0; i < 5; i++) {
            completableQueue.poll();
        }
        assertThat(completableQueue.isEmpty())
                .isTrue();
    }

    @Test
    void manyTakersThenComplete() throws InterruptedException {

        final int count = 10;
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();
        final CountDownLatch aboutToTakeLatch = new CountDownLatch(count);
        final CountDownLatch doneTakesLatch = new CountDownLatch(count);
        final ExecutorService takeExecutor = Executors.newFixedThreadPool(10);
        final AtomicBoolean completed = new AtomicBoolean(false);

        // Line up 10 takers on an empty queue so, they will all block
        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    aboutToTakeLatch.countDown();
                    final Integer item = completableQueue.take();
                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    completed.set(true);
                }
                doneTakesLatch.countDown();
            }, takeExecutor);
        }

        // Make sure all threads have or are ready to take
        aboutToTakeLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);
        // Complete the queue, which _should_ release all blocked takers
        completableQueue.complete();
        doneTakesLatch.await();
        assertThat(completed)
                .isTrue();
    }

    @Test
    void manyTakersThenTerminate() throws InterruptedException {

        final int count = 10;
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();
        final CountDownLatch aboutToTakeLatch = new CountDownLatch(count);
        final CountDownLatch doneTakesLatch = new CountDownLatch(count);
        final ExecutorService takeExecutor = Executors.newFixedThreadPool(10);
        final AtomicBoolean completed = new AtomicBoolean(false);

        // Line up 10 takers on an empty queue so, they will all block
        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    aboutToTakeLatch.countDown();
                    final Integer item = completableQueue.take();
                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    completed.set(true);
                }
                doneTakesLatch.countDown();
            }, takeExecutor);
        }

        // Make sure all threads have or are ready to take
        aboutToTakeLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);
        // terminate the queue, which _should_ release all blocked takers
        completableQueue.terminate();
        doneTakesLatch.await();
        assertThat(completed)
                .isTrue();
    }

    @Test
    void manyPuttersThenComplete() throws InterruptedException {

        final int count = 10;
        final List<Integer> outputItems = new CopyOnWriteArrayList<>();
        final CountDownLatch aboutToTakeLatch = new CountDownLatch(count);
        final CountDownLatch doneTakesLatch = new CountDownLatch(count);
        final ExecutorService takeExecutor = Executors.newFixedThreadPool(10);
        final AtomicBoolean completed = new AtomicBoolean(false);

        // Line up 10 takers on an empty queue so, they will all block
        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    aboutToTakeLatch.countDown();
                    final Integer item = completableQueue.take();
                    outputItems.add(item);
                    LOGGER.debug("Got item: {}", item);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    completed.set(true);
                }
                doneTakesLatch.countDown();
            }, takeExecutor);
        }

        // Make sure all threads have or are ready to take
        aboutToTakeLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);
        // Complete the queue, which _should_ release all blocked takers
        completableQueue.complete();
        doneTakesLatch.await();
        assertThat(completed)
                .isTrue();
    }
}
