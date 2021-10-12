package stroom.pipeline.refdata.store.offheapstore.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxnWrapper;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.offheapstore.serdes.StringSerde;
import stroom.util.concurrent.HighWaterMarkTracker;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;

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
import java.util.Optional;
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

    // If this value is set too high then the test will fail on gh actions as
    // that has limited cores available.
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int MAX_READERS = CORES;

    private LmdbEnv lmdbEnv;
    private BasicLmdbDb<String, String> database;

    @Test
    void testWritersBlockReaders_withWrites() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = true;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        buildEnvAndDb(doWritersBlockReaders);

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark, true);
    }

    @Test
    void testWritersBlockReaders_withoutWrites() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = true;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        buildEnvAndDb(doWritersBlockReaders);

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark, false);
    }

    @Test
    void testWritersDontBlockReaders_withWrites() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = false;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        buildEnvAndDb(doWritersBlockReaders);

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark, true);
    }

    @Test
    void testWritersDontBlockReaders_withoutWrites() throws IOException, InterruptedException {

        final boolean doWritersBlockReaders = false;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        buildEnvAndDb(doWritersBlockReaders);

        doMultiThreadTest(doWritersBlockReaders, expectedNumReadersHighWaterMark, false);
    }

    @Test
    void testBatchingWriteTxnWrapper_withBatchSize() throws Exception {
        buildEnvAndDb(true);

        try (final BatchingWriteTxnWrapper batchingWriteTxnWrapper = lmdbEnv.openBatchingWriteTxn(2)) {
            for (int i = 0; i < 9; i++) {
                database.put(batchingWriteTxnWrapper.getTxn(), buildKey(i), buildValue(i), false);
                batchingWriteTxnWrapper.commitIfRequired();
            }
            // final commit
            batchingWriteTxnWrapper.commit();
        }

        Assertions.assertThat(database.getEntryCount())
                .isEqualTo(9);
    }

    @Test
    void testBatchingWriteTxnWrapper_withBatchSize_withAbort() throws Exception {
        buildEnvAndDb(true);

        try (final BatchingWriteTxnWrapper batchingWriteTxnWrapper = lmdbEnv.openBatchingWriteTxn(2)) {
            for (int i = 0; i < 9; i++) {
                database.put(batchingWriteTxnWrapper.getTxn(), buildKey(i), buildValue(i), false);
                batchingWriteTxnWrapper.commitIfRequired();
            }

            // abort the txn so no. 9 should be rolled back leaving 8
            batchingWriteTxnWrapper.abort();
        }

        Assertions.assertThat(database.getEntryCount())
                .isEqualTo(8);
    }

    @Test
    void testBatchingWriteTxnWrapper_noWork() throws Exception {
        buildEnvAndDb(true);

        try (final BatchingWriteTxnWrapper batchingWriteTxnWrapper = lmdbEnv.openBatchingWriteTxn(2)) {
            // do nothing
        }

        Assertions.assertThat(database.getEntryCount())
                .isEqualTo(0);
    }

    @Test
    void testBatchingWriteTxnWrapper_noWork_abort() throws Exception {
        buildEnvAndDb(true);

        try (final BatchingWriteTxnWrapper batchingWriteTxnWrapper = lmdbEnv.openBatchingWriteTxn(2)) {
            batchingWriteTxnWrapper.abort();
        }

        Assertions.assertThat(database.getEntryCount())
                .isEqualTo(0);
    }

    private void buildEnvAndDb(final boolean isReaderBlockedByWriter) throws IOException {
        LOGGER.info("MAX_READERS: {}", MAX_READERS);

        final Path dbDir = Files.createTempDirectory("stroom");

        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new PathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        lmdbEnv = new LmdbEnvFactory(pathCreator, tempDirProvider, new LmdbLibraryConfig())
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(MAX_READERS)
                .setIsReaderBlockedByWriter(isReaderBlockedByWriter)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        database = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");
    }

    private void doMultiThreadTest(final boolean isReaderBlockedByWriter,
                                   final int expectedNumReadersHighWaterMark,
                                   final boolean doWrites)
            throws IOException, InterruptedException {


        final HighWaterMarkTracker readersHighWaterMarkTracker = new HighWaterMarkTracker();
        final HighWaterMarkTracker writersHighWaterMarkTracker = new HighWaterMarkTracker();

        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);


        database.logDatabaseContents(LOGGER::info);

        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(1);

        final int threadCount = 50;
        LOGGER.info("threadCount: {}", threadCount);

        final CountDownLatch threadsFinishedLatch = new CountDownLatch(threadCount);
        final CountDownLatch threadsStartedLatch = new CountDownLatch(threadCount);
        final CountDownLatch releaseThreadsLatch = new CountDownLatch(1);
        final Executor executor = Executors.newFixedThreadPool(threadCount + 2);
        final Queue<String> threads = new ConcurrentLinkedQueue<>();
        final List<Exception> exceptions = new ArrayList<>();

        final int readerThreads = doWrites
                ? threadCount / 2
                : threadCount;
        final int writerThreads = readerThreads;

        // load the data
        // pad the keys to a fixed length so they sort in number order
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            IntStream.rangeClosed(1, writerThreads).forEach(i -> {
                database.put(writeTxn, buildKey(i), buildValue(i), false);
            });
        });

        IntStream.rangeClosed(1, readerThreads)
                .forEach(i -> {
                    createReaderThread(
                            lmdbEnv,
                            database,
                            readersHighWaterMarkTracker,
                            threadsFinishedLatch,
                            threadsStartedLatch,
                            releaseThreadsLatch,
                            executor,
                            threads,
                            exceptions,
                            i);

                    if (doWrites) {
                        createWriterThread(
                                lmdbEnv,
                                database,
                                writersHighWaterMarkTracker,
                                threadsFinishedLatch,
                                threadsStartedLatch,
                                releaseThreadsLatch,
                                executor,
                                threads,
                                exceptions,
                                i);
                    }
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

        // Can't really test for a min value on the readers to ensure they
        // are working concurrently as this will be run by github actions
        // which only has one core. Useful for manually checking locally though

        LOGGER.info("numReaders: {}", lmdbEnv.info().numReaders);
        // numreaders is a high water mark of max concurrent readers
        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isLessThanOrEqualTo(expectedNumReadersHighWaterMark);

        LOGGER.info("readersHighWaterMarkTracker: {}", readersHighWaterMarkTracker);
        Assertions.assertThat(readersHighWaterMarkTracker.getHighWaterMark())
                .isLessThanOrEqualTo(expectedNumReadersHighWaterMark);

        if (doWrites) {
            LOGGER.info("writersHighWaterMarkTracker: {}", writersHighWaterMarkTracker);
            Assertions.assertThat(writersHighWaterMarkTracker.getHighWaterMark())
                    .isEqualTo(1);
        }
    }

    private void createReaderThread(
            final LmdbEnv lmdbEnv,
            final BasicLmdbDb<String, String> database,
            final HighWaterMarkTracker highWaterMarkTracker,
            final CountDownLatch threadsFinishedLatch,
            final CountDownLatch threadsStartedLatch,
            final CountDownLatch releaseThreadsLatch,
            final Executor executor,
            final Queue<String> threads,
            final List<Exception> exceptions,
            final int i) {

        LOGGER.trace("Creating reader thread {}", i);

        CompletableFuture.runAsync(() -> {
            LOGGER.trace("Init reader thread {}", i);
            threads.add(Thread.currentThread().getName());
            threadsStartedLatch.countDown();
            try {
                // Get all threads to wait for this latch to count down to zero
                releaseThreadsLatch.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            LOGGER.trace("Running reader thread {}", i);

            lmdbEnv.doWithReadTxn(txnRead -> {
                try {
                    highWaterMarkTracker.doWithHighWaterMarkTracking(() -> {
                        final Optional<String> optVal = database.get(txnRead, "01");

                        Assertions.assertThat(optVal)
                                .isNotEmpty();
                        LOGGER.trace("highWaterMarkTracker: {}", highWaterMarkTracker);
                    });
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    LOGGER.trace("Finished reader thread {}", i);

                    threads.remove(Thread.currentThread().getName());
                    threadsFinishedLatch.countDown();
                }
            });
        }, executor);
    }

    private void createWriterThread(
            final LmdbEnv lmdbEnv,
            final BasicLmdbDb<String, String> database,
            final HighWaterMarkTracker highWaterMarkTracker,
            final CountDownLatch threadsFinishedLatch,
            final CountDownLatch threadsStartedLatch,
            final CountDownLatch releaseThreadsLatch,
            final Executor executor,
            final Queue<String> threads,
            final List<Exception> exceptions,
            final int i) {

        LOGGER.trace("Creating writer thread {}", i);
        final String key = buildKey(i);

        CompletableFuture.runAsync(() -> {
            LOGGER.trace("Init writer thread {}", i);
            threads.add(Thread.currentThread().getName());
            threadsStartedLatch.countDown();
            try {
                // Get all threads to wait for this latch to count down to zero
                releaseThreadsLatch.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            LOGGER.trace("Running writer thread {}", i);

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                try {
                    highWaterMarkTracker.doWithHighWaterMarkTracking(() -> {
                        // Do a write
                        final PutOutcome putOutcome = database.put(
                                writeTxn, key, "xxxxxx", true);

                        Assertions.assertThat(putOutcome.isSuccess())
                                .isTrue();
                        LOGGER.trace("highWaterMarkTracker: {}", highWaterMarkTracker);
                    });

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    LOGGER.trace("Finished writer thread {}", i);

                    threads.remove(Thread.currentThread().getName());
                    threadsFinishedLatch.countDown();
                }
            });
        }, executor);
    }

    private String buildKey(int i) {
        return String.format("%02d", i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }

}
