package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.serde.StringSerde;
import stroom.util.concurrent.HighWaterMarkTracker;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ModelStringUtil;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.Env.MapFullException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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
    private static final int CORES = 100;
    private static final int MAX_READERS = CORES;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final ByteBufferPool BYTE_BUFFER_POOL = new ByteBufferPoolFactory().getByteBufferPool();

    @AfterAll
    static void afterAll() {
        executor.shutdown();
        BYTE_BUFFER_POOL.clear();
    }

    @Test
    void testSingleThreaded(@TempDir Path dbDir) {
        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(3)
                .singleThreaded()
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        try (lmdbEnv) {
            final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    BYTE_BUFFER_POOL,
                    new StringSerde(),
                    new StringSerde(),
                    "MyBasicLmdb");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                db.put(writeTxn, "foo", "FOO", false);
                // Do get in its own txn, so it can't see the uncommitted put yet
                assertThat(db.get("foo"))
                        .isEmpty();
            });

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                db.put(writeTxn, "bar", "BAR", false);
                // Gets each in their own txn
                assertThat(db.get("foo"))
                        .hasValue("FOO");
                assertThat(db.get("bar"))
                        .isEmpty();
            });
            assertThat(db.get("bar"))
                    .hasValue("BAR");

            // We have no semaphore protection for max readers so make sure LMDB throws and
            // doesn't seg fault.
            Assertions.assertThatThrownBy(
                            () -> {
                                lmdbEnv.doWithReadTxn(readTxn1 -> {
                                    lmdbEnv.doWithReadTxn(readTxn2 -> {
                                        lmdbEnv.doWithReadTxn(readTxn3 -> {
                                            lmdbEnv.doWithReadTxn(readTxn4 -> {
                                                lmdbEnv.doWithReadTxn(readTxn5 -> {
                                                    assertThat(db.get("bar"))
                                                            .hasValue("BAR");
                                                });
                                            });
                                        });
                                    });
                                });
                            })
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maxreaders reached");
        }
    }

    @Test
    void testWritersBlockReaders_withWrites() throws IOException {

        final boolean doWritersBlockReaders = true;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        doWithEnvAndDb(doWritersBlockReaders,
                (lmdbEnv, database) ->
                        doMultiThreadTest(
                                lmdbEnv,
                                database,
                                doWritersBlockReaders,
                                expectedNumReadersHighWaterMark,
                                true));
    }

    @Test
    void testWritersBlockReaders_withoutWrites() throws IOException {

        final boolean doWritersBlockReaders = true;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        doWithEnvAndDb(
                doWritersBlockReaders,
                (lmdbEnv, database) ->
                        doMultiThreadTest(
                                lmdbEnv,
                                database,
                                doWritersBlockReaders,
                                expectedNumReadersHighWaterMark,
                                false));
    }

    @Test
    void testWritersDontBlockReaders_withWrites() throws IOException {

        final boolean doWritersBlockReaders = false;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        doWithEnvAndDb(
                doWritersBlockReaders,
                (lmdbEnv, database) ->
                        doMultiThreadTest(
                                lmdbEnv,
                                database,
                                doWritersBlockReaders,
                                expectedNumReadersHighWaterMark,
                                false));
    }

    @Test
    void testWritersDontBlockReaders_withoutWrites() throws IOException {

        final boolean doWritersBlockReaders = false;
        final int expectedNumReadersHighWaterMark = MAX_READERS;

        doWithEnvAndDb(
                doWritersBlockReaders,
                (lmdbEnv, database) ->
                        doMultiThreadTest(
                                lmdbEnv,
                                database,
                                doWritersBlockReaders,
                                expectedNumReadersHighWaterMark,
                                false));
    }

    @Test
    void testBatchingWriteTxnWrapper_withBatchSize() throws Exception {
        doWithEnvAndDb(
                true,
                (lmdbEnv, database) -> {
                    try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
                        for (int i = 0; i < 9; i++) {
                            database.put(batchingWriteTxn.getTxn(), buildKey(i), buildValue(i), false);
                            batchingWriteTxn.commitIfRequired();
                        }
                        // final commit
                        batchingWriteTxn.commit();
                    }

                    assertThat(database.getEntryCount())
                            .isEqualTo(9);
                });
    }

    @Test
    void testBatchingWriteTxnWrapper_withBatchSize_withAbort() throws Exception {
        doWithEnvAndDb(
                true,
                (lmdbEnv, database) -> {
                    try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
                        for (int i = 0; i < 9; i++) {
                            database.put(batchingWriteTxn.getTxn(), buildKey(i), buildValue(i), false);
                            batchingWriteTxn.commitIfRequired();
                        }

                        // abort the txn so no. 9 should be rolled back leaving 8
                        batchingWriteTxn.abort();
                    }

                    assertThat(database.getEntryCount())
                            .isEqualTo(8);
                });
    }

    @Test
    void testBatchingWriteTxnWrapper_noWork() throws Exception {
        doWithEnvAndDb(
                true,
                (lmdbEnv, database) -> {
                    try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
                        // do nothing
                    }

                    assertThat(database.getEntryCount())
                            .isEqualTo(0);
                });
    }

    @Test
    void testBatchingWriteTxnWrapper_noWork_abort() throws Exception {
        doWithEnvAndDb(
                true,
                (lmdbEnv, database) -> {
                    try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
                        batchingWriteTxn.abort();
                    }

                    assertThat(database.getEntryCount())
                            .isEqualTo(0);
                });
    }

    /**
     * Have multiple threads open an lmdb env and write/read from the db.
     * More of a manual test to check that many envs don't cause problems.
     * Have tried it with 1000.
     */
    @Test
    void testManyEnvs() throws IOException, InterruptedException {
        final List<Path> dbDirs = new ArrayList<>();
        final List<LmdbEnv> envs = new ArrayList<>();
        final int cnt = 10;
        final CountDownLatch startLatch = new CountDownLatch(cnt);
        final CountDownLatch closeTxnLatch = new CountDownLatch(cnt);
        final CountDownLatch completionLatch = new CountDownLatch(cnt);
        for (int i = 0; i < cnt; i++) {

            final Path dbDir = Files.createTempDirectory("stroom");
            dbDirs.add(dbDir);

            final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
            final TempDirProvider tempDirProvider = () -> dbDir;

            final LmdbEnv lmdbEnv = new LmdbEnvFactory(
                    pathCreator,
                    new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                    .builder(dbDir)
                    .withMapSize(ByteSize.ofKibibytes(200))
                    .withMaxDbCount(1)
                    .setIsReaderBlockedByWriter(true)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();
            envs.add(lmdbEnv);

            final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    BYTE_BUFFER_POOL,
                    new StringSerde(),
                    new StringSerde(),
                    "MyBasicLmdb");

            CompletableFuture.runAsync(() -> {
                lmdbEnv.doWithWriteTxn(writeTxn -> {

                    LOGGER.debug("putting to env: {}", lmdbEnv.getLocalDir().toAbsolutePath());
                    db.put(writeTxn, "hello", "world", false);
                });

                lmdbEnv.doWithReadTxn(readTxn -> {
                    startLatch.countDown();
                    try {
                        LOGGER.debug("countDownLatch1: {}", startLatch.getCount());
                        startLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    final Optional<String> optVal = db.get("hello");
                    assertThat(optVal)
                            .hasValue("world");

                    closeTxnLatch.countDown();
                    try {
                        // Wait for all others to reach this point before closing the txn so we have
                        // cnt txns open
                        closeTxnLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    completionLatch.countDown();
                });
            }, executor);
        }

        LOGGER.debug("countDownLatch2: {}", completionLatch.getCount());
        completionLatch.await();

        for (int i = 0; i < cnt; i++) {
            LOGGER.debug("Closing {}", envs.get(i).getLocalDir().toAbsolutePath());
            envs.get(i).close();
            FileUtil.deleteDir(dbDirs.get(i));
        }
    }


    @Test
    void testMaxEnvSizeReached() throws IOException {

        final Path dbDir = Files.createTempDirectory("stroom");
        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;
        try (LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(ByteSize.ofKibibytes(30))
                .withMaxDbCount(1)
                .withMaxReaderCount(MAX_READERS)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build()) {

            BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    BYTE_BUFFER_POOL,
                    new StringSerde(),
                    new StringSerde(),
                    "testMaxEnvSizeReached");

            LOGGER.info("env size: {}", ModelStringUtil.formatIECByteSizeString(lmdbEnv.getSizeOnDisk()));

            // Tiny max env size so put a load of entries to blow the limit
            Assertions.assertThatThrownBy(() ->
                            lmdbEnv.doWithWriteTxn(writeTxn -> {
                                for (int i = 0; i < 1_000; i++) {
                                    db.put(writeTxn,
                                            "this is my key " + i,
                                            "this is my value " + i,
                                            false,
                                            false);
                                }
                            }))
                    .rootCause()
                    .isInstanceOf(MapFullException.class)
                    .message()
                    .containsIgnoringCase("Environment mapsize reached");
        }
    }

    private void doWithEnvAndDb(final boolean isReaderBlockedByWriter,
                                final BiConsumer<LmdbEnv, AbstractLmdbDb<String, String>> work)
            throws IOException {
        LOGGER.info("MAX_READERS: {}", MAX_READERS);

        final Path dbDir = Files.createTempDirectory("stroom");

        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(MAX_READERS)
                .setIsReaderBlockedByWriter(isReaderBlockedByWriter)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        try (lmdbEnv) {
            final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    BYTE_BUFFER_POOL,
                    new StringSerde(),
                    new StringSerde(),
                    "MyBasicLmdb");
            work.accept(lmdbEnv, db);
        } finally {
            FileUtil.deleteDir(dbDir);
        }
    }

    @Test
    void testCompact(@TempDir Path dbDir) {
        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(MAX_READERS)
                .withEnvFlags(envFlags)
                .build();

        try (lmdbEnv) {
            final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    BYTE_BUFFER_POOL,
                    new StringSerde(),
                    new StringSerde(),
                    "MyBasicLmdb");

            final int iterations = 100_000;
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = 0; i < iterations; i++) {
                    db.put(writeTxn, "key-" + i, "val-" + i, false);
                }
            });
            LOGGER.info("Put {} entries", ModelStringUtil.formatCsv(iterations));
            assertThat(db.getEntryCount())
                    .isEqualTo(iterations);

            final long sizeOnDisk1 = lmdbEnv.getSizeOnDisk();
            final long sizeInUse1 = lmdbEnv.getSizeInUse();
            LOGGER.info("sizeOnDisk1: {}, sizeInUse1: {}", ByteSize.ofBytes(sizeOnDisk1), ByteSize.ofBytes(sizeInUse1));

            // Not a lot should happen
            lmdbEnv.compact();

            final long sizeOnDisk2 = lmdbEnv.getSizeOnDisk();
            final long sizeInUse2 = lmdbEnv.getSizeInUse();
            LOGGER.info("sizeOnDisk2: {}, sizeInUse2: {}", ByteSize.ofBytes(sizeOnDisk2), ByteSize.ofBytes(sizeInUse2));

            assertThat(sizeOnDisk2)
                    .isCloseTo(sizeInUse1, Percentage.withPercentage(5));

            // Make sure we can query the re-opened db
            assertThat(db.get("key-5"))
                    .hasValue("val-5");

            final int tenPct = (int) (iterations * 0.1);
            final int twentyPct = (int) (iterations * 0.2);
            final LongAdder delCount = new LongAdder();
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                // Delete all bar the first 10%
                for (int i = tenPct; i < iterations; i++) {
                    db.delete(writeTxn, "key-" + i);
                    delCount.increment();
                }
            });
            LOGGER.info("Deleted {} entries", ModelStringUtil.formatCsv(delCount));
            assertThat(db.getEntryCount())
                    .isEqualTo(tenPct);

            final long sizeOnDisk3 = lmdbEnv.getSizeOnDisk();
            final long sizeInUse3 = lmdbEnv.getSizeInUse();
            LOGGER.info("sizeOnDisk3: {}, sizeInUse3: {}", ByteSize.ofBytes(sizeOnDisk3), ByteSize.ofBytes(sizeInUse3));

            // Compact again, should get smaller
            lmdbEnv.compact();

            final long sizeOnDisk4 = lmdbEnv.getSizeOnDisk();
            final long sizeInUse4 = lmdbEnv.getSizeInUse();
            LOGGER.info("sizeOnDisk4: {}, sizeInUse4: {}", ByteSize.ofBytes(sizeOnDisk4), ByteSize.ofBytes(sizeInUse4));

            // Make sure the new size on disk is about 20% of the original. Not 10%
            // as I think
            assertThat(sizeOnDisk4)
                    .isCloseTo((long) (sizeInUse1 * 0.2), Percentage.withPercentage(10));

            // Make sure we can query the re-opened db
            assertThat(db.get("key-0"))
                    .hasValue("val-0");
        }
    }

    private void doMultiThreadTest(final LmdbEnv lmdbEnv,
                                   final AbstractLmdbDb<String, String> database,
                                   final boolean isReaderBlockedByWriter,
                                   final int expectedNumReadersHighWaterMark,
                                   final boolean doWrites) {


        final HighWaterMarkTracker readersHighWaterMarkTracker = new HighWaterMarkTracker();
        final HighWaterMarkTracker writersHighWaterMarkTracker = new HighWaterMarkTracker();

        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);


        database.logDatabaseContents(LOGGER::info);

        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(1);

        final int threadCount = 50;
        LOGGER.info("threadCount: {}", threadCount);

        final CountDownLatch threadsStartedLatch = new CountDownLatch(threadCount);
        final CountDownLatch releaseThreadsLatch = new CountDownLatch(1);
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

        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        IntStream.rangeClosed(1, readerThreads)
                .forEach(i -> {
                    futures.add(createReaderThread(
                            lmdbEnv,
                            database,
                            readersHighWaterMarkTracker,
                            threadsStartedLatch,
                            releaseThreadsLatch,
                            threads,
                            exceptions,
                            i));

                    if (doWrites) {
                        futures.add(createWriterThread(
                                lmdbEnv,
                                database,
                                writersHighWaterMarkTracker,
                                threadsStartedLatch,
                                releaseThreadsLatch,
                                threads,
                                exceptions,
                                i));
                    }
                });

        // wait for all threads to be ready to work
        try {
            threadsStartedLatch.await();

            // Release all threads at once to hit LMDB
            releaseThreadsLatch.countDown();

//            do {
//                LOGGER.info("numReaders: {}, threadsFinishedLatch: {}, threads: {}",
//                        lmdbEnv.info().numReaders,
//                        threadsFinishedLatch.getCount(),
//                        String.join(", ", threads));
//
//            } while (!threadsFinishedLatch.await(50, TimeUnit.MILLISECONDS));
//
//            // Wait for all threads to finish
//            threadsFinishedLatch.await();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            LOGGER.info("Exception count: {}", exceptions.size());

            // We don't want any max readers exceptions
            assertThat(exceptions)
                    .isEmpty();

            // Can't really test for a min value on the readers to ensure they
            // are working concurrently as this will be run by github actions
            // which only has one core. Useful for manually checking locally though

            LOGGER.info("numReaders: {}", lmdbEnv.info().numReaders);
            // numreaders is a high water mark of max concurrent readers
            assertThat(lmdbEnv.info().numReaders)
                    .isLessThanOrEqualTo(expectedNumReadersHighWaterMark);

            LOGGER.info("readersHighWaterMarkTracker: {}", readersHighWaterMarkTracker);
            assertThat(readersHighWaterMarkTracker.getHighWaterMark())
                    .isLessThanOrEqualTo(expectedNumReadersHighWaterMark);

            if (doWrites) {
                LOGGER.info("writersHighWaterMarkTracker: {}", writersHighWaterMarkTracker);
                assertThat(writersHighWaterMarkTracker.getHighWaterMark())
                        .isEqualTo(1);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Void> createReaderThread(
            final LmdbEnv lmdbEnv,
            final AbstractLmdbDb<String, String> database,
            final HighWaterMarkTracker highWaterMarkTracker,
            final CountDownLatch threadsStartedLatch,
            final CountDownLatch releaseThreadsLatch,
            final Queue<String> threads,
            final List<Exception> exceptions,
            final int i) {

        LOGGER.trace("Creating reader thread {}", i);

        return CompletableFuture.runAsync(() -> {
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

                        assertThat(optVal)
                                .isNotEmpty();
                        LOGGER.trace("highWaterMarkTracker: {}", highWaterMarkTracker);
                    });
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    LOGGER.trace("Finished reader thread {}", i);

                    threads.remove(Thread.currentThread().getName());
                }
            });
        }, executor);
    }

    private CompletableFuture<Void> createWriterThread(
            final LmdbEnv lmdbEnv,
            final AbstractLmdbDb<String, String> database,
            final HighWaterMarkTracker highWaterMarkTracker,
            final CountDownLatch threadsStartedLatch,
            final CountDownLatch releaseThreadsLatch,
            final Queue<String> threads,
            final List<Exception> exceptions,
            final int i) {

        LOGGER.trace("Creating writer thread {}", i);
        final String key = buildKey(i);

        return CompletableFuture.runAsync(() -> {
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

                        assertThat(putOutcome.isSuccess())
                                .isTrue();
                        LOGGER.trace("highWaterMarkTracker: {}", highWaterMarkTracker);
                    });

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    LOGGER.trace("Finished writer thread {}", i);

                    threads.remove(Thread.currentThread().getName());
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
