package stroom.util.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class TestGuardPerformance {

    private final int iterations = 1_000_000;
    private final int threadCount = Runtime.getRuntime().availableProcessors();
    private volatile Object env = new Object();

    @Test
    public void perfTest() {
        // Do multiple rounds to let it warm up
        for (int i = 1; i <= 3; i++) {
            System.out.println("Round: " + i + " SimpleGuard");
            IntStream.of(1, 2, 4, 8, threadCount, threadCount * 2)
                    .forEach(stripes -> runTest(stripes, new SimpleGuard(this::onClose)));


            System.out.println("Round: " + i + " StripedGuard");
            IntStream.of(1, 2, 4, 8, threadCount, threadCount * 2)
                    .forEach(stripes -> runTest(stripes, new StripedGuard(this::onClose, stripes)));
        }
    }


    private void runTest(int stripes, final Guard refCounter) {
//    System.out.println("Running test for " + stripes + " stripes");

        final AtomicReference<Instant> startTime = new AtomicReference<>(null);
        final CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
//    final RefCounter refCounter = new StripedRefCounterImpl(stripes, this::onClose);
        final CountDownLatch startLatch = new CountDownLatch(threadCount);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                // Wait for all threads to be ready
                countDownThenAwait(startLatch);

                // Capture the start time
                startTime.updateAndGet(currVal -> {
                    if (currVal == null) {
                        return Instant.now();
                    } else {
                        return currVal;
                    }
                });

                for (int j = 0; j < iterations; j++) {
                    refCounter.acquire(() -> {
                        // Make sure we have an env that is not 'closed'
                        Objects.requireNonNull(env);
                        return null;
                    });
                }
//        System.out.println(Thread.currentThread() + " - Done");
            }, executorService);
        }
        CompletableFuture.allOf(futures).join();

//        if (refCounter.getCount() != 0) {
//            throw new IllegalStateException("Ref count is " + refCounter.getCount());
//        }

        final Duration duration = Duration.between(startTime.get(), Instant.now());
        final double iterationsPerSec = (double) iterations / duration.toMillis() * 1000;

        System.out.println("All Finished"
                           + ", stripes: " + stripes
                           + ", threads: " + threadCount
                           + ", duration: " + duration
                           + ", iterationsPerSec: " + iterationsPerSec);
    }

    private void countDownThenAwait(final CountDownLatch latch) {
        latch.countDown();
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void onClose() {
        System.out.println(Thread.currentThread() + " - Starting onClose runnable");
        env = null;
        System.out.println(Thread.currentThread() + " - Finishing onClose runnable");
    }
}
