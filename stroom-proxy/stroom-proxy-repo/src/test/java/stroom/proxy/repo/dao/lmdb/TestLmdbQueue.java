package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestLmdbQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbQueue.class);

    @Inject
    private LmdbEnv lmdbEnv;

    @Test
    void testSimple() {
        test(1, 1, 1, false);
    }

//    @Test
//    void testSimpleNative() {
//        test(1, 1, 1, true);
//    }

    @Test
    void testSimpleWithMore() {
        test(1, 1, 1_000_000, false);
    }

//    @Test
//    void testSimpleWithMoreNative() {
//        test(1, 1, 1_000_000, true);
//    }

    @Test
    void testComplex() {
        test(10, 3, 1_000_000, false);
    }

    void test(final int producerThreads,
              final int consumerThreads,
              final long totalSources,
              final boolean nativeByteOrder) {
        final LongSerde keySerde = new LongSerde();
        final LmdbQueue<Long> newSourceQueue = new LmdbQueue<>(
                lmdbEnv,
                "new-queue",
                keySerde,
                nativeByteOrder);

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.start();
        }, "start");

        Optional<Long> minId = newSourceQueue.getMinId();
        Optional<Long> maxId = newSourceQueue.getMaxId();
        minId.ifPresent(id -> assertThat(id).isZero());
        maxId.ifPresent(id -> assertThat(id).isZero());

        final AtomicLong totalAdded = new AtomicLong();
        final AtomicLong totalConsumed = new AtomicLong();
        final List<CompletableFuture<Void>> producers = new ArrayList<>();
        final List<CompletableFuture<Void>> consumers = new ArrayList<>();
        final List<CompletableFuture<Void>> all = new ArrayList<>();
        final Instant start = Instant.now();

        final AtomicLong sourceFileStoreId = new AtomicLong();
        for (int threads = 0; threads < producerThreads; threads++) {
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                long added = totalAdded.incrementAndGet();
                while (added <= totalSources) {
                    newSourceQueue.put(sourceFileStoreId.incrementAndGet());
                    added = totalAdded.incrementAndGet();
                }
            });
            producers.add(completableFuture);
            all.add(completableFuture);
        }

        for (int threads = 0; threads < consumerThreads; threads++) {
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                long consumed = totalConsumed.incrementAndGet();
                while (consumed <= totalSources) {
                    final Optional<Long> source = newSourceQueue.take(1, TimeUnit.SECONDS);
                    if (source.isPresent()) {
                        consumed = totalConsumed.incrementAndGet();
                    }
                }
            });
            consumers.add(completableFuture);
            all.add(completableFuture);
        }

        CompletableFuture.allOf(producers.toArray(new CompletableFuture[0])).whenCompleteAsync((unused, throwable) -> {
            lmdbEnv.sync();
            LOGGER.info("Completed production in: " + Duration.between(start, Instant.now()).toString());
        });
        CompletableFuture.allOf(consumers.toArray(new CompletableFuture[0])).whenCompleteAsync((unused, throwable) -> {
            LOGGER.info("Completed consumption in: " + Duration.between(start, Instant.now()).toString());
        });
        CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).join();

        minId = newSourceQueue.getMinId();
        maxId = newSourceQueue.getMaxId();
        assertThat(minId).isPresent();
        assertThat(maxId).isPresent();
        minId.ifPresent(id -> assertThat(id).isGreaterThan(0));
        maxId.ifPresent(id -> assertThat(id).isEqualTo(totalSources));

        LOGGER.logDurationIfInfoEnabled(() -> {
            newSourceQueue.clear();
            lmdbEnv.stop();
        }, "stop");
    }
}
