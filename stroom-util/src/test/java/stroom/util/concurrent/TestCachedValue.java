package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestCachedValue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCachedValue.class);

    @Test
    void singleThreadTest() {

        final AtomicInteger version = new AtomicInteger(1);


        final CachedValue<String, Integer> cachedValue = CachedValue.builder()
                .withMaxCheckIntervalMillis(100)
                .withStateSupplier(() -> {
                    LOGGER.debug("Supplying state");
                    return version.get();
                })
                .withValueFunction(state -> {
                    LOGGER.debug("Supplying value");
                    return "version " + state;
                })
                .build();

        assertThat(cachedValue.getValue())
                .isEqualTo("version 1");
        assertThat(cachedValue.getValue())
                .isEqualTo("version 1");

        version.incrementAndGet();

        LOGGER.debug("Sleeping");
        ThreadUtil.sleepIgnoringInterrupts(150);

        assertThat(cachedValue.getValue())
                .isEqualTo("version 2");
        assertThat(cachedValue.getValue())
                .isEqualTo("version 2");

        LOGGER.debug("Sleeping");
        ThreadUtil.sleepIgnoringInterrupts(100);

        assertThat(cachedValue.getValue())
                .isEqualTo("version 2");
    }

    @Test
    void multiThreadTest() throws InterruptedException {

//        final AtomicInteger version = new AtomicInteger(1);
        final int version = 1;


        final CachedValue<String, Integer> cachedValue = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withStateSupplier(() -> {
                    LOGGER.debug("Supplying state");
//                    return version.get();
                    return version;
                })
                .withValueFunction(state -> {
                    LOGGER.debug("Supplying value");
                    return "version " + state;
                })
                .build();

        final int threadCount = 3;
        final CountDownLatch startLatch = new CountDownLatch(threadCount);
        final CountDownLatch finishLatch = new CountDownLatch(threadCount);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    try {
                        startLatch.countDown();
                        LOGGER.debug("Thread waiting, startLatch {}", startLatch.getCount());
                        startLatch.await(10, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        LOGGER.debug("interrupted", e);
                        throw new RuntimeException(e);
                    }
                    LOGGER.debug("Thread starting");
                    final String value = cachedValue.getValue();
                    final boolean areEqual = Objects.equals(value, "version 1");
                    if (!areEqual) {
                        LOGGER.debug("Not equal, value: {}", value);
                    }
                    assertThat(areEqual)
                            .isTrue();
                    finishLatch.countDown();
                    LOGGER.debug("Thread finished, finishLatch {}", finishLatch.getCount());
                } catch (final RuntimeException e) {
                    LOGGER.debug("error", e);
                    Assertions.fail(e.getMessage());
                }
            }, executorService);
        }

        finishLatch.await(10, TimeUnit.SECONDS);
        LOGGER.debug("Finished");
    }

    @Test
    void testStateless() {
        final AtomicInteger updateCount = new AtomicInteger();
        final CachedValue<Integer, Void> updater = CachedValue.builder()
                .withMaxCheckIntervalMillis(200)
                .withoutStateSupplier()
                .withValueSupplier(updateCount::incrementAndGet)
                .build();

        assertThat(updateCount)
                .hasValue(0);

        Integer value = updater.getValue();
        assertThat(value)
                .isEqualTo(1);

        value = updater.getValue();
        assertThat(value)
                .isEqualTo(1);

        ThreadUtil.sleep(250);

        value = updater.getValue();
        assertThat(value)
                .isEqualTo(2);
    }
}
