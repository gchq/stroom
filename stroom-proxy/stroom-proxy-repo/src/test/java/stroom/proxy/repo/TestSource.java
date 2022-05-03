package stroom.proxy.repo;

import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.queue.Batch;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSource.class);

    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private SourceDao sourceDao;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @BeforeEach
    void cleanup() {
        proxyRepoSources.clear();
    }

    @Test
    void testAddSource() {
        for (long i = 0; i < 10; i++) {
            proxyRepoSources.addSource(
                    i,
                    "test",
                    null,
                    null);
        }
    }

    @Test
    void testUniquePathRollback() {
        proxyRepoSources.addSource(
                1L,
                "test",
                null,
                null);
        proxyRepoSources.flush();

        assertThatThrownBy(() -> {
            proxyRepoSources.addSource(
                    1L,
                    "test",
                    null,
                    null);
            proxyRepoSources.flush();
        }).isInstanceOf(DataAccessException.class);
        sourceDao.clearQueue();

        proxyRepoSources.addSource(
                2L,
                "test",
                null,
                null);
        proxyRepoSources.flush();
    }

//    @Test
//    void testSourceExists() {
//        proxyRepoSources.addSource(
//                1L,
//                "test",
//                null,
//                System.currentTimeMillis(),
//                null);
//        proxyRepoSources.insert();
//        final boolean exists = proxyRepoSources.sourceExists("path");
//        assertThat(exists).isTrue();
//    }

    @Test
    void testSourceQueueSimple() {
        testSourceQueue(1, 1, 1);
    }

    @Test
    void testSourceQueueComplex() {
        testSourceQueue(10, 3, 1_000_000);
    }

    private void testSourceQueue(final int producerThreads,
                                 final int consumerThreads,
                                 final long totalSources) {
        final AtomicLong totalAdded = new AtomicLong();
        final AtomicLong totalConsumed = new AtomicLong();
        final CompletableFuture[] all = new CompletableFuture[producerThreads + consumerThreads];

        for (int threads = 0; threads < producerThreads; threads++) {
            final CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
                boolean add = true;
                while (add) {
                    final long added = totalAdded.incrementAndGet();
                    if (added <= totalSources) {
                        proxyRepoSources.addSource(
                                ThreadLocalRandom.current().nextLong(),
                                "test",
                                null,
                                null);
//                        LOGGER.info("ADDED: " + added);
                    } else {
                        add = false;
                    }
                }
            });
            all[threads] = completableFuture;
        }

        for (int threads = producerThreads; threads < producerThreads + consumerThreads; threads++) {
            final CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
                boolean consume = true;
                while (consume) {
                    final Batch<RepoSource> batch = proxyRepoSources.getNewSources(1, TimeUnit.SECONDS);
//                    LOGGER.info("CONSUMED SOURCE: " + source.get().getId());
                    totalConsumed.addAndGet(batch.list().size());
//                    LOGGER.info("CONSUMED: " + totalConsumed.get());
                    if (totalConsumed.get() == totalSources) {
                        consume = false;
                    }
                }
            });
            all[threads] = completableFuture;
        }

        // Insert thread.
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            proxyRepoSources.flush();
        }, 1, 1, TimeUnit.SECONDS);

        CompletableFuture.allOf(all).join();

        scheduledExecutorService.shutdownNow();
    }
}
