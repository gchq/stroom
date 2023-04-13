package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.queue.Batch;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSourceDao.class);

    @Inject
    private SourceDao sourceDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
    }

    @Test
    void testSource() {
//        boolean exists = sourceDao.pathExists("test");
//        assertThat(exists).isFalse();

        sourceDao.addSource(1L, "test", "test");
        sourceDao.flush();
//        exists = sourceDao.pathExists("test");
//        assertThat(exists).isTrue();
        final Batch<RepoSource> batch = sourceDao.getNewSources();
        assertThat(batch.list().size()).isOne();

        sourceDao.markDeletableSources();
        sourceDao.deleteSources();
        assertThat(sourceDao.countSources()).isOne();

        sourceDao.setSourceExamined(batch.list().get(0).id(), true, 0);
        sourceDao.markDeletableSources();
        sourceDao.deleteSources();
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.clear();
    }


    @Test
    void testAddSourcePerformance() {
        long now = System.currentTimeMillis();
        for (long i = 0; i < 1000000; i++) {
            sourceDao.addSource(i, "test", "test");
        }
        sourceDao.flush();
        LOGGER.info(Duration.of(System.currentTimeMillis() - now, ChronoUnit.MILLIS).toString());
    }

    @Test
    void testSourceDirectSimple() {
        testSourceDirect(1, 1, 1);
    }

    @Test
    void testSourceDirectComplex() {
        testSourceDirect(10, 3, 1_000_000);
    }

    private void testSourceDirect(final int producerThreads,
                                  final int consumerThreads,
                                  final long totalSources) {
        final AtomicLong totalAdded = new AtomicLong();
        final AtomicLong totalConsumed = new AtomicLong();
        final CompletableFuture[] all = new CompletableFuture[producerThreads + consumerThreads];

        final AtomicLong sourceFileStoreId = new AtomicLong();
        for (int threads = 0; threads < producerThreads; threads++) {
            final CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
                long added = totalAdded.incrementAndGet();
                while (added <= totalSources) {
                    sourceDao.addSource(
                            sourceFileStoreId.incrementAndGet(),
                            "test",
                            null);
//                    LOGGER.info("ADDED: " + added);
                    added = totalAdded.incrementAndGet();
                }
            });
            all[threads] = completableFuture;
        }

        for (int threads = producerThreads; threads < producerThreads + consumerThreads; threads++) {
            final CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
                long consumed = totalConsumed.incrementAndGet();
                while (consumed <= totalSources) {
                    final Batch<RepoSource> sources = sourceDao.getNewSources(1, TimeUnit.SECONDS);
//                    LOGGER.info("CONSUMED SOURCE: " + source.get().getId());
//                    LOGGER.info("CONSUMED: " + consumed);
                    consumed = totalConsumed.addAndGet(sources.list().size());
                }
            });
            all[threads] = completableFuture;
        }

        // Insert thread.
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            sourceDao.flush();
        }, 1, 1, TimeUnit.SECONDS);

        CompletableFuture.allOf(all).join();

        scheduledExecutorService.shutdownNow();
    }
}
