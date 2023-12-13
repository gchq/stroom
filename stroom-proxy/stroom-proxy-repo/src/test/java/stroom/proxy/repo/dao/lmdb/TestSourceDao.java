package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
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
public class TestSourceDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSourceDao.class);

    @Inject
    private LmdbEnv lmdbEnv;
    @Inject
    private SourceDao sourceDao;

    @BeforeEach
    void beforeEach() {
        lmdbEnv.start();
    }

    @AfterEach
    void afterEach() {
        sourceDao.clear();
        lmdbEnv.stop();
    }

    @RepeatedTest(100)
    @Test
    void testSource() {
        LOGGER.info(() -> "Clear");
        sourceDao.clear();
        assertThat(sourceDao.getNewSourceQueue().getMinId()).isNotPresent();
        assertThat(sourceDao.getNewSourceQueue().getMaxId()).isNotPresent();
        assertThat(sourceDao.getExaminedSourceQueue().getMinId()).isNotPresent();
        assertThat(sourceDao.getExaminedSourceQueue().getMaxId()).isNotPresent();
        assertThat(sourceDao.getDeletableSourceQueue().getMinId()).isNotPresent();
        assertThat(sourceDao.getDeletableSourceQueue().getMaxId()).isNotPresent();

        LOGGER.info(() -> "Add source");
        sourceDao.addSource(1L, "test", "test");
        lmdbEnv.sync();

        final RepoSource repoSource = sourceDao.getNextSource();
        assertThat(repoSource).isNotNull();
        assertThat(sourceDao.countSources()).isOne();

        LOGGER.info(() -> "Set source examined");
        sourceDao.setSourceExamined(repoSource.fileStoreId(), 0);
        lmdbEnv.sync();
        final RepoSource deletable = sourceDao.getDeletableSource();
        LOGGER.info(() -> "Delete source: " + deletable.fileStoreId());
        assertThat(deletable.fileStoreId()).isOne();
        sourceDao.deleteSource(deletable.fileStoreId());
        lmdbEnv.sync();

//        assertThat(sourceDao.getNewSourceQueue().getMinId()).isNotPresent();
//        assertThat(sourceDao.getNewSourceQueue().getMaxId()).isNotPresent();
//        assertThat(sourceDao.getExaminedSourceQueue().getMinId()).isNotPresent();
//        assertThat(sourceDao.getExaminedSourceQueue().getMaxId()).isNotPresent();
//        assertThat(sourceDao.getDeletableSourceQueue().getMinId()).isNotPresent();
//        assertThat(sourceDao.getDeletableSourceQueue().getMaxId()).isNotPresent();
        assertThat(sourceDao.countSources()).isZero();
    }

    @Test
    void testAddSourcePerformance() {
        for (long i = 0; i < 1000000; i++) {
            sourceDao.addSource(i, "test", "test");
        }
        lmdbEnv.sync();
    }

    @Test
    void testSourceDirectSimple() {
        testSourceDirect(1, 1, 1);
    }

    @Test
    void testSourceDirectSimpleWithMoreSources() {
        testSourceDirect(1, 1, 1_000_000);
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
        final List<CompletableFuture<Void>> producers = new ArrayList<>();
        final List<CompletableFuture<Void>> consumers = new ArrayList<>();
        final List<CompletableFuture<Void>> all = new ArrayList<>();
        final Instant start = Instant.now();

        final AtomicLong sourceFileStoreId = new AtomicLong();
        for (int threads = 0; threads < producerThreads; threads++) {
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                long added = totalAdded.incrementAndGet();
                while (added <= totalSources) {
                    sourceDao.addSource(
                            sourceFileStoreId.incrementAndGet(),
                            "test",
                            null);
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
                    final Optional<RepoSource> source = sourceDao.getNextSource(1, TimeUnit.SECONDS);
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
    }
}
