package stroom.lmdb;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class TestLmdbKeyValueStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbKeyValueStore.class);

    private static final String DB_NAME = "myLmdb";

    public static final long KILO_BYTES = 1024;
    public static final long MEGA_BYTES = 1024 * KILO_BYTES;
    public static final long GIGA_BYTES = 1024 * MEGA_BYTES;

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void test() {
        LOGGER.error("My Error", new RuntimeException());
    }

    @Test
    public void testStringString_db() throws IOException {

        Path path = Paths.get("/tmp/testStringString_db");
        deleteDirRecursive(path);
        Files.createDirectories(path);

        final Env<ByteBuffer> env = Env.<ByteBuffer>create()
                .setMapSize(50 * GIGA_BYTES)
                .setMaxDbs(1)
                .open(path.toFile());

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, DbiFlags.MDB_CREATE);

        final ByteBuffer key = ByteBuffer.allocateDirect(env.getMaxKeySize());
        final ByteBuffer value = ByteBuffer.allocateDirect(700);
        key.put("greeting".getBytes(StandardCharsets.UTF_8)).flip();
        value.put("Hello world".getBytes(StandardCharsets.UTF_8)).flip();

        final int valSize = value.remaining();

        db.put(key, value);

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer found = db.get(txn, key);
            Assert.assertNotNull(found);

            // The fetchedVal is read-only and points to LMDB memory
            final ByteBuffer fetchedVal = txn.val();
            Assert.assertThat(fetchedVal.remaining(), CoreMatchers.is(valSize));

            // Let's double-check the fetched value is correct
            Assert.assertThat(StandardCharsets.UTF_8.decode(fetchedVal).toString(),
                    CoreMatchers.is("Hello world"));
        }

        // We can also delete. The simplest way is to let Dbi allocate a new Txn...
        db.delete(key);

        // Now if we try to fetch the deleted row, it won't be present
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Assert.assertNull(db.get(txn, key));
        }
    }

    @Test
    public void perfTest() throws IOException {

        LOGGER.info("Generating test data");
        List<Map.Entry<String, String>> entries = IntStream.rangeClosed(1, 10_000)
                .mapToObj(i -> Maps.immutableEntry(
                        "key" + i + UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()))
                .collect(Collectors.toList());

        LOGGER.info("Entry count: {}", entries.size());

        List<Map.Entry<String, String>> randomEntries = new ArrayList<>(entries);

        LOGGER.info("Shuffling test data");
        Collections.shuffle(randomEntries);

        LOGGER.info("Initialising stores");
        Path dir = tmpDir.newFolder().toPath();
        KeyValueStore lmdbKeyValueStore = new LmdbKeyValueStore("MyDB", dir);

        KeyValueStore inMemoryKeyValueStore = new InMemoryKeyValueStore();

        List<KeyValueStore> stores = Arrays.asList(lmdbKeyValueStore, inMemoryKeyValueStore);

        //individual puts
        stores.forEach(store -> {
            LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            LOGGER.info("Store: {}", store.getClass().getName());
            Instant start;
//            Instant start = Instant.now();
//
//            entries.forEach(entry ->
//                    store.put(entry.getKey(), entry.getValue()));
//
//            LOGGER.info("Put duration {}", Duration.between(start, Instant.now()).toString());
//
//            //gets
//            start = Instant.now();
//
//            randomEntries.forEach(entry -> {
//                Optional<String> val = store.get(entry.getKey());
//                Preconditions.checkNotNull(val);
//            });
//
//            LOGGER.info("Get duration {}", Duration.between(start, Instant.now()).toString());
//
//            store.clear();

            //putBatch
            start = Instant.now();

            store.putBatch(entries);

            LOGGER.info("PutBatch duration {} ms", Duration.between(start, Instant.now()).toMillis());

            //gets
            for (int i = 0; i < 10; i++) {
                start = Instant.now();

                randomEntries.forEach(entry -> {
                    Optional<String> val = store.getWithTxn(entry.getKey());
                    Preconditions.checkNotNull(val);
                });

                LOGGER.info("Get duration {} ms", Duration.between(start, Instant.now()).toMillis());
            }

            try {
                store.close();
            } catch (Exception e) {
                throw new RuntimeException("Error closing store " + store, e);
            }
        });
    }

    @Test
    public void testLargeDB() throws IOException {
        Path path = Paths.get("/disk2/testLargeDB");
        deleteDirRecursive(path);
        Files.createDirectories(path);

        final Env<ByteBuffer> env = Env.<ByteBuffer>create()
                .setMapSize(50 * GIGA_BYTES)
                .setMaxDbs(1)
                .open(path.toFile());

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, DbiFlags.MDB_CREATE);

        int recCount = 3700;
        int batchSize = 1000;

        //Load all the data into the db in batches. Each batch is loaded in a txn.  Using a batch
        //that needs more memory than is available seems to kill it, so the amount of data
        //in the txn needs to be less than available RAM
        //Once committed, the txn cannot be reused.
        LOGGER.info("Loading data");

        doTimedWork("Loaded data", () -> {
            int i = 1;
            while (i < recCount) {
                int thisBatchSize = Math.min(batchSize, recCount - i + 1);
                LOGGER.info("Batch size: {}", thisBatchSize);
                putValues(db, env, i, thisBatchSize);
                i += thisBatchSize;
            }
        });

        //Get each kv pair in turn. If the DB is bigger than available RAM this will
        //mean shifting data in/out of page cache and reading from disk
        LOGGER.info("Reading all data");

        doTimedWork("Read data", () -> {
            try (Txn<ByteBuffer> txn = env.txnRead()) {

                LongStream.rangeClosed(1, recCount).forEach(j -> {
                    getValue(db, txn, j);

                    if (j % 500 == 0) {
                        LOGGER.info("Read {}", j);
                    }
                });
            }
        });

        //repeatedly get the values for the same 10 keys many times
        //akin to frequent access to hot data
        //these kv pairs should therefore sit in the page cache and be fast to access
        LOGGER.info("Reading hot keys many times");

        int hotKeyCount = 10;
        int iterations = recCount / hotKeyCount;
        doTimedWork(String.format("Read %s hot keys for %s iterations", hotKeyCount, iterations), () -> {
            try (Txn<ByteBuffer> txn = env.txnRead()) {

                LongStream.rangeClosed(1, recCount / 10).forEach(i -> {
                    LongStream.rangeClosed(1, 10).forEach(key -> {
                        getValue(db, txn, key);
                    });
                });
            }
        });

        /*
        The following is the output on a VM with 4GB RAM (~2.5Gb available) and backed by
        an NVMe SSD. 3700 kv pairs (3.7Gb of data) is loaded into LMDB. Xmx is set to 256M.

        11:27:22.011 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Loading data
        11:27:22.015 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Batch size: 1000
        11:27:22.016 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Putting start: 1, count 1000
        11:27:22.582 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 500
        11:27:22.970 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 1000
        11:27:22.970 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committing
        11:27:24.404 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committed
        11:27:24.405 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Batch size: 1000
        11:27:24.405 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Putting start: 1001, count 1000
        11:27:24.784 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 1500
        11:27:25.138 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 2000
        11:27:25.138 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committing
        11:27:26.557 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committed
        11:27:26.558 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Batch size: 1000
        11:27:26.558 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Putting start: 2001, count 1000
        11:27:26.939 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 2500
        11:27:27.275 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 3000
        11:27:27.275 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committing
        11:27:28.471 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committed
        11:27:28.471 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Batch size: 700
        11:27:28.471 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Putting start: 3001, count 700
        11:27:28.798 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Put 3500
        11:27:28.931 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committing
        11:27:29.803 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Committed
        11:27:29.815 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Loaded data in PT7.789S
        11:27:29.815 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Reading all data
        11:27:30.291 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 500
        11:27:30.744 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 1000
        11:27:31.208 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 1500
        11:27:31.638 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 2000
        11:27:32.055 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 2500
        11:27:32.343 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 3000
        11:27:32.345 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 3500
        11:27:32.345 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read data in PT2.524S
        11:27:32.345 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Reading hot keys many times
        11:27:32.349 [main] INFO  stroom.lmdb.TestLmdbKeyValueStore - Read 10 hot keys for 370 iterations in PT0.003S
         */
    }



    /**
     * Get a kv pair from the db using the passes key, and return the extracted long value
     * from the first 8 bytes of the kv pair value
     */
    private static long getValue(Dbi<ByteBuffer> db, Txn<ByteBuffer> txn, long key) {
        final ByteBuffer keyBuf = ByteBuffer.allocateDirect(Long.BYTES);
//                final ByteBuffer key = ByteBuffer.allocateDirect(env.getMaxKeySize());
        keyBuf.clear();
        keyBuf.putLong(key).flip();

        final ByteBuffer val = db.get(txn, keyBuf);

        Assert.assertNotNull(val);

        long extractedVal = val.getLong();

        Assert.assertEquals(key, extractedVal);
        Assert.assertEquals(MEGA_BYTES - Long.BYTES, val.remaining());

        return extractedVal;
    }

    /**
     * Put a batch of vk pairs into the DB under a single txn
     * @param startKey The key at the start of the batch
     * @param count The number of kv pairs to put
     */
    private static void putValues(final Dbi<ByteBuffer> db, Env<ByteBuffer> env, final long startKey, final long count) {
        LOGGER.info("Putting start: {}, count {}", startKey, count);
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            LongStream.range(startKey, startKey + count)
                    .forEach(i -> {
                        putValue(db, txn, i);
                        if (i % 500 == 0) {
                            LOGGER.info("Put {}", i);
                        }
                    });
            LOGGER.info("Committing");
            txn.commit();
            LOGGER.info("Committed");
        }
    }

    /**
     * Put a single kv pair into the database using the passed txn
     * @param keyVal The value of the key, also used in part of the kv pair value
     */
    private static void putValue(final Dbi<ByteBuffer> db, final Txn<ByteBuffer> txn, final long keyVal) {

        //build the key buffer
        final ByteBuffer key = ByteBuffer.allocateDirect(Long.BYTES);
        key.clear();
        key.putLong(keyVal).flip();

        //build the value buffer, 1MB in size, with the key also written to
        //the first 8 bytes
        final byte[] valueArr = new byte[(int) MEGA_BYTES - Long.BYTES];
        final ByteBuffer value = ByteBuffer.allocateDirect((int) MEGA_BYTES);
        value.clear();
        value.putLong(keyVal);
        value.put(valueArr).flip();
        db.put(txn, key, value);
    }


    private void deleteDirRecursive(final Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);
        }
    }

    private static void doTimedWork(final String logMsg, final Runnable work) {
        Instant startTime = Instant.now();
        work.run();
        LOGGER.info(logMsg + " in {}", Duration.between(startTime, Instant.now()).toString());
    }
}
