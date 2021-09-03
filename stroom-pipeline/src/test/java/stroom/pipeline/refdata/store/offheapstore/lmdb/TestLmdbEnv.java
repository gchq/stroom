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
 * where concurrent readers > maxReaders setting. Tests the concurrency protection in
 * {@link LmdbEnv}
 */
public class TestLmdbEnv {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbEnv.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);

    private static final int MAX_READERS = 3;

    @Test
    void testWritersBlockReaders() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = true;
        final int expectedNumReadersHighWaterMark = 1;

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark);
    }

    @Test
    void testWritersDontBlockReaders() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = false;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark);
    }

    private void doMultiThreadTest(final boolean isReaderBlockedByWriter,
                                   final int expectedNumReadersHighWaterMark)
            throws IOException, InterruptedException {

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
                .withMaxReaderCount(MAX_READERS)
                .setIsReaderBlockedByWriter(isReaderBlockedByWriter)
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
                    LOGGER.trace("Creating thread {}", i);

                    CompletableFuture.runAsync(() -> {
                        LOGGER.trace("Init thread {}", i);
                        threads.add(Thread.currentThread().getName());
                        threadsStartedLatch.countDown();
                        try {
                            // Get all threads to wait for this latch to count down to zero
                            releaseThreadsLatch.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        LOGGER.trace("Running thread {}", i);

                        lmdbEnv.doWithReadTxn(txnRead -> {
                            try {
                                // Do the read
                                Assertions.assertThat(database.get(txnRead, "01"))
                                        .isNotEmpty();
                            } catch (Exception e) {
                                exceptions.add(e);
                            } finally {
                                LOGGER.trace("Finished thread {}", i);

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

        } while (!threadsFinishedLatch.await(50, TimeUnit.MILLISECONDS));

        // Wait for all threads to finish
        threadsFinishedLatch.await();

        LOGGER.info("Exception count: {}", exceptions.size());

        // We don't want any max readers exceptions
        Assertions.assertThat(exceptions)
                .isEmpty();

        LOGGER.info("numReaders: {}", lmdbEnv.info().numReaders);

        // numreaders is a high water mark of max concurrent readers
        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(expectedNumReadersHighWaterMark);
    }

    private String buildKey(int i) {
        return String.format("%02d", i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }
}
