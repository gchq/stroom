package stroom.pipeline.refdata.store.offheapstore.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.pipeline.refdata.store.offheapstore.serdes.StringSerde;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Env.ReadersFullException;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Demonstrate the behaviour of too many readers trying to do a get() on LMDB at once
 * where concurrent readers > maxReaders setting. Not a test of the stroom code.
 */
public class TestLmdbMaxReaders {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbMaxReaders.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);

    @Test
    void test() throws IOException, InterruptedException {

//        ThreadUtil.sleepAtLeastIgnoreInterrupts(10_000);

        final int maxReaders = 5;

        final Path dbDir = Files.createTempDirectory("stroom");

        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};
        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new PathCreator(() -> dbDir, () -> dbDir);

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator)
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(maxReaders)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        final BasicLmdbDb<String, String> database = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");

        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);

        // pad the keys to a fixed length so they sort in number order
        IntStream.rangeClosed(1, 5).forEach(i -> {
            database.put(buildKey(i), buildValue(i), false);
        });

        database.logDatabaseContents(LOGGER::info);

        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(1);


        final int threadCount = 50;
        final CountDownLatch threadsFinishedLatch = new CountDownLatch(threadCount);
        final CountDownLatch threadsStartedLatch = new CountDownLatch(threadCount);
        final CountDownLatch releaseThreadsLatch = new CountDownLatch(1);
        final Executor executor = Executors.newFixedThreadPool(threadCount + 10);
        final Queue<String> threads = new ConcurrentLinkedQueue<>();
        final List<Exception> exceptions = new ArrayList<>();

        IntStream.rangeClosed(1, threadCount)
                .forEach(i -> {
                    LOGGER.info("Creating thread {}", i);

                    CompletableFuture.runAsync(() -> {
                        LOGGER.info("Init thread {}", i);
                        threads.add(Thread.currentThread().getName());
                        threadsStartedLatch.countDown();
                        try {
                            // Get all threads to start work at the same time
                            releaseThreadsLatch.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        LOGGER.info("Running thread {}", i);

                        lmdbEnv.doWithWriteTxn(txnRead -> {
                            try {
                                Assertions.assertThat(database.get(txnRead, "01"))
                                        .isNotEmpty();
                            } catch (Exception e) {
                                exceptions.add(e);
                            } finally {
                                LOGGER.info("Finished thread {}", i);

                                threads.remove(Thread.currentThread().getName());
                                threadsFinishedLatch.countDown();
                            }
                        });
                    }, executor);
                });

        // wait for all threads to be ready to work
        threadsStartedLatch.await();

        // Release all threads at once to hit LMDB
        releaseThreadsLatch.countDown();

        do {
            LOGGER.info("numReaders: {}, threadsFinishedLatch: {}, threads: {}",
                    lmdbEnv.info().numReaders,
                    threadsFinishedLatch.getCount(),
                    String.join(", ", threads));

        } while (!threadsFinishedLatch.await(1, TimeUnit.SECONDS));

        // Wait for all threads to finish
        threadsFinishedLatch.await();

        LOGGER.info("Exception count: {}", exceptions.size());

        Assertions.assertThat(exceptions)
                .isNotEmpty();

        Assertions.assertThat(exceptions.stream()
                .distinct()
                .allMatch(clazz ->
                        clazz instanceof ReadersFullException))
                .isTrue();

        LOGGER.info("numReaders: {}", lmdbEnv.info().numReaders);
    }

    private String buildKey(int i) {
        return String.format("%02d", i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }
}
