package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.serde.StringSerde;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

import org.junit.jupiter.api.RepeatedTest;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Demonstrate the behaviour of too many readers trying to do a get() on LMDB at once
 * where concurrent readers > maxReaders setting. Tests the concurrency protection in
 * {@link LmdbEnv}
 */
public class TestLmdbEnv2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbEnv2.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);

    // If this value is set too high then the test will fail on gh actions as
    // that has limited cores available.
    private static final int CORES = 400;
    private static final int MAX_READERS = CORES;


    @RepeatedTest(1000)
    void test() {
        List<CompletableFuture> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    doTest();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    void doTest() throws IOException {

        LOGGER.info("MAX_READERS: {}", MAX_READERS);

        final Path dbDir = Files.createTempDirectory("stroom");

        final EnvFlags[] envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                DB_MAX_SIZE,
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator, tempDirProvider, LmdbLibraryConfig::new)
                .builder(dbDir)
                .withMapSize(DB_MAX_SIZE)
                .withMaxDbCount(1)
                .withMaxReaderCount(MAX_READERS)
                .setIsReaderBlockedByWriter(false)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        final BasicLmdbDb<String, String> database = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");

        List<CompletableFuture> futures = new ArrayList<>();

        // load the data
        // pad the keys to a fixed length so they sort in number order
        futures.add(CompletableFuture.runAsync(() -> {
                    lmdbEnv.doWithWriteTxn(writeTxn -> {
                        IntStream.rangeClosed(1, 10000000).forEach(i -> {
                            database.put(writeTxn, buildKey(i), buildValue(i), false);
                        });
                    });
                }));

        for (int i = 0; i < 10000; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                lmdbEnv.doWithReadTxn(readTxn -> {
                    database.get(readTxn, buildKey(0));
                });
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        lmdbEnv.close();
    }

    private String buildKey(int i) {
        return String.format("%02d", i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }

}
