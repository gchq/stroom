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

        CountDownLatch donePutsLatch = new CountDownLatch(count);
        CountDownLatch doneTakesLatch = new CountDownLatch(count);

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
                } catch (InterruptedException | CompleteException e) {
                    throw new RuntimeException(e);
                }
            }, takeExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    completableQueue.put(item);
                    donePutsLatch.countDown();
                } catch (InterruptedException e) {
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

        CountDownLatch donePutsLatch = new CountDownLatch(count);
        CountDownLatch donePollingLatch = new CountDownLatch(count);

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
                } catch (InterruptedException | CompleteException e) {
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
                } catch (InterruptedException e) {
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

        CountDownLatch donePutsLatch = new CountDownLatch(count);
        CountDownLatch donePollingLatch = new CountDownLatch(count);

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
                } catch (InterruptedException | CompleteException e) {
                    throw new RuntimeException(e);
                }
            }, pollExecutor);
        }

        for (final Integer item : items) {
            CompletableFuture.runAsync(() -> {
                try {
                    completableQueue.put(item);
                    donePutsLatch.countDown();
                } catch (InterruptedException e) {
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

        CountDownLatch donePutsLatch = new CountDownLatch(count);
        CountDownLatch doneTakesLatch = new CountDownLatch(count);

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
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (CompleteException e) {
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
                } catch (InterruptedException e) {
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
}
