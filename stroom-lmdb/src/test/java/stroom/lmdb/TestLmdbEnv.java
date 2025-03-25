package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.lmdb.serde.StringSerde;
import stroom.util.concurrent.HighWaterMarkTracker;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ModelStringUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env.MapFullException;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static java.lang.Long.reverseBytes;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.Objects.requireNonNull;

/**
 * Demonstrate the behaviour of too many readers trying to do a get() on LMDB at once
 * where concurrent readers > maxReaders setting. Tests the concurrency protection in
 * {@link LmdbEnv}
 */
public class TestLmdbEnv {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbEnv.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);
    private static Method MISMATCH_METHOD = getMismatchMethod();

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

                    Assertions.assertThat(database.getEntryCount())
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

                    Assertions.assertThat(database.getEntryCount())
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

                    Assertions.assertThat(database.getEntryCount())
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

                    Assertions.assertThat(database.getEntryCount())
                            .isEqualTo(0);
                });
    }

    @Test
    void testByteOrder() throws IOException {
        final Path dbDir = Files.createTempDirectory("stroom");

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        try (final LmdbEnv lmdbEnv = new LmdbEnvFactory(
                pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(ByteSize.ofGibibytes(1))
                .withMaxDbCount(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build()) {
            final Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("test", DbiFlags.MDB_CREATE);
            try (final WriteTxn writeTxn = lmdbEnv.openWriteTxn()) {
                final int rows = 100000;
                int insertCount = 0;
                ByteBuffer[] byteBuffersIn = new ByteBuffer[rows];
                for (int row = 0; row < rows; row++) {
                    final byte[] bytes = new byte[(int) (Math.random() * 510) + 1];
                    try {
                        for (int i = 0; i < bytes.length; i++) {
                            final byte b = (byte) (Math.random() * 255);
                            bytes[i] = b;
                        }
                        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
                        byteBuffer.put(bytes);
                        byteBuffer.flip();

                        if (dbi.put(writeTxn.getTxn(), byteBuffer, byteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                            byteBuffersIn[insertCount] = ByteBuffer.wrap(bytes);
                            insertCount++;
                        }

                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }

                byteBuffersIn = Arrays.copyOf(byteBuffersIn, insertCount);

                final ByteBuffer[] byteBuffersOut = new ByteBuffer[byteBuffersIn.length];
                try (final CursorIterable<ByteBuffer> cursorIterable =
                        dbi.iterate(writeTxn.getTxn(), KeyRange.all())) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                    int row = 0;
                    while (iterator.hasNext()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        final ByteBuffer key = keyVal.key();
                        final ByteBuffer copy = ByteBuffer.wrap(ByteBufferUtils.toBytes(key));
                        byteBuffersOut[row] = copy;
                        row++;
                    }
                }

                Arrays.sort(byteBuffersIn, (o1, o2) -> compareBuff(o1, o2));
//                Arrays.sort(byteBuffersIn, (o1, o2) -> compareByteBuffersUnsigned(o1, o2));

                Assertions.assertThat(byteBuffersIn.length).isEqualTo(byteBuffersOut.length);
                for (int i = 0; i < byteBuffersIn.length; i++) {
                    final ByteBuffer in = byteBuffersIn[i];
                    final ByteBuffer out = byteBuffersOut[i];

                    if (!in.equals(out)) {
//                        ByteBuffer[] inBefore = new ByteBuffer[10];
//                        ByteBuffer[] inAfter = new ByteBuffer[10];
//                        ByteBuffer[] outBefore = new ByteBuffer[10];
//                        ByteBuffer[] outAfter = new ByteBuffer[10];
//
//                        for (int j = 0; j < 10; j++) {
//                            int index = i - 10 + j;
//                            if (index > 0 && index < byteBuffersIn.length) {
//                                inBefore[j] = byteBuffersIn[index];
//                                outBefore[j] = byteBuffersOut[index];
//                            }
//                        }
//
//                        for (int j = 0; j < 10; j++) {
//                            int index = i + j + 1;
//                            if (index > 0 && index < byteBuffersIn.length) {
//                                inAfter[j] = byteBuffersIn[index];
//                                outAfter[j] = byteBuffersOut[index];
//                            }
//                        }


                        ByteBuffer[] inSample = new ByteBuffer[10];
                        ByteBuffer[] outSample = new ByteBuffer[10];

                        for (int j = 0; j < 10; j++) {
                            int index = i - 5 + j;
                            if (index > 0 && index < byteBuffersIn.length) {
                                inSample[j] = byteBuffersIn[index];
                                outSample[j] = byteBuffersOut[index];
                            }
                        }

                        // Error
                        Assertions.fail();
                    }
                }
            }
        }
    }

    public static int compareBuff(final ByteBuffer o1, final ByteBuffer o2) {
        requireNonNull(o1);
        requireNonNull(o2);
        if (o1.equals(o2)) {
            return 0;
        }
        final int minLength = Math.min(o1.limit(), o2.limit());
        final int minWords = minLength / Long.BYTES;

        final boolean reverse1 = o1.order() == LITTLE_ENDIAN;
        final boolean reverse2 = o2.order() == LITTLE_ENDIAN;
        for (int i = 0; i < minWords * Long.BYTES; i += Long.BYTES) {
            final long lw = reverse1
                    ? reverseBytes(o1.getLong(i))
                    : o1.getLong(i);
            final long rw = reverse2
                    ? reverseBytes(o2.getLong(i))
                    : o2.getLong(i);
            final int diff = Long.compareUnsigned(lw, rw);
            if (diff != 0) {
                return diff;
            }
        }

        for (int i = minWords * Long.BYTES; i < minLength; i++) {
            final int lw = Byte.toUnsignedInt(o1.get(i));
            final int rw = Byte.toUnsignedInt(o2.get(i));
            final int result = Integer.compareUnsigned(lw, rw);
            if (result != 0) {
                return result;
            }
        }

        return o1.remaining() - o2.remaining();
    }

    private int compareByteBuffersUnsigned(ByteBuffer bb1, ByteBuffer bb2) {
        int thisPos = bb1.position();
        int thisRem = bb1.limit() - thisPos;
        int thatPos = bb2.position();
        int thatRem = bb2.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0) {
            return -1;
        }
        int i = 0;
        try {
            i = (int) MISMATCH_METHOD.invoke(null, bb1, thisPos, bb2, thatPos, length);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        if (i >= 0) {
            return compare(bb1.get(thisPos + i), bb2.get(thatPos + i));
        }
        return thisRem - thatRem;
    }

    private static int compare(byte x, byte y) {


        return Byte.compareUnsigned(x, y);

    }


    private static Method getMismatchMethod() {
        Method method = null;
        try {
            final Class<?> clazz = Class.forName("java.nio.BufferMismatch");
            method = clazz.getDeclaredMethod("mismatch",
                    ByteBuffer.class, int.class, ByteBuffer.class, int.class, int.class);
            method.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return method;
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
                    Assertions.assertThat(optVal)
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

    private void doMultiThreadTest(final LmdbEnv lmdbEnv,
                                   final AbstractLmdbDb<String, String> database,
                                   final boolean isReaderBlockedByWriter,
                                   final int expectedNumReadersHighWaterMark,
                                   final boolean doWrites) {


        final HighWaterMarkTracker readersHighWaterMarkTracker = new HighWaterMarkTracker();
        final HighWaterMarkTracker writersHighWaterMarkTracker = new HighWaterMarkTracker();

        Assertions.assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);


        database.logDatabaseContents(LOGGER::info);

        Assertions.assertThat(lmdbEnv.info().numReaders)
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

                        Assertions.assertThat(optVal)
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

                        Assertions.assertThat(putOutcome.isSuccess())
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
